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

package unit.views.applicationCheck

import config.ApplicationConfig
import controllers.TermsOfUseForm
import domain._
import org.jsoup.Jsoup
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.i18n.Messages.Implicits._
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.time.DateTimeUtils
import utils.CSRFTokenHelper._

class TermsOfUseSpec extends PlaySpec with OneAppPerSuite with MockitoSugar {

  val appConfig: ApplicationConfig = mock[ApplicationConfig]

  "Terms of use view" must {
    val thirdPartyApplication =
      Application(
        "APPLICATION_ID",
        "CLIENT_ID",
        "APPLICATION NAME",
        DateTimeUtils.now,
        DateTimeUtils.now,
        Environment.PRODUCTION,
        Some("APPLICATION DESCRIPTION"),
        Set(Collaborator("sample@example.com", Role.ADMINISTRATOR), Collaborator("someone@example.com", Role.DEVELOPER)),
        Standard(),
        ApplicationState(State.TESTING, None, None, DateTimeUtils.now)
      )

    "show terms of use agreement page that requires terms of use to be agreed" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val checkInformation = CheckInformation()

      val termsOfUseForm = TermsOfUseForm.fromCheckInformation(checkInformation)
      val developer = utils.DeveloperSession("email@example.com", "First Name", "Last Name", None, loggedInState = LoggedInState.LOGGED_IN)

      val page = views.html.applicationcheck.termsOfUse.render(
        app = thirdPartyAppplication,
        form = TermsOfUseForm.form.fill(termsOfUseForm),
        mode = CheckYourAnswersPageMode.RequestCheck,
        request,
        developer,
        applicationMessages,
        appConfig)
      page.contentType must include("text/html")

      val document = Jsoup.parse(page.body)
      document.getElementById("termsOfUseAgreed") mustNot be(null)
      document.getElementById("termsOfUseAgreed").attr("checked") mustNot be("checked")
    }

    "show terms of use agreement page that already has the correct terms of use agreed" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val appConfigMock = mock[ApplicationConfig]
      val termsOfUseAgreement = TermsOfUseAgreement("email@example.com", DateTimeUtils.now, "1.0")

      val checkInformation = CheckInformation(termsOfUseAgreements = Seq(termsOfUseAgreement))

      val termsOfUseForm = TermsOfUseForm.fromCheckInformation(checkInformation)
      val developer = utils.DeveloperSession("email@example.com", "First Name", "Last Name", None, loggedInState = LoggedInState.LOGGED_IN)

      val page = views.html.applicationcheck.termsOfUse.render(
        app = thirdPartyAppplication.copy(checkInformation = Some(checkInformation)),
        form = TermsOfUseForm.form.fill(termsOfUseForm),
        mode = CheckYourAnswersPageMode.RequestCheck,
        request,
        developer,
        implicitly,
        appConfigMock)
      page.contentType must include("text/html")

      page.body.contains("Terms of use agreed by email@example.com") mustBe true
    }
  }
}
