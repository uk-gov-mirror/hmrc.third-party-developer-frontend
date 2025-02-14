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
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.test.FakeRequest
import play.twirl.api.Html
import utils._
import views.helper.CommonViewSpec
import views.html.ClientIdView

import scala.collection.JavaConverters._

class ClientIdSpec extends CommonViewSpec with WithCSRFAddToken with CollaboratorTracker with LocalUserIdTracker {
  trait Setup {
    val clientIdView = app.injector.instanceOf[ClientIdView]

    def elementExistsByText(doc: Document, elementType: String, elementText: String): Boolean = {
      doc.select(elementType).asScala.exists(node => node.text.trim == elementText)
    }
  }

  "Client ID page" should {
    val request = FakeRequest().withCSRFToken
    val developer = utils.DeveloperSession("Test", "Test", "Test", None, loggedInState = LoggedInState.LOGGED_IN)

    val application = Application(
      ApplicationId("Test Application ID"),
      ClientId("Test Application Client ID"),
      "Test Application",
      DateTime.now(),
      DateTime.now(),
      None,
      Environment.PRODUCTION,
      Some("Test Application"),
      collaborators = Set(developer.email.asAdministratorCollaborator),
      access = Standard(),
      state = ApplicationState.testing,
      checkInformation = None
    )

    "render" in new Setup {
      val page: Html = clientIdView.render(application, request, developer, messagesProvider, appConfig)

      page.contentType should include("text/html")
      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Client ID") shouldBe true
    }
  }
}
