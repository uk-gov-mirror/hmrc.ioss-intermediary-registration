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

package uk.gov.hmrc.iossintermediaryregistration.models.audit

import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.iossintermediaryregistration.controllers.actions.AuthorisedMandatoryVrnRequest
import uk.gov.hmrc.iossintermediaryregistration.models.etmp.amend.{AmendRegistrationResponse, EtmpAmendRegistrationRequest}
import uk.gov.hmrc.iossintermediaryregistration.models.etmp.responses.EtmpEnrolmentResponse

case class EtmpAmendRegistrationAuditModel(
                                            etmpRegistrationAuditType: EtmpRegistrationAuditType,
                                            userId: String,
                                            userAgent: String,
                                            vrn: String,
                                            etmpRegistrationRequest: EtmpAmendRegistrationRequest,
                                            etmpEnrolmentResponse: Option[EtmpEnrolmentResponse],
                                            etmpAmendResponse: Option[AmendRegistrationResponse],
                                            errorResponse: Option[String],
                                            submissionResult: SubmissionResult
                                          ) extends JsonAuditModel {

  override val auditType: String = etmpRegistrationAuditType.auditType

  override val transactionName: String = etmpRegistrationAuditType.transactionName

  private val etmpEnrolmentResponseObj: JsObject =
    if (etmpEnrolmentResponse.isDefined) {
      Json.obj("etmpEnrolmentResponse" -> etmpEnrolmentResponse)
    } else {
      Json.obj()
    }
    
  private val etmpAmendResponseObj: JsObject =
    if (etmpAmendResponse.isDefined) {
      Json.obj("etmpAmendResponse" -> etmpAmendResponse)
    } else {
      Json.obj()
    }

  private val errorResponseObj: JsObject =
    if (errorResponse.isDefined) {
      Json.obj("errorResponse" -> errorResponse)
    } else {
      Json.obj()
    }

  override val detail: JsValue = Json.obj(
    "userId" -> userId,
    "browserUserAgent" -> userAgent,
    "requestersVrn" -> vrn,
    "etmpRegistrationRequest" -> Json.toJson(etmpRegistrationRequest),
    "submissionResult" -> Json.toJson(submissionResult)
  ) ++ etmpEnrolmentResponseObj ++
    etmpAmendResponseObj ++
    errorResponseObj
}

object EtmpAmendRegistrationAuditModel {

  def build(
             etmpRegistrationAuditType: EtmpRegistrationAuditType,
             etmpRegistrationRequest: EtmpAmendRegistrationRequest,
             etmpEnrolmentResponse: Option[EtmpEnrolmentResponse],
             etmpAmendResponse: Option[AmendRegistrationResponse],
             errorResponse: Option[String],
             submissionResult: SubmissionResult
           )(implicit request: AuthorisedMandatoryVrnRequest[_]): EtmpAmendRegistrationAuditModel =
    EtmpAmendRegistrationAuditModel(
      etmpRegistrationAuditType = etmpRegistrationAuditType,
      userId = request.userId,
      userAgent = request.headers.get("user-agent").getOrElse(""),
      vrn = request.vrn.vrn,
      etmpRegistrationRequest = etmpRegistrationRequest,
      etmpEnrolmentResponse = etmpEnrolmentResponse,
      etmpAmendResponse = etmpAmendResponse,
      errorResponse = errorResponse,
      submissionResult = submissionResult
    )
}
