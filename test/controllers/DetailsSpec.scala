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

package controllers

import builder.DeveloperBuilder
import domain._
import domain.models.applications._
import domain.models.developers.{DeveloperSession, LoggedInState, Session}
import mocks.service._
import org.jsoup.Jsoup
import org.mockito.captor.ArgCaptor
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.{FakeRequest, Helpers}
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.SessionService
import uk.gov.hmrc.http.HeaderCarrier
import utils.ViewHelpers._
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import views.html.{ChangeDetailsView, DetailsView}
import views.html.application.PendingApprovalView
import views.html.checkpages.applicationcheck.UnauthorisedAppDetailsView

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future._
import utils.LocalUserIdTracker
import utils.TestApplications
import utils.CollaboratorTracker

class DetailsSpec 
    extends BaseControllerSpec 
    with WithCSRFAddToken 
    with TestApplications 
    with DeveloperBuilder 
    with CollaboratorTracker 
    with LocalUserIdTracker {

  Helpers.running(app) {
    "details" when {
      "logged in as a Developer on an application" should {
        "return the view for a standard production app with no change link" in new Setup {
          val approvedApplication = anApplication(developerEmail = loggedInUser.email)
          detailsShouldRenderThePage(approvedApplication, hasChangeButton = false)
        }

        "return the view for a developer on a sandbox app" in new Setup {
          detailsShouldRenderThePage(aSandboxApplication(developerEmail = loggedInUser.email))
        }
      }

      "logged in as an Administrator on an application" should {
        "return the view for a standard production app" in new Setup {
          val approvedApplication = anApplication(adminEmail = loggedInUser.email)
          detailsShouldRenderThePage(approvedApplication)
        }

        "return the view for an admin on a sandbox app" in new Setup {
          detailsShouldRenderThePage(aSandboxApplication(adminEmail = loggedInUser.email))
        }

        "return a redirect when using an application in testing state" in new Setup {
          val testingApplication = anApplication(adminEmail = loggedInUser.email, state = ApplicationState.testing)

          givenApplicationAction(testingApplication, loggedInUser)

          val result = testingApplication.callDetails

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(s"/developer/applications/${testingApplication.id.value}/request-check")
        }

        "return the credentials requested page on an application pending approval" in new Setup {
          val pendingApprovalApplication = anApplication(adminEmail = loggedInUser.email, state = ApplicationState.pendingGatekeeperApproval("dont-care"))

          givenApplicationAction(pendingApprovalApplication, loggedInUser)

          val result = addToken(underTest.details(pendingApprovalApplication.id))(loggedInRequest)

          status(result) shouldBe OK

          val document = Jsoup.parse(contentAsString(result))
          elementExistsByText(document, "h1", "Credentials requested") shouldBe true
          elementExistsByText(document, "span", pendingApprovalApplication.name) shouldBe true
        }

        "return the credentials requested page on an application pending verification" in new Setup {
          val pendingVerificationApplication = anApplication(adminEmail = loggedInUser.email, state = ApplicationState.pendingRequesterVerification("dont-care", "dont-care"))

          givenApplicationAction(pendingVerificationApplication, loggedInUser)

          val result = addToken(underTest.details(pendingVerificationApplication.id))(loggedInRequest)

          status(result) shouldBe OK

          val document = Jsoup.parse(contentAsString(result))
          elementExistsByText(document, "h1", "Credentials requested") shouldBe true
          elementExistsByText(document, "span", pendingVerificationApplication.name) shouldBe true
        }
      }

      "not a team member on an application" should {
        "return not found" in new Setup {
          val application = aStandardApplication
          givenApplicationAction(application, loggedInUser)

          val result = application.callDetails

          status(result) shouldBe NOT_FOUND
        }
      }

      "not logged in" should {
        "redirect to login" in new Setup {
          val application = aStandardApplication
          givenApplicationAction(application, loggedInUser)

          val result = application.callDetailsNotLoggedIn

          redirectsToLogin(result)
        }
      }
    }

    "changeDetails" should {
      "return the view for an admin on a standard production app" in new Setup {
        changeDetailsShouldRenderThePage(
          anApplication(adminEmail = loggedInUser.email)
        )
      }

      "return the view for a developer on a sandbox app" in new Setup {
        changeDetailsShouldRenderThePage(
          aSandboxApplication(developerEmail = loggedInUser.email)
        )
      }

      "return the view for an admin on a sandbox app" in new Setup {
        changeDetailsShouldRenderThePage(
          aSandboxApplication(adminEmail = loggedInUser.email)
        )
      }

      "return forbidden for a developer on a standard production app" in new Setup {
        val application = anApplication(developerEmail = loggedInUser.email)
        givenApplicationAction(application, loggedInUser)

        val result = application.callChangeDetails

        status(result) shouldBe FORBIDDEN
      }

      "return not found when not a teamMember on the app" in new Setup {
        val application = aStandardApprovedApplication
        givenApplicationAction(application, loggedInUser)

        val result = application.callChangeDetails

        status(result) shouldBe NOT_FOUND
      }

      "redirect to login when not logged in" in new Setup {
        val application = aStandardApprovedApplication
        givenApplicationAction(application, loggedInUser)

        val result = application.callDetailsNotLoggedIn

        redirectsToLogin(result)
      }

      "return not found for an ROPC application" in new Setup {
        val application = anROPCApplication()
        givenApplicationAction(application, loggedInUser)

        val result = underTest.details(application.id)(loggedInRequest)

        status(result) shouldBe NOT_FOUND
      }

      "return not found for a privileged application" in new Setup {
        val application = aPrivilegedApplication()
        givenApplicationAction(application, loggedInUser)

        val result = underTest.details(application.id)(loggedInRequest)

        status(result) shouldBe NOT_FOUND
      }
    }

    "changeDetailsAction validation" should {
      "not pass when application is updated with empty name" in new Setup {
        val application = anApplication(adminEmail = loggedInUser.email)
        givenApplicationAction(application, loggedInUser)

        val result = application.withName("").callChangeDetailsAction

        status(result) shouldBe BAD_REQUEST
      }

      "not pass when application is updated with invalid name" in new Setup {
        val application = anApplication(adminEmail = loggedInUser.email)
        givenApplicationAction(application, loggedInUser)

        val result = application.withName("a").callChangeDetailsAction

        status(result) shouldBe BAD_REQUEST
      }

      "update name which contain HMRC should fail" in new Setup {
        when(underTest.applicationService.isApplicationNameValid(*, *, *)(*))
          .thenReturn(Future.successful(Invalid.invalidName))

        val application = anApplication(adminEmail = loggedInUser.email)
        givenApplicationAction(application, loggedInUser)

        val result = application.withName("my invalid HMRC application name").callChangeDetailsAction

        status(result) shouldBe BAD_REQUEST

        verify(underTest.applicationService).isApplicationNameValid(eqTo("my invalid HMRC application name"), eqTo(application.deployedTo), eqTo(Some(application.id)))(
          *
        )
      }
    }

    "changeDetailsAction for production app in testing state" should {

      "return not found" in new Setup {
        val application = aStandardNonApprovedApplication()
        givenApplicationAction(application, loggedInUser)

        val result = application.callChangeDetails

        status(result) shouldBe NOT_FOUND
      }

      "return not found when not a teamMember on the app" in new Setup {
        val application = aStandardApprovedApplication
        givenApplicationAction(application, loggedInUser)

        val result = application.withDescription(newDescription).callChangeDetailsAction

        status(result) shouldBe NOT_FOUND
      }

      "redirect to login when not logged in" in new Setup {
        val application = aStandardApprovedApplication
        givenApplicationAction(application, loggedInUser)

        val result = application.withDescription(newDescription).callChangeDetailsActionNotLoggedIn

        redirectsToLogin(result)
      }
    }

    "changeDetailsAction for production app in uplifted state" should {

      "redirect to the details page on success for an admin" in new Setup {
        val application = anApplication(adminEmail = loggedInUser.email)

        changeDetailsShouldRedirectOnSuccess(application)
      }

      "return forbidden for a developer" in new Setup {
        val application = anApplication(developerEmail = loggedInUser.email)

        givenApplicationAction(application, loggedInUser)

        val result = application.withDescription(newDescription).callChangeDetailsAction

        status(result) shouldBe FORBIDDEN
      }

      "keep original application name when administrator does an update" in new Setup {
        val application = anApplication(adminEmail = loggedInUser.email)

        givenApplicationAction(application, loggedInUser)

        application.withName(newName).callChangeDetailsAction

        val updatedApplication = captureUpdatedApplication
        updatedApplication.name shouldBe application.name
      }
    }

    "changeDetailsAction for sandbox app" should {

      "redirect to the details page on success for an admin" in new Setup {
        changeDetailsShouldRedirectOnSuccess(aSandboxApplication(adminEmail = loggedInUser.email))
      }

      "redirect to the details page on success for a developer" in new Setup {
        changeDetailsShouldRedirectOnSuccess(aSandboxApplication(developerEmail = loggedInUser.email))
      }

      "update all fields for an admin" in new Setup {
        changeDetailsShouldUpdateTheApplication(aSandboxApplication(adminEmail = loggedInUser.email))
      }

      "update all fields for a developer" in new Setup {
        changeDetailsShouldUpdateTheApplication(aSandboxApplication(adminEmail = loggedInUser.email))
      }

      "update the app but not the check information" in new Setup {
        val application = aSandboxApplication(adminEmail = loggedInUser.email)
        givenApplicationAction(application, loggedInUser)

        await(application.withName(newName).callChangeDetailsAction)

        verify(underTest.applicationService).update(any[UpdateApplicationRequest])(*)
        verify(underTest.applicationService, never).updateCheckInformation(eqTo(application), any[CheckInformation])(*)
      }
    }
  }

  trait Setup extends ApplicationServiceMock with ApplicationActionServiceMock {
    val unauthorisedAppDetailsView = app.injector.instanceOf[UnauthorisedAppDetailsView]
    val pendingApprovalView = app.injector.instanceOf[PendingApprovalView]
    val detailsView = app.injector.instanceOf[DetailsView]
    val changeDetailsView = app.injector.instanceOf[ChangeDetailsView]

    val underTest = new Details(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      mock[SessionService],
      mcc,
      cookieSigner,
      unauthorisedAppDetailsView,
      pendingApprovalView,
      detailsView,
      changeDetailsView
    )

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val developer = buildDeveloper()
    val sessionId = "sessionId"
    val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

    val loggedInUser = DeveloperSession(session)

    val newName = "new name"
    val newDescription = Some("new description")
    val newTermsUrl = Some("http://example.com/new-terms")
    val newPrivacyUrl = Some("http://example.com/new-privacy")

    when(underTest.applicationService.isApplicationNameValid(*, *, *)(*))
      .thenReturn(Future.successful(Valid))

    when(underTest.sessionService.fetch(eqTo(sessionId))(*))
      .thenReturn(successful(Some(session)))

    when(underTest.sessionService.updateUserFlowSessions(sessionId)).thenReturn(successful(()))

    when(underTest.applicationService.update(any[UpdateApplicationRequest])(*))
      .thenReturn(successful(ApplicationUpdateSuccessful))

    when(underTest.applicationService.updateCheckInformation(any[Application], any[CheckInformation])(*))
      .thenReturn(successful(ApplicationUpdateSuccessful))

    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)

    def captureUpdatedApplication: UpdateApplicationRequest = {
      val captor = ArgCaptor[UpdateApplicationRequest]
      verify(underTest.applicationService).update(captor)(*)
      captor.value
    }

    def redirectsToLogin(result: Future[Result]) = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
    }

    def detailsShouldRenderThePage(application: Application, hasChangeButton: Boolean = true) = {
      givenApplicationAction(application, loggedInUser)

      val result = application.callDetails

      status(result) shouldBe OK
      val doc = Jsoup.parse(contentAsString(result))
      linkExistsWithHref(doc, routes.Details.changeDetails(application.id).url) shouldBe hasChangeButton
      elementIdentifiedByIdContainsText(doc, "applicationId", application.id.value) shouldBe true
      elementIdentifiedByIdContainsText(doc, "applicationName", application.name) shouldBe true
      elementIdentifiedByIdContainsText(doc, "description", application.description.getOrElse("None")) shouldBe true
      elementIdentifiedByIdContainsText(doc, "privacyPolicyUrl", application.privacyPolicyUrl.getOrElse("None")) shouldBe true
      elementIdentifiedByIdContainsText(doc, "termsAndConditionsUrl", application.termsAndConditionsUrl.getOrElse("None")) shouldBe true
    }

    def changeDetailsShouldRenderThePage(application: Application) = {
      givenApplicationAction(application, loggedInUser)

      val result = application.callChangeDetails

      status(result) shouldBe OK
      val doc = Jsoup.parse(contentAsString(result))
      formExistsWithAction(doc, routes.Details.changeDetailsAction(application.id).url) shouldBe true
      linkExistsWithHref(doc, routes.Details.details(application.id).url) shouldBe true
      inputExistsWithValue(doc, "applicationId", "hidden", application.id.value) shouldBe true
      if (application.deployedTo == Environment.SANDBOX || application.state.name == State.TESTING) {
        inputExistsWithValue(doc, "applicationName", "text", application.name) shouldBe true
      } else {
        inputExistsWithValue(doc, "applicationName", "hidden", application.name) shouldBe true
      }
      textareaExistsWithText(doc, "description", application.description.getOrElse("None")) shouldBe true
      inputExistsWithValue(doc, "privacyPolicyUrl", "text", application.privacyPolicyUrl.getOrElse("None")) shouldBe true
      inputExistsWithValue(doc, "termsAndConditionsUrl", "text", application.termsAndConditionsUrl.getOrElse("None")) shouldBe true
    }

    def changeDetailsShouldRedirectOnSuccess(application: Application) = {
      givenApplicationAction(application, loggedInUser)

      val result = application.withDescription(newDescription).callChangeDetailsAction

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.Details.details(application.id).url)
    }

    def changeDetailsShouldUpdateTheApplication(application: Application) = {
      givenApplicationAction(application, loggedInUser)

      await(
        application
          .withName(newName)
          .withDescription(newDescription)
          .withTermsAndConditionsUrl(newTermsUrl)
          .withPrivacyPolicyUrl(newPrivacyUrl)
          .callChangeDetailsAction
      )

      val updatedApplication = captureUpdatedApplication
      updatedApplication.name shouldBe newName
      updatedApplication.description shouldBe newDescription
      updatedApplication.access match {
        case access: Standard =>
          access.termsAndConditionsUrl shouldBe newTermsUrl
          access.privacyPolicyUrl shouldBe newPrivacyUrl

        case _ => fail("Expected AccessType of STANDARD")
      }
    }

    implicit val format = Json.format[EditApplicationForm]

    implicit class ChangeDetailsAppAugment(val app: Application) {
      private val appAccess = app.access.asInstanceOf[Standard]

      final def toForm = EditApplicationForm(app.id, app.name, app.description, appAccess.privacyPolicyUrl, appAccess.termsAndConditionsUrl)

      final def callDetails: Future[Result] = underTest.details(app.id)(loggedInRequest)

      final def callDetailsNotLoggedIn: Future[Result] = underTest.details(app.id)(loggedOutRequest)

      final def callChangeDetails: Future[Result] = addToken(underTest.changeDetails(app.id))(loggedInRequest)

      final def callChangeDetailsNotLoggedIn: Future[Result] = addToken(underTest.changeDetails(app.id))(loggedOutRequest)

      final def callChangeDetailsAction: Future[Result] = callChangeDetailsAction(loggedInRequest)

      final def callChangeDetailsActionNotLoggedIn: Future[Result] = callChangeDetailsAction(loggedOutRequest)

      private final def callChangeDetailsAction[T](request: FakeRequest[T]): Future[Result] = {
        addToken(underTest.changeDetailsAction(app.id))(request.withJsonBody(Json.toJson(app.toForm)))
      }
    }
  }
}
