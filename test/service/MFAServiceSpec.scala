/*
 * Copyright 2021 HM Revenue & Customs
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

package service

import connectors.ThirdPartyDeveloperConnector
import play.api.http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import utils.AsyncHmrcSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}
import domain.models.developers.UserId

class MFAServiceSpec extends AsyncHmrcSpec {

  trait Setup {
    val userId = UserId.random
    val email = "bob.smith@example.com"
    val totpCode = "12345678"
    val connector = mock[ThirdPartyDeveloperConnector]

    when(connector.enableMfa(eqTo(userId))(*)).thenReturn(successful(()))
    when(connector.removeMfa(eqTo(userId), eqTo(email))(*)).thenReturn(successful(()))

    val service = new MFAService(connector)
  }

  trait FailedTotpVerification extends Setup {
    when(connector.verifyMfa(eqTo(userId), eqTo(totpCode))(*)).thenReturn(successful(false))
  }

  trait SuccessfulTotpVerification extends Setup {
    when(connector.verifyMfa(eqTo(userId), eqTo(totpCode))(*)).thenReturn(successful(true))
  }

  "enableMfa" should {
    "return failed totp when totp verification fails" in new FailedTotpVerification {
      val result = await(service.enableMfa(userId, totpCode)(HeaderCarrier()))
      result.totpVerified shouldBe false
    }

    "not call enable mfa when totp verification fails" in new FailedTotpVerification {
      await(service.enableMfa(userId, totpCode)(HeaderCarrier()))
      verify(connector, never).enableMfa(eqTo(userId))(*)
    }

    "return successful totp when totp verification passes" in new SuccessfulTotpVerification {
      val result = await(service.enableMfa(userId, totpCode)(HeaderCarrier()))
      result.totpVerified shouldBe true
    }

    "enable MFA totp when totp verification passes" in new SuccessfulTotpVerification {
      await(service.enableMfa(userId, totpCode)(HeaderCarrier()))
      verify(connector, times(1)).enableMfa(eqTo(userId))(*)
    }

    "throw exception if update fails" in new SuccessfulTotpVerification {
      when(connector.enableMfa(eqTo(userId))(*))
        .thenReturn(failed(UpstreamErrorResponse("failed to enable MFA", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      intercept[UpstreamErrorResponse](await(service.enableMfa(userId, totpCode)(HeaderCarrier())))
    }
  }

  "removeMfa" should {
    "return failed totp when totp verification fails" in new FailedTotpVerification {
      val result: MFAResponse = await(service.removeMfa(userId, email, totpCode)(HeaderCarrier()))
      result.totpVerified shouldBe false
    }

    "not call remove mfa when totp verification fails" in new FailedTotpVerification {
      await(service.removeMfa(userId, email, totpCode)(HeaderCarrier()))
      verify(connector, never).removeMfa(eqTo(userId), eqTo(email))(*)
    }

    "return successful totp when totp verification passes" in new SuccessfulTotpVerification {
      val result: MFAResponse = await(service.removeMfa(userId, email, totpCode)(HeaderCarrier()))

      result.totpVerified shouldBe true
    }

    "remove MFA when totp verification passes" in new SuccessfulTotpVerification {
      await(service.removeMfa(userId, email, totpCode)(HeaderCarrier()))

      verify(connector, times(1)).removeMfa(eqTo(userId), eqTo(email))(*)
    }

    "throw exception if removal fails" in new SuccessfulTotpVerification {
      when(connector.removeMfa(eqTo(userId), eqTo(email))(*))
        .thenReturn(failed(UpstreamErrorResponse("failed to remove MFA", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      intercept[UpstreamErrorResponse](await(service.removeMfa(userId, email, totpCode)(HeaderCarrier())))
    }
  }
}
