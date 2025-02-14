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
import domain.models.applications.Environment.PRODUCTION
import domain.models.developers.LoggedInState
import model.ApplicationViewModel
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.test.FakeRequest
import uk.gov.hmrc.time.DateTimeUtils.now
import views.helper.CommonViewSpec
import views.html.include.LeftHandNav

import scala.collection.JavaConverters._
import utils.CollaboratorTracker
import utils.LocalUserIdTracker

class LeftHandNavSpec extends CommonViewSpec with CollaboratorTracker with LocalUserIdTracker {

  val leftHandNavView = app.injector.instanceOf[LeftHandNav]

  trait Setup {
    val applicationId = ApplicationId("std-app-id")
    val clientId = ClientId("std-client-id")
    implicit val request = FakeRequest()
    implicit val loggedIn = utils.DeveloperSession("user@example.com", "Test", "Test", None, loggedInState = LoggedInState.LOGGED_IN)
    val standardApplication = Application(applicationId, clientId, "name", now, now, None, PRODUCTION, access = Standard())
    val privilegedApplication = Application(applicationId, clientId, "name", now, now, None, PRODUCTION, access = Privileged())
    val ropcApplication = Application(applicationId, clientId, "name", now, now, None, PRODUCTION, access = ROPC())

    def elementExistsById(doc: Document, id: String) = doc.select(s"#$id").asScala.nonEmpty
  }

  "Left Hand Nav" should {

    "include links to manage API subscriptions, credentials and team members for an app with standard access" in new Setup {
      val document: Document = Jsoup.parse(leftHandNavView(Some(ApplicationViewModel(standardApplication, hasSubscriptionsFields = false, hasPpnsFields = false)), Some("")).body)

      elementExistsById(document, "nav-manage-subscriptions") shouldBe true
      elementExistsById(document, "nav-manage-credentials") shouldBe true
      elementExistsById(document, "nav-manage-client-id") shouldBe false
      elementExistsById(document, "nav-manage-client-secrets") shouldBe false
      elementExistsById(document, "nav-manage-team") shouldBe true
      elementExistsById(document, "nav-delete-application") shouldBe true
    }

    "include links to manage team members but not API subscriptions for an app with privileged access" in new Setup {
      val document: Document = Jsoup.parse(leftHandNavView(Some(ApplicationViewModel(privilegedApplication, hasSubscriptionsFields = false, hasPpnsFields = false)), Some("")).body)

      elementExistsById(document, "nav-manage-subscriptions") shouldBe false
      elementExistsById(document, "nav-manage-credentials") shouldBe true
      elementExistsById(document, "nav-manage-client-id") shouldBe false
      elementExistsById(document, "nav-manage-client-secrets") shouldBe false
      elementExistsById(document, "nav-manage-team") shouldBe true
      elementExistsById(document, "nav-delete-application") shouldBe false
    }

    "include links to manage team members but not API subscriptions for an app with ROPC access" in new Setup {
      val document: Document = Jsoup.parse(leftHandNavView(Some(ApplicationViewModel(ropcApplication, hasSubscriptionsFields = false, hasPpnsFields = false)), Some("")).body)

      elementExistsById(document, "nav-manage-subscriptions") shouldBe false
      elementExistsById(document, "nav-manage-credentials") shouldBe true
      elementExistsById(document, "nav-manage-client-id") shouldBe false
      elementExistsById(document, "nav-manage-client-secrets") shouldBe false
      elementExistsById(document, "nav-manage-team") shouldBe true
      elementExistsById(document, "nav-delete-application") shouldBe false
    }

    "include links to client ID and client secrets if the user is an admin and the app has reached production state" in new Setup {
      val application = standardApplication.copy(collaborators = Set(loggedIn.email.asAdministratorCollaborator), state = ApplicationState.production("", ""))

      val document: Document = Jsoup.parse(leftHandNavView(Some(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false)), Some("")).body)

      elementExistsById(document, "nav-manage-client-id") shouldBe true
      elementExistsById(document, "nav-manage-client-secrets") shouldBe true
    }

    "include links to client ID and client secrets if the user is not an admin but the app is in sandbox" in new Setup {
      val application =
        standardApplication.copy(deployedTo = Environment.SANDBOX, collaborators = Set(loggedIn.email.asDeveloperCollaborator), state = ApplicationState.production("", ""))

      val document: Document = Jsoup.parse(leftHandNavView(Some(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false)), Some("")).body)

      elementExistsById(document, "nav-manage-client-id") shouldBe true
      elementExistsById(document, "nav-manage-client-secrets") shouldBe true
    }

    "include link to push secrets when the application has PPNS fields and the user is an admin or the application is sandbox" in new Setup {
      val prodAppAsAdmin = standardApplication.copy(deployedTo = Environment.PRODUCTION, collaborators = Set(loggedIn.email.asAdministratorCollaborator))
      val sandboxAppAsDev = standardApplication.copy(deployedTo = Environment.SANDBOX, collaborators = Set(loggedIn.email.asDeveloperCollaborator))

      val prodAppAsAdminDocument: Document = Jsoup.parse(leftHandNavView(Some(ApplicationViewModel(prodAppAsAdmin, hasSubscriptionsFields = true, hasPpnsFields = true)), Some("")).body)
      val sandboxAppAsDevDocument: Document = Jsoup.parse(leftHandNavView(Some(ApplicationViewModel(sandboxAppAsDev, hasSubscriptionsFields = true, hasPpnsFields = true)), Some("")).body)

      elementExistsById(prodAppAsAdminDocument, "nav-manage-push-secrets") shouldBe true
      elementExistsById(sandboxAppAsDevDocument, "nav-manage-push-secrets") shouldBe true
    }

    "not include link to push secrets when the application does not have PPNS fields, or it does, but the user is only a developer for a production app" in new Setup {
      val prodAppAsAdmin = standardApplication.copy(deployedTo = Environment.PRODUCTION, collaborators = Set(loggedIn.email.asAdministratorCollaborator))
      val sandboxAppAsDev = standardApplication.copy(deployedTo = Environment.SANDBOX, collaborators = Set(loggedIn.email.asDeveloperCollaborator))
      val prodAppAsDev = standardApplication.copy(deployedTo = Environment.PRODUCTION, collaborators = Set(loggedIn.email.asDeveloperCollaborator))

      val prodAppAsAdminDocument: Document = Jsoup.parse(leftHandNavView(Some(ApplicationViewModel(prodAppAsAdmin, hasSubscriptionsFields = true, hasPpnsFields = false)), Some("")).body)
      val sandboxAppAsDevDocument: Document = Jsoup.parse(leftHandNavView(Some(ApplicationViewModel(sandboxAppAsDev, hasSubscriptionsFields = true, hasPpnsFields = false)), Some("")).body)
      val prodAppAsDevDocument: Document = Jsoup.parse(leftHandNavView(Some(ApplicationViewModel(prodAppAsDev, hasSubscriptionsFields = true, hasPpnsFields = true)), Some("")).body)

      elementExistsById(prodAppAsAdminDocument, "nav-manage-push-secrets") shouldBe false
      elementExistsById(sandboxAppAsDevDocument, "nav-manage-push-secrets") shouldBe false
      elementExistsById(prodAppAsDevDocument, "nav-manage-push-secrets") shouldBe false
    }
  }
}
