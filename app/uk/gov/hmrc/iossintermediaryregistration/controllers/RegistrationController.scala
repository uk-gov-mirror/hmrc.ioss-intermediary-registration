/*
 * Copyright 2025 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.iossintermediaryregistration.controllers

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.iossintermediaryregistration.config.AppConfig
import uk.gov.hmrc.iossintermediaryregistration.connectors.EnrolmentsConnector
import uk.gov.hmrc.iossintermediaryregistration.controllers.actions.{AuthenticatedControllerComponents, AuthorisedMandatoryVrnRequest}
import uk.gov.hmrc.iossintermediaryregistration.logging.Logging
import uk.gov.hmrc.iossintermediaryregistration.models.RegistrationStatus
import uk.gov.hmrc.iossintermediaryregistration.models.audit.{EtmpRegistrationAuditType, EtmpRegistrationRequestAuditModel, SubmissionResult}
import uk.gov.hmrc.iossintermediaryregistration.models.etmp.amend.EtmpAmendRegistrationRequest
import uk.gov.hmrc.iossintermediaryregistration.models.etmp.responses.{EtmpEnrolmentErrorResponse, EtmpEnrolmentResponse}
import uk.gov.hmrc.iossintermediaryregistration.models.etmp.{EtmpRegistrationRequest, EtmpRegistrationStatus}
import uk.gov.hmrc.iossintermediaryregistration.models.responses.{EtmpEnrolmentError, EtmpException}
import uk.gov.hmrc.iossintermediaryregistration.repositories.RegistrationStatusRepository
import uk.gov.hmrc.iossintermediaryregistration.services.{AuditService, RegistrationService, RetryService}
import uk.gov.hmrc.iossintermediaryregistration.utils.FutureSyntax.FutureOps
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.Clock
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class RegistrationController @Inject()(
                                             cc: AuthenticatedControllerComponents,
                                             enrolmentsConnector: EnrolmentsConnector,
                                             registrationService: RegistrationService,
                                             auditService: AuditService,
                                             registrationStatusRepository: RegistrationStatusRepository,
                                             retryService: RetryService,
                                             appConfig: AppConfig,
                                             clock: Clock
                                           )(implicit ec: ExecutionContext) extends BackendController(cc) with Logging {

  def createRegistration(): Action[EtmpRegistrationRequest] = cc.authAndRequireVat()(parse.json[EtmpRegistrationRequest]).async {
    implicit request: AuthorisedMandatoryVrnRequest[EtmpRegistrationRequest] =>
      registrationService.createRegistration(request.body).flatMap {
        case Right(etmpEnrolmentResponse) =>
          enrollRegistration(etmpEnrolmentResponse.formBundleNumber).map { etmpRegistrationStatus =>
            auditRegistrationEvent(
              formBundleNumber = etmpEnrolmentResponse.formBundleNumber,
              etmpEnrolmentResponse = etmpEnrolmentResponse,
              etmpRegistrationStatus = etmpRegistrationStatus,
              successResponse = Created(Json.toJson(etmpEnrolmentResponse)))
          }
        case Left(EtmpEnrolmentError(EtmpEnrolmentErrorResponse.alreadyActiveSubscriptionErrorCode, body)) =>
          auditService.audit(EtmpRegistrationRequestAuditModel.build(
            EtmpRegistrationAuditType.CreateRegistration, request.body, None, Some(body), SubmissionResult.Duplicate)
          )
          logger.error(
            s"Business Partner already has an active IOSS Subscription for this regime with error code ${EtmpEnrolmentErrorResponse.alreadyActiveSubscriptionErrorCode}" +
              s"with message body $body"
          )
          Conflict(Json.toJson(
            s"Business Partner already has an active IOSS Subscription for this regime with error code ${EtmpEnrolmentErrorResponse.alreadyActiveSubscriptionErrorCode}" +
              s"with message body $body"
          )).toFuture
        case Left(error) =>
          auditService.audit(EtmpRegistrationRequestAuditModel.build(
            EtmpRegistrationAuditType.CreateRegistration, request.body, None, Some(error.body), SubmissionResult.Failure)
          )
          logger.error(s"Internal server error ${error.body}")
          InternalServerError(Json.toJson(s"Internal server error ${error.body}")).toFuture
      }
  }

  private def enrollRegistration(formBundleNumber: String)
                                (implicit hc: HeaderCarrier): Future[EtmpRegistrationStatus] = {
    (for {
      _ <- registrationStatusRepository.delete(formBundleNumber)
      _ <- registrationStatusRepository.insert(RegistrationStatus(subscriptionId = formBundleNumber,
        status = EtmpRegistrationStatus.Pending))
      enrolmentResponse <- enrolmentsConnector.confirmEnrolment(formBundleNumber)
    } yield {
      val enrolmentResponseStatus = enrolmentResponse.status
      enrolmentResponseStatus match {
        case NO_CONTENT =>
          retryService.getEtmpRegistrationStatus(appConfig.maxRetryCount, appConfig.delay, formBundleNumber)
        case status =>
          logger.error(s"Failed to add enrolment - $status with body ${enrolmentResponse.body}")
          throw EtmpException(s"Failed to add enrolment - ${enrolmentResponse.body}")
      }
    }).flatten
  }

  private def auditRegistrationEvent(formBundleNumber: String,
                                     etmpEnrolmentResponse: EtmpEnrolmentResponse,
                                     etmpRegistrationStatus: EtmpRegistrationStatus,
                                     successResponse: Result)
                                    (implicit hc: HeaderCarrier, request: AuthorisedMandatoryVrnRequest[EtmpRegistrationRequest]): Result = {
    etmpRegistrationStatus match {
      case EtmpRegistrationStatus.Success =>
        auditService.audit(EtmpRegistrationRequestAuditModel.build(
          EtmpRegistrationAuditType.CreateRegistration, request.body, Some(etmpEnrolmentResponse), None, SubmissionResult.Success)
        )
        logger.info("Successfully created registration and enrolment")
        successResponse
      case registrationStatus: EtmpRegistrationStatus =>
        logger.error(s"Failed to add enrolment, got registration status $registrationStatus")
        registrationStatusRepository.set(RegistrationStatus(subscriptionId = formBundleNumber, status = EtmpRegistrationStatus.Error))
        throw EtmpException(s"Failed to add enrolment, got registration status $registrationStatus")
    }
  }

  def displayRegistration(intermediaryNumber: String): Action[AnyContent] = cc.authAndRequireVat().async {
    implicit request =>
      registrationService.getRegistrationWrapper(intermediaryNumber, request.vrn).map { registrationWrapper =>
        Ok(Json.toJson(registrationWrapper))
      }.recover {
        case exception =>
          logger.error(exception.getMessage, exception)
          InternalServerError(exception.getMessage)
      }
  }

  def amend(): Action[EtmpAmendRegistrationRequest] = cc.authAndRequireVat()(parse.json[EtmpAmendRegistrationRequest]).async {
    implicit request =>
      val etmpAmendRegistrationRequest: EtmpAmendRegistrationRequest = request.body
      registrationService.amendRegistration(etmpAmendRegistrationRequest).map {
        case Right(amendRegistrationResponse) =>
          Ok(Json.toJson(amendRegistrationResponse))

        case Left(error) =>
          val errorMessage: String = s"Internal server error with error: $error and message: ${error.getMessage}."
          logger.error(errorMessage)
          InternalServerError(errorMessage)
      }
  }

  def getAccounts: Action[AnyContent] = cc.authAndRequireIntermediary().async {

    implicit request =>
      enrolmentsConnector.es2(request.credentials.providerId).map {
        case Right(enrolments) => Ok(Json.toJson(enrolments))
        case Left(e) => InternalServerError(e.body)
      }
  }
}
