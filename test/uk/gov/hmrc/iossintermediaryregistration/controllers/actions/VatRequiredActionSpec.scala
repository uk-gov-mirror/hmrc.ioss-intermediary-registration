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

import play.api.mvc.Result
import play.api.mvc.Results.Unauthorized
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.iossintermediaryregistration.base.BaseSpec
import uk.gov.hmrc.iossintermediaryregistration.config.AppConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VatRequiredActionSpec extends BaseSpec {

  class Harness() extends VatRequiredAction(mock[AppConfig]) {

    def callRefine[A](request: AuthorisedRequest[A]): Future[Either[Result, AuthorisedMandatoryVrnRequest[A]]] = refine(request)
  }

  "Vat Required Action" - {

    "when the user has logged in as an Organisation Admin with strong credentials but no vat enrolment" - {

      "must return Unauthorized" in {

        val action = new Harness()
        val request = FakeRequest(GET, "/test/url?k=session-id")
        val result = action.callRefine(AuthorisedRequest(request,
          testCredentials,
          userId,
          None,
          Enrolments(Set.empty)
        )).futureValue

        result `mustBe` Left(Unauthorized)
      }

      "must return Right" in {

        val action = new Harness()
        val request = FakeRequest(GET, "/test/url?k=session-id")
        val result = action.callRefine(AuthorisedRequest(request,
          testCredentials,
          userId,
          Some(vrn),
          Enrolments(Set.empty)
        )).futureValue

        val expectResult = AuthorisedMandatoryVrnRequest(request, testCredentials, userId, vrn, None, None)

        result `mustBe` Right(expectResult)
      }
    }

    "when the user has logged in as an Individual without a VAT enrolment" - {

      "must be redirected to the insufficient Enrolments page" in {

        val action = new Harness()
        val request = FakeRequest(GET, "/test/url?k=session-id")
        val result = action.callRefine(AuthorisedRequest(request,
          testCredentials,
          userId,
          None,
          Enrolments(Set.empty)
        )).futureValue

        result `mustBe` Left(Unauthorized)
      }
    }
  }
}
