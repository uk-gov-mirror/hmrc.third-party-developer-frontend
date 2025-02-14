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

package views.include

import controllers.Credentials.serverTokenCutoffDate
import domain.models.applications._
import domain.models.developers.LoggedInState
import model.ApplicationViewModel
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.test.FakeRequest
import uk.gov.hmrc.time.DateTimeUtils
import utils.ViewHelpers.elementExistsByText
import utils._
import views.helper.CommonViewSpec
import views.html.include.LeftHandNav

class LeftHandNavSpec extends CommonViewSpec with WithCSRFAddToken with CollaboratorTracker with LocalUserIdTracker {

  trait Setup {
    val leftHandNavView = app.injector.instanceOf[LeftHandNav]

    val request = FakeRequest().withCSRFToken

    val applicationId = ApplicationId("1234")
    val clientId = ClientId("clientId123")
    val applicationName = "Test Application"

    val loggedInUser = utils.DeveloperSession("givenname.familyname@example.com", "Givenname", "Familyname", loggedInState = LoggedInState.LOGGED_IN)

    val application = Application(
      applicationId,
      clientId,
      applicationName,
      DateTimeUtils.now,
      DateTimeUtils.now,
      None,
      Environment.PRODUCTION,
      Some("Description 1"),
      Set(loggedInUser.email.asAdministratorCollaborator),
      state = ApplicationState.production(loggedInUser.email, ""),
      access = Standard(redirectUris = List("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com"))
    )

    val applicationViewModelWithApiSubscriptions = ApplicationViewModel(application, hasSubscriptionsFields = true, hasPpnsFields = false)
    val applicationViewModelWithNoApiSubscriptions = ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false)
  }

  "Left Hand Nav" when {
    "working with an application with no api subscriptions" should {
      "render correctly" in new Setup {
        val page = leftHandNavView.render(Some(applicationViewModelWithNoApiSubscriptions), Some("details"), request, loggedInUser, appConfig)

        page.contentType should include("text/html")

        val document = Jsoup.parse(page.body)
        elementExistsByText(document, "a", "API subscriptions") shouldBe true
        elementExistsByText(document, "a", "Subscription configuration") shouldBe false
        elementExistsByText(document, "a", "Credentials") shouldBe true
        elementExistsByText(document, "a", "Client ID") shouldBe true
        elementExistsByText(document, "a", "Client secrets") shouldBe true
        elementExistsByText(document, "a", "Server token") shouldBe false
        elementExistsByText(document, "a", "Redirect URIs") shouldBe true
        elementExistsByText(document, "a", "Team members") shouldBe true
        elementExistsByText(document, "a", "Delete application") shouldBe true
      }

      "NOT display server token link for old apps" in new Setup {
        val oldAppWithoutSubsFields =
          ApplicationViewModel(application.copy(createdOn = serverTokenCutoffDate.minusDays(1)), hasSubscriptionsFields = false, hasPpnsFields = false)
        val page = leftHandNavView.render(Some(oldAppWithoutSubsFields), Some("details"), request, loggedInUser, appConfig)

        page.contentType should include("text/html")

        val document = Jsoup.parse(page.body)
        elementExistsByText(document, "a", "Server token") shouldBe false
      }
    }

    "working with an application with api subscriptions" should {
      "render correctly" in new Setup {
        val page = leftHandNavView.render(Some(applicationViewModelWithApiSubscriptions), Some("details"), request, loggedInUser, appConfig)
        page.contentType should include("text/html")

        val document = Jsoup.parse(page.body)
        elementExistsByText(document, "a", "API subscriptions") shouldBe true
        elementExistsByText(document, "a", "Subscription configuration") shouldBe true
        elementExistsByText(document, "a", "Credentials") shouldBe true
        elementExistsByText(document, "a", "Client ID") shouldBe true
        elementExistsByText(document, "a", "Client secrets") shouldBe true
        elementExistsByText(document, "a", "Server token") shouldBe false
        elementExistsByText(document, "a", "Redirect URIs") shouldBe true
        elementExistsByText(document, "a", "Team members") shouldBe true
        elementExistsByText(document, "a", "Delete application") shouldBe true
      }

      "NOT display server token link for old apps" in new Setup {
        val oldAppWithSubsFields =
          ApplicationViewModel(application.copy(createdOn = serverTokenCutoffDate.minusDays(1)), hasSubscriptionsFields = true, hasPpnsFields = false)
        val page = leftHandNavView.render(Some(oldAppWithSubsFields), Some("details"), request, loggedInUser, appConfig)

        page.contentType should include("text/html")

        val document = Jsoup.parse(page.body)
        elementExistsByText(document, "a", "Server token") shouldBe false
      }
    }

    "on the View all applications page" should {
      "render correct wording for default environment config" in new Setup {
        val page = leftHandNavView.render(None, Some("manage-applications"), request, loggedInUser, appConfig)
        page.contentType should include("text/html")

        val document = Jsoup.parse(page.body)
        elementExistsByText(document, "a", "Add an application to the sandbox") shouldBe true
        elementExistsByText(document, "a", "Get production credentials") shouldBe true

        userProfileSectionCorrectlyDisplayed(document) shouldBe true
      }

      "render correct wording for QA and Development config" in new Setup {
        when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
        when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")

        val page = leftHandNavView.render(None, Some("manage-applications"), request, loggedInUser, appConfig)
        page.contentType should include("text/html")

        val document = Jsoup.parse(page.body)
        elementExistsByText(document, "a", "Add an application to Development") shouldBe true
        elementExistsByText(document, "a", "Add an application to QA") shouldBe true

        userProfileSectionCorrectlyDisplayed(document) shouldBe true
      }

      "render correct wording for Staging" in new Setup {
        when(appConfig.nameOfPrincipalEnvironment).thenReturn("Staging")
        when(appConfig.nameOfSubordinateEnvironment).thenReturn("Staging")

        val page = leftHandNavView.render(None, Some("manage-applications"), request, loggedInUser, appConfig)
        page.contentType should include("text/html")

        val document = Jsoup.parse(page.body)
        elementExistsByText(document, "a", "Add an application to Staging") shouldBe true
        elementExistsByText(document, "a", "Add an application to Staging") shouldBe true

        userProfileSectionCorrectlyDisplayed(document) shouldBe true
      }

      "render correct wording for Integration" in new Setup {
        when(appConfig.nameOfPrincipalEnvironment).thenReturn("Integration")
        when(appConfig.nameOfSubordinateEnvironment).thenReturn("Integration")

        val page = leftHandNavView.render(None, Some("manage-applications"), request, loggedInUser, appConfig)
        page.contentType should include("text/html")

        val document: Document = Jsoup.parse(page.body)
        elementExistsByText(document, "a", "Add an application to Integration") shouldBe true
        elementExistsByText(document, "a", "Add an application to Integration") shouldBe true

        userProfileSectionCorrectlyDisplayed(document) shouldBe true
      }
    }
  }

  def userProfileSectionCorrectlyDisplayed(document: Document): Boolean =
    elementExistsByText(document, "a", "Manage profile") &&
      elementExistsByText(document, "a", "Email preferences") &&
      elementExistsByText(document, "a", "Change password") &&
      elementExistsByText(document, "a", "Account protection")
}
