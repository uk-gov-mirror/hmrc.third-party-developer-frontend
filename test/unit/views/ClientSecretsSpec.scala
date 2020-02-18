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

package unit.views

import config.ApplicationConfig
import domain._
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits._
import play.api.mvc.Flash
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils
import utils.CSRFTokenHelper._
import utils.SharedMetricsClearDown
import views.html.clientSecrets

import scala.collection.JavaConversions._

class ClientSecretsSpec extends UnitSpec with OneServerPerSuite with SharedMetricsClearDown with MockitoSugar {
  trait Setup {
    val appConfig: ApplicationConfig = mock[ApplicationConfig]

    def elementExistsByText(doc: Document, elementType: String, elementText: String): Boolean = {
      doc.select(elementType).exists(node => node.text.trim == elementText)
    }

    def elementExistsById(doc: Document, id: String): Boolean = doc.select(s"#$id").nonEmpty
  }

  "Client secrets page" should {
    val request = FakeRequest().withCSRFToken
    val developer = utils.DeveloperSession("Test", "Test", "Test", None, loggedInState = LoggedInState.LOGGED_IN)

    val clientSecret1 = ClientSecret("", "clientSecret1Content", DateTimeUtils.now)
    val clientSecret2 = ClientSecret("", "clientSecret2Content", DateTimeUtils.now)
    val clientSecret3 = ClientSecret("", "clientSecret3Content", DateTimeUtils.now)
    val clientSecret4 = ClientSecret("", "clientSecret4Content", DateTimeUtils.now)
    val clientSecret5 = ClientSecret("", "clientSecret5Content", DateTimeUtils.now)

    val application = Application(
      "Test Application ID",
      "Test Application Client ID",
      "Test Application",
      DateTime.now(),
      DateTime.now(),
      Environment.PRODUCTION,
      Some("Test Application"),
      collaborators = Set(Collaborator(developer.email, Role.ADMINISTRATOR)),
      access = Standard(),
      state = ApplicationState.production("requester", "verificationCode"),
      checkInformation = None
    )

    "show generate a client secret button but no delete button when the app does not have any client secrets yet" in new Setup {
      val emptyClientSecrets = Seq.empty
      val page = clientSecrets.render(application, emptyClientSecrets, request, developer, applicationMessages, appConfig, Flash())

      page.contentType should include ("text/html")

      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "button", "Generate a client secret") shouldBe true
      elementExistsByText(document, "a", "Delete") shouldBe false
    }

    "show generate another client secret button but no delete button when the app has only one client secret" in new Setup {
      val oneClientSecret = Seq(clientSecret1)
      val page = clientSecrets.render(application, oneClientSecret, request, developer, applicationMessages, appConfig, Flash())

      page.contentType should include("text/html")

      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "button", "Generate another client secret") shouldBe true
      elementExistsByText(document, "a", "Delete") shouldBe false
    }

    "show copy button when a new client secret has just been added" in new Setup {
      val oneClientSecret = Seq(clientSecret1)
      val flash = Flash(Map("newSecret" -> clientSecret1.secret))

      val page = clientSecrets.render(application, oneClientSecret, request, developer, applicationMessages, appConfig, flash)

      page.contentType should include("text/html")

      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "a", "Copy") shouldBe true
    }

    "show generate another client secret button and delete button when the app has more than one client secret" in new Setup {
      val twoClientSecrets = Seq(clientSecret1, clientSecret2)
      val page = clientSecrets.render(application, twoClientSecrets, request, developer, applicationMessages, appConfig, Flash())

      page.contentType should include ("text/html")

      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "button", "Generate another client secret") shouldBe true
      elementExistsByText(document, "a", "Delete") shouldBe true
    }

    "not show generate another client secret button when the app has reached the limit of 5 client secrets" in new Setup {
      val twoClientSecrets = Seq(clientSecret1, clientSecret2, clientSecret3, clientSecret4, clientSecret5)
      val page = clientSecrets.render(application, twoClientSecrets, request, developer, applicationMessages, appConfig, Flash())

      page.contentType should include ("text/html")

      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "button", "Generate another client secret") shouldBe false
      elementExistsByText(document, "p", "Rotate your client secret regularly. You cannot have more than 5 client secrets.") shouldBe true
      elementExistsByText(document, "a", "Delete") shouldBe true
    }
  }
}
