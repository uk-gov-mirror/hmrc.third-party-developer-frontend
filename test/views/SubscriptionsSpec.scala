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

import controllers.{EditApplicationForm, GroupedSubscriptions, PageData}
import domain.models.applications._
import domain.models.developers.LoggedInState
import model.ApplicationViewModel
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html
import utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.ManageSubscriptionsView

import scala.collection.JavaConverters._

class SubscriptionsSpec extends CommonViewSpec with WithCSRFAddToken {

  val manageSubscriptions = app.injector.instanceOf[ManageSubscriptionsView]

  trait Setup {
    def elementExistsByText(doc: Document, elementType: String, elementText: String): Boolean = {
      doc.select(elementType).asScala.exists(node => node.text.trim == elementText)
    }

    def elementExistsById(doc: Document, id: String): Boolean = doc.select(s"#$id").asScala.nonEmpty
  }

  def buildApplication(applicationState: ApplicationState, environment: Environment): Application = Application(
    ApplicationId("Test Application ID"),
    ClientId("Test Application Client ID"),
    "Test Application",
    DateTime.now(),
    DateTime.now(),
    None,
    environment,
    Some("Test Application"),
    Set.empty,
    Standard(),
    applicationState,
    None
  )

  "Subscriptions page" should {
    val developer = utils.DeveloperSession("Test", "Test", "Test", None, loggedInState = LoggedInState.LOGGED_IN)

    val productionApplicationPendingGatekeeperApproval = buildApplication(ApplicationState.pendingGatekeeperApproval("somebody@example.com"), Environment.PRODUCTION)
    val productionApplicationPendingRequesterVerification = buildApplication(ApplicationState.pendingRequesterVerification("somebody@example.com", ""), Environment.PRODUCTION)
    val productionApplication = buildApplication(ApplicationState.production("somebody@example.com", ""), Environment.PRODUCTION)
    val productionApplicationTesting = buildApplication(ApplicationState.testing, Environment.PRODUCTION)

    val sandboxApplicationTesting = buildApplication(ApplicationState.testing, Environment.SANDBOX)

    def renderPageForApplicationAndRole(application: Application, role: CollaboratorRole, pageData: PageData, request: FakeRequest[AnyContentAsEmpty.type]) = {
      manageSubscriptions.render(
        role,
        pageData,
        EditApplicationForm.withData(productionApplicationTesting),
        ApplicationViewModel(application, false, false),
        Some(GroupedSubscriptions(Seq.empty, Seq.empty)),
        Map.empty,
        ApplicationId(""),
        request,
        developer,
        messagesProvider,
        appConfig,
        "subscriptions"
      )
    }

    "render for an Admin with a PRODUCTION app in ACTIVE state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(productionApplication, None, Map.empty)

      val page: Html = renderPageForApplicationAndRole(productionApplication, CollaboratorRole.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body should include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for a Developer with a PRODUCTION app in ACTIVE state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(productionApplication, None, Map.empty)

      val page: Html = renderPageForApplicationAndRole(productionApplication, CollaboratorRole.DEVELOPER, pageData, request)

      page.contentType should include("text/html")
      page.body should include("You need admin rights to make API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for an Admin with a PRODUCTION app in CREATED state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(productionApplicationTesting, None, Map.empty)

      val page: Html = renderPageForApplicationAndRole(productionApplicationTesting, CollaboratorRole.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body shouldNot include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for a Develolper with a PRODUCTION app in CREATED state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(productionApplicationTesting, None, Map.empty)

      val page: Html = renderPageForApplicationAndRole(productionApplicationTesting, CollaboratorRole.DEVELOPER, pageData, request)

      page.contentType should include("text/html")
      page.body shouldNot include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for an Admin with a PRODUCTION app in PENDING_GATEKEEPER_APPROVAL state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(productionApplicationPendingGatekeeperApproval, None, Map.empty)

      val page: Html = renderPageForApplicationAndRole(productionApplicationPendingGatekeeperApproval, CollaboratorRole.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body should include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for an Admin with a PRODUCTION app in PENDING_REQUESTER_APPROVAL state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(productionApplicationPendingRequesterVerification, None, Map.empty)

      val page: Html = renderPageForApplicationAndRole(productionApplicationPendingRequesterVerification, CollaboratorRole.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body should include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for an Admin with a SANDBOX app in CREATED state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(sandboxApplicationTesting, None, Map.empty)

      val page: Html = renderPageForApplicationAndRole(sandboxApplicationTesting, CollaboratorRole.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body shouldNot include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for a Developer with a SANDBOX app in CREATED state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(sandboxApplicationTesting, None, Map.empty)

      val page: Html = renderPageForApplicationAndRole(sandboxApplicationTesting, CollaboratorRole.DEVELOPER, pageData, request)

      page.contentType should include("text/html")
      page.body shouldNot include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }
  }
}
