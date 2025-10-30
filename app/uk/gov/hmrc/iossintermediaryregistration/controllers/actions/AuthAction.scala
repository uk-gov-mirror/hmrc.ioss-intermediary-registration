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

package uk.gov.hmrc.iossintermediaryregistration.controllers.actions

import play.api.mvc.*
import play.api.mvc.Results.Unauthorized
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.AffinityGroup.{Agent, Individual, Organisation}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.iossintermediaryregistration.logging.Logging
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

trait AuthAction extends ActionBuilder[AuthorisedRequest, AnyContent] with ActionFunction[Request, AuthorisedRequest]

class AuthActionImpl @Inject()(
                                override val authConnector: AuthConnector,
                                val parser: BodyParsers.Default
                              )(implicit val executionContext: ExecutionContext)
  extends AuthAction with AuthorisedFunctions with Logging {

  override def invokeBlock[A](request: Request[A], block: AuthorisedRequest[A] => Future[Result]): Future[Result] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    authorised(
      AuthProviders(AuthProvider.GovernmentGateway) and
        (AffinityGroup.Individual or AffinityGroup.Organisation or AffinityGroup.Agent) and
        CredentialStrength(CredentialStrength.strong)
    ).retrieve(
      Retrievals.credentials and
        Retrievals.internalId and
        Retrievals.allEnrolments and
        Retrievals.affinityGroup and
        Retrievals.confidenceLevel
    ) {

      case Some(credentials) ~ Some(internalId) ~ enrolments ~ Some(Organisation) ~ _ =>
        val maybeVrn = findVrnFromEnrolments(enrolments)
        block(AuthorisedRequest(request, credentials, internalId, maybeVrn, enrolments))

      case Some(credentials) ~ Some(internalId) ~ enrolments ~ Some(Agent) ~ _ =>
        val maybeVrn = findVrnFromEnrolments(enrolments)
        block(AuthorisedRequest(request, credentials, internalId, maybeVrn, enrolments))

      case Some(credentials) ~ Some(internalId) ~ enrolments ~ Some(Individual) ~ confidence =>
        val maybeVrn = findVrnFromEnrolments(enrolments)
        if (confidence >= ConfidenceLevel.L250) {
          block(AuthorisedRequest(request, credentials, internalId, maybeVrn, enrolments))
        } else {
          throw InsufficientConfidenceLevel("Insufficient confidence level")
        }
      case _ =>
        throw new UnauthorizedException("Unable to retrieve authorisation data")
    } recover {
      case e: AuthorisationException =>
        logger.info(e.getMessage, e)
        Unauthorized
    }
  }

  private def findVrnFromEnrolments(enrolments: Enrolments): Option[Vrn] = {
    enrolments.enrolments.find(_.key == "HMRC-MTD-VAT")
      .flatMap {
        enrolment =>
          enrolment.identifiers.find(_.key == "VRN").map(e => Vrn(e.value))
      } orElse enrolments.enrolments.find(_.key == "HMCE-VATDEC-ORG")
      .flatMap {
        enrolment =>
          enrolment.identifiers.find(_.key == "VATRegNo").map(e => Vrn(e.value))
      }
  }
}
