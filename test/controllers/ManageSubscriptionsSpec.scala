/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import java.util.UUID.randomUUID

import config.ErrorHandler
import domain._
import org.joda.time.DateTimeZone
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.BDDMockito.given
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.{ApplicationService, AuditService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.Future._

class ManageSubscriptionSpec extends BaseControllerSpec with WithCSRFAddToken {

  val appId = "1234"
  val clientId = "clientId123"

  val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

  val loggedInUser = DeveloperSession(session)

  val partLoggedInSessionId = "partLoggedInSessionId"
  val partLoggedInSession = Session(partLoggedInSessionId, developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA)

  val application = Application(appId, clientId, "App name 1", DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
      Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
      access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

  val tokens = ApplicationToken("clientId", Seq(aClientSecret("secret"), aClientSecret("secret2")), "token")

  private val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)

  trait ManageSubscriptionsSetup {

    val mockSessionService = mock[SessionService]
    val mockAuditService = mock[AuditService]
    val mockApplicationService = mock[ApplicationService](org.mockito.Mockito.withSettings().verboseLogging())
    val mockErrorHandler = mock[ErrorHandler]

    val manageSubscriptionController = new ManageSubscriptions(
      mockSessionService,
      mockAuditService,
      mockApplicationService,
      mockErrorHandler,
      messagesApi
    )

    given(mockSessionService.fetch(eqTo(sessionId))(any[HeaderCarrier]))
    .willReturn(Some(session))

    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    .withLoggedIn(manageSubscriptionController,implicitly)(sessionId)
    .withSession(sessionParams: _*)

    val partLoggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    .withLoggedIn(manageSubscriptionController,implicitly)(partLoggedInSessionId)
    .withSession(sessionParams: _*)
  }

  "manageSubscriptions" should {

    "return the list subscription metadata page with the user logged in" in new ManageSubscriptionsSetup {

      given(mockApplicationService.fetchByApplicationId(eqTo(appId))(any[HeaderCarrier]))
        .willReturn(successful(application))

      given(mockApplicationService.fetchByTeamMemberEmail(any())(any[HeaderCarrier]))
          .willReturn(successful(List(application)))

      private val result = await(manageSubscriptionController.listApiSubscriptions(appId)(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include(loggedInUser.displayedName)
      bodyOf(result) should include("Sign out")
      bodyOf(result) should include("You can submit metadata with each API request for these APIs.")
    }

    "return to the login page when the user is not logged in" in new ManageSubscriptionsSetup {

      val request = FakeRequest()

      private val result = await(manageSubscriptionController.listApiSubscriptions(appId)(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }
  }
  private def aClientSecret(secret: String) = ClientSecret(randomUUID.toString, secret, secret, DateTimeUtils.now.withZone(DateTimeZone.getDefault))
}
