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

package views

import domain.models.applications._
import domain.models.developers.LoggedInState
import org.jsoup.Jsoup
import play.api.test.FakeRequest
import uk.gov.hmrc.time.DateTimeUtils
import utils.ViewHelpers.elementExistsByText
import utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.DeletePrincipalApplicationCompleteView
import builder.DeveloperBuilder
import utils.LocalUserIdTracker

class DeletePrincipalApplicationCompleteSpec extends CommonViewSpec with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker {

  val deletePrincipalApplicationCompleteView = app.injector.instanceOf[DeletePrincipalApplicationCompleteView]

  "delete application complete page" should {
    "render with no errors" in {

      val request = FakeRequest().withCSRFToken

      val appId = ApplicationId("1234")
      val clientId = ClientId("clientId123")
      val loggedInUser = utils.DeveloperSession("developer@example.com", "John", "Doe", loggedInState = LoggedInState.LOGGED_IN)
      val application = Application(
        appId,
        clientId,
        "App name 1",
        DateTimeUtils.now,
        DateTimeUtils.now,
        None,
        Environment.PRODUCTION,
        Some("Description 1"),
        Set(loggedInUser.email.asAdministratorCollaborator),
        state = ApplicationState.production(loggedInUser.email, ""),
        access = Standard(redirectUris = List("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com"))
      )

      val page = deletePrincipalApplicationCompleteView.render(application, request, loggedInUser, messagesProvider, appConfig)
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Request submitted") shouldBe true
    }
  }

}
