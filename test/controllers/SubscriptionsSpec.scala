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

import java.util.UUID.randomUUID

import builder.DeveloperBuilder
import connectors.ThirdPartyDeveloperConnector
import domain.models.apidefinitions.{ApiContext, ApiVersion,ApiIdentifier}
import domain.models.applications._
import domain.models.connectors.TicketResult
import domain.models.developers.{Developer, DeveloperSession, LoggedInState, Session}
import mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock, SessionServiceMock}
import org.joda.time.DateTimeZone
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import views.helper.EnvironmentNameService
import views.html.{AddAppSubscriptionsView, ManageSubscriptionsView, SubscribeRequestSubmittedView, UnsubscribeRequestSubmittedView}
import views.html.include.ChangeSubscriptionConfirmationView
import domain.models.developers.UserId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful
import utils.LocalUserIdTracker

class SubscriptionsSpec extends BaseControllerSpec with SubscriptionTestHelperSugar with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker {

  val apiName = "api-1"
  val apiVersion = ApiVersion("1.0")
  val apiContext = ApiContext("Context")
  val apiIdentifier = ApiIdentifier(apiContext, apiVersion)
  val displayStatus = "Status"

  val developer: Developer = buildDeveloper()
  val sessionId = "sessionId"
  val session: Session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

  val loggedInDeveloper: DeveloperSession = DeveloperSession(session)

  val anApplication: Application = Application(
    appId,
    clientId,
    "App name 1",
    DateTimeUtils.now,
    DateTimeUtils.now,
    None,
    Environment.PRODUCTION,
    Some("Description 1"),
    Set(loggedInDeveloper.email.asAdministratorCollaborator),
    state = ApplicationState.production(loggedInDeveloper.email, ""),
    access = Standard(redirectUris = List("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com"))
  )

  val activeApplication: Application = anApplication

  val activeDeveloperApplication: Application = anApplication.copy(collaborators = Set(loggedInDeveloper.email.asDeveloperCollaborator))

  val ropcApplication: Application = anApplication.copy(access = ROPC())

  val privilegedApplication: Application = anApplication.copy(access = Privileged())

  val newApplication: Application = anApplication.copy(state = ApplicationState.testing)

  val newSandboxApplication: Application = anApplication.copy(deployedTo = Environment.SANDBOX, state = ApplicationState.testing)

  val adminApplication: Application = anApplication.copy(collaborators = Set(loggedInDeveloper.email.asAdministratorCollaborator))
  val developerApplication: Application = anApplication.copy(collaborators = Set(loggedInDeveloper.email.asDeveloperCollaborator))

  val adminSubmittedProductionApplication: Application =
    adminApplication.copy(deployedTo = Environment.PRODUCTION, state = ApplicationState.production(loggedInDeveloper.email, ""))
  val adminCreatedProductionApplication: Application = adminApplication.copy(deployedTo = Environment.PRODUCTION, state = ApplicationState.testing)
  val adminSubmittedSandboxApplication: Application = adminApplication.copy(deployedTo = Environment.SANDBOX, state = ApplicationState.production(loggedInDeveloper.email, ""))
  val adminCreatedSandboxApplication: Application = adminApplication.copy(deployedTo = Environment.SANDBOX, state = ApplicationState.testing)
  val developerSubmittedProductionApplication: Application =
    developerApplication.copy(deployedTo = Environment.PRODUCTION, state = ApplicationState.production(loggedInDeveloper.email, ""))
  val developerCreatedProductionApplication: Application = developerApplication.copy(deployedTo = Environment.PRODUCTION, state = ApplicationState.testing)
  val developerSubmittedSandboxApplication: Application =
    developerApplication.copy(deployedTo = Environment.SANDBOX, state = ApplicationState.production(loggedInDeveloper.email, ""))
  val devloperCreatedSandboxApplication: Application = developerApplication.copy(deployedTo = Environment.SANDBOX, state = ApplicationState.testing)

  val tokens: ApplicationToken = ApplicationToken(List(aClientSecret(), aClientSecret()), "token")

  trait Setup extends ApplicationServiceMock with SessionServiceMock with ApplicationActionServiceMock {
    val manageSubscriptionsView = app.injector.instanceOf[ManageSubscriptionsView]
    val addAppSubscriptionsView = app.injector.instanceOf[AddAppSubscriptionsView]
    val changeSubscriptionConfirmationView = app.injector.instanceOf[ChangeSubscriptionConfirmationView]
    val unsubscribeRequestSubmittedView = app.injector.instanceOf[UnsubscribeRequestSubmittedView]
    val subscribeRequestSubmittedView = app.injector.instanceOf[SubscribeRequestSubmittedView]
    implicit val environmentNameService = new EnvironmentNameService(appConfig)

    val underTest = new Subscriptions(
      mock[ThirdPartyDeveloperConnector],
      mock[AuditService],
      mockErrorHandler,
      applicationServiceMock,
      mock[SubscriptionsService],
      applicationActionServiceMock,
      sessionServiceMock,
      mcc,
      cookieSigner,
      manageSubscriptionsView,
      addAppSubscriptionsView,
      changeSubscriptionConfirmationView,
      unsubscribeRequestSubmittedView,
      subscribeRequestSubmittedView
    )

    implicit val hc: HeaderCarrier = HeaderCarrier()

    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)
    givenApplicationUpdateSucceeds()
    fetchByApplicationIdReturns(activeApplication.id, activeApplication)

    val subsData = List(
      exampleSubscriptionWithFields("api1", 1),
      exampleSubscriptionWithFields("api2", 1)
    )

    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
  }

  "subscriptions" should {
    "return the ROPC page for a ROPC app" in new Setup {
      fetchByApplicationIdReturns(appId, ropcApplication)
      givenApplicationAction(ApplicationWithSubscriptionData(ropcApplication, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
      val result = addToken(underTest.manageSubscriptions(appId))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "return the privileged page for a privileged app" in new Setup {
      fetchByApplicationIdReturns(appId, privilegedApplication)
      givenApplicationAction(ApplicationWithSubscriptionData(privilegedApplication, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
      val result = addToken(underTest.manageSubscriptions(appId))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "return the subscriptions page for a developer on a standard app" in new Setup {
      fetchByApplicationIdReturns(appId, activeApplication)
      givenApplicationAction(ApplicationWithSubscriptionData(activeApplication, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
      val result = addToken(underTest.manageSubscriptions(appId))(loggedInRequest)
      status(result) shouldBe OK
      titleOf(result) shouldBe "Manage API subscriptions - HMRC Developer Hub - GOV.UK"
    }
  }

  "subscriptions2" should {
    "return the ROPC page for a ROPC app" in new Setup {
      fetchByApplicationIdReturns(appId, ropcApplication)
      givenApplicationAction(ApplicationWithSubscriptionData(ropcApplication, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
      val result = addToken(underTest.addAppSubscriptions(appId))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "return the privileged page for a privileged app" in new Setup {
      fetchByApplicationIdReturns(appId, privilegedApplication)
      givenApplicationAction(ApplicationWithSubscriptionData(privilegedApplication, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
      val result = addToken(underTest.addAppSubscriptions(appId))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "return the subscriptions page for a developer on a standard app" in new Setup {
      fetchByApplicationIdReturns(appId, activeApplication)
      givenApplicationAction(ApplicationWithSubscriptionData(activeApplication, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
      val result = addToken(underTest.addAppSubscriptions(appId))(loggedInRequest)
      status(result) shouldBe OK
      titleOf(result) shouldBe "Which APIs do you want to use? - HMRC Developer Hub - GOV.UK"
    }

    "return the subscriptions page for a developer on a standard app in the Sandbox environment" in new Setup {
      when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
      when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")
      fetchByApplicationIdReturns(appId, activeApplication)
      givenApplicationAction(ApplicationWithSubscriptionData(activeApplication, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

      val result = addToken(underTest.addAppSubscriptions(appId))(loggedInRequest)
      status(result) shouldBe OK
      titleOf(result) shouldBe "Which APIs do you want to use? - HMRC Developer Hub - GOV.UK"
      contentAsString(result) should include("Subscribe to the APIs you want to use in the sandbox")
    }

    "return the subscriptions page for a developer on a standard app in the Development environment" in new Setup {
      when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
      when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")
      fetchByApplicationIdReturns(appId, activeApplication)
      givenApplicationAction(ApplicationWithSubscriptionData(activeApplication, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

      val result = addToken(underTest.addAppSubscriptions(appId))(loggedInRequest)
      status(result) shouldBe OK
      titleOf(result) shouldBe "Which APIs do you want to use? - HMRC Developer Hub - GOV.UK"
      contentAsString(result) should include("Subscribe to the APIs you want to use in development")
    }
  }

  "changeApiSubscription" when {
    def forbiddenSubscriptionChange(app: => Application): Unit = {
      "return 400 Bad Request" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${app.id}/change-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("subscribed" -> "true")

        fetchByApplicationIdReturns(appId, app)
        givenApplicationAction(ApplicationWithSubscriptionData(app, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)

        val result = underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe BAD_REQUEST
      }
    }

    def allowedSubscriptionChange(app: => Application): Unit = {
      "successfully subscribe to an API and redirect" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${app.id}/change-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("subscribed" -> "true")

        fetchByApplicationIdReturns(appId, app)
        givenApplicationAction(ApplicationWithSubscriptionData(app, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
        givenSubscribeToApiSucceeds(app, apiIdentifier)
        givenUpdateCheckInformationSucceeds(app)

        val result = underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.Details.details(app.id).url)

        verify(applicationServiceMock).subscribeToApi(eqTo(app), eqTo(apiIdentifier))(*)
        verify(applicationServiceMock, never).updateCheckInformation(eqTo(app), any[CheckInformation])(*)
      }

      "successfully unsubscribe from an API and redirect" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${app.id}/change-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("subscribed" -> "false")

        fetchByApplicationIdReturns(appId, app)
        givenApplicationAction(ApplicationWithSubscriptionData(app, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
        ungivenSubscribeToApiSucceeds(app, apiIdentifier)
        givenUpdateCheckInformationSucceeds(app)

        val result = underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.Details.details(app.id).url)

        verify(applicationServiceMock).unsubscribeFromApi(eqTo(app), eqTo(apiIdentifier))(*)
        verify(applicationServiceMock, never).updateCheckInformation(eqTo(app), any[CheckInformation])(*)
      }

      "return a Bad Request without changing the subscription when requesting a change to the subscription when the form is invalid" in new Setup {
        val redirectTo = "APPLICATION_CHECK_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${app.id}/change-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody()

        fetchByApplicationIdReturns(appId, app)
        givenApplicationAction(ApplicationWithSubscriptionData(app, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
        ungivenSubscribeToApiSucceeds(app, apiIdentifier)
        givenUpdateCheckInformationSucceeds(app)

        val result = underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe BAD_REQUEST

        verify(applicationServiceMock, never).unsubscribeFromApi(eqTo(app), eqTo(apiIdentifier))(*)
        verify(applicationServiceMock, never).updateCheckInformation(eqTo(app), any[CheckInformation])(*)
      }
    }

    def allowedSubscriptionChangeWithCheckUpdate(app: => Application): Unit = {
      "successfully subscribe to an API, update the check information and redirect" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${app.id}/change-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("subscribed" -> "true")

        fetchByApplicationIdReturns(appId, app)
        givenApplicationAction(ApplicationWithSubscriptionData(app, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
        givenSubscribeToApiSucceeds(app, apiIdentifier)
        givenUpdateCheckInformationSucceeds(app)

        val result = underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.Details.details(app.id).url)

        verify(applicationServiceMock).subscribeToApi(eqTo(app), eqTo(apiIdentifier))(*)
        verify(applicationServiceMock).updateCheckInformation(eqTo(app), any[CheckInformation])(*)
      }

      "successfully unsubscribe from an API, update the check information and redirect" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${app.id}/change-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("subscribed" -> "false")

        fetchByApplicationIdReturns(appId, app)

        givenApplicationAction(ApplicationWithSubscriptionData(app, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
        ungivenSubscribeToApiSucceeds(app, apiIdentifier)
        givenUpdateCheckInformationSucceeds(app)

        val result = underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.Details.details(app.id).url)

        verify(applicationServiceMock).unsubscribeFromApi(eqTo(app), eqTo(apiIdentifier))(*)
        verify(applicationServiceMock).updateCheckInformation(eqTo(app), any[CheckInformation])(*)
      }

      "return a Bad Request without changing the subscription or check information when requesting a change to the subscription when the form is invalid" in new Setup {
        val redirectTo = "APPLICATION_CHECK_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${app.id}/change-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody()

        fetchByApplicationIdReturns(appId, app)

        givenApplicationAction(ApplicationWithSubscriptionData(app, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
        ungivenSubscribeToApiSucceeds(app, apiIdentifier)
        givenUpdateCheckInformationSucceeds(app)

        val result = underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe BAD_REQUEST

        verify(applicationServiceMock, never).unsubscribeFromApi(eqTo(app), eqTo(apiIdentifier))(*)
        verify(applicationServiceMock, never).updateCheckInformation(eqTo(app), any[CheckInformation])(*)
      }
    }

    "an administrator attempts to change a submitted-for-checking production application" should { behave like forbiddenSubscriptionChange(adminSubmittedProductionApplication) }
    "an administrator attempts to change a created production application" should { behave like allowedSubscriptionChangeWithCheckUpdate(adminCreatedProductionApplication) }
    "an administrator attempts to change a submitted-for-checking sandbox application" should { behave like allowedSubscriptionChange(adminSubmittedSandboxApplication) }
    "an administrator attempts to change a created sandbox application" should { behave like allowedSubscriptionChange(adminCreatedSandboxApplication) }
    "a developer attempts to change a submitted-for-checking production application" should { behave like forbiddenSubscriptionChange(developerSubmittedProductionApplication) }
    "a developer attempts to change a created production application" should { behave like allowedSubscriptionChangeWithCheckUpdate(developerCreatedProductionApplication) }
    "a developer attempts to change a submitted-for-checking sandbox application" should { behave like allowedSubscriptionChange(developerSubmittedSandboxApplication) }
    "a developer attempts to change a created sandbox application" should { behave like allowedSubscriptionChange(devloperCreatedSandboxApplication) }
  }

  "changeLockedApiSubscription" when {
    def checkBadLockedSubscriptionChangeRequest(app: => Application, expectedStatus: Int): Unit = {
      s"return $expectedStatus" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(
          "GET",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId)

        fetchByApplicationIdReturns(appId, app)

        givenApplicationAction(ApplicationWithSubscriptionData(app, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
        givenAppIsSubscribedToApi(appId, apiIdentifier)

        val result = underTest.changeLockedApiSubscription(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe expectedStatus

        verify(applicationServiceMock, never).isSubscribedToApi(eqTo(app.id), eqTo(apiIdentifier))(*)
      }
    }

    def forbiddenLockedSubscriptionChangeRequest(app: => Application): Unit = {
      checkBadLockedSubscriptionChangeRequest(app, FORBIDDEN)
    }

    def badLockedSubscriptionChangeRequest(app: => Application): Unit = {
      checkBadLockedSubscriptionChangeRequest(app, BAD_REQUEST)
    }

    def allowedLockedSubscriptionChangeRequest(app: => Application): Unit = {
      "render the subscribe to locked subscription page when changing an unsubscribed api" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(
          "GET",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId)

        fetchByApplicationIdReturns(appId, app)

        givenApplicationAction(ApplicationWithSubscriptionData(app, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
        givenAppIsNotSubscribedToApi(appId, apiIdentifier)

        val result = underTest.changeLockedApiSubscription(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe OK
        contentAsString(result) should include(s"Are you sure you want to request to subscribe to $apiName ${apiVersion.value}?")

        verify(applicationServiceMock).isSubscribedToApi(eqTo(app.id), eqTo(apiIdentifier))(*)
      }

      "render the unsubscribe from locked subscription page when changing a subscribed api" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(
          "GET",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId)

        fetchByApplicationIdReturns(appId, app)

        givenApplicationAction(ApplicationWithSubscriptionData(app, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
        givenAppIsSubscribedToApi(appId, apiIdentifier)

        val result = underTest.changeLockedApiSubscription(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe OK
        contentAsString(result) should include(s"Are you sure you want to request to unsubscribe from $apiName ${apiVersion.value}?")

        verify(applicationServiceMock).isSubscribedToApi(eqTo(app.id), eqTo(apiIdentifier))(*)
      }
    }

    "an administrator attempts to change a submitted-for-checking production application" should {
      behave like allowedLockedSubscriptionChangeRequest(adminSubmittedProductionApplication)
    }
    "an administrator attempts to change a created production application" should { behave like badLockedSubscriptionChangeRequest(adminCreatedProductionApplication) }
    "an administrator attempts to change a submitted-for-checking sandbox application" should { behave like badLockedSubscriptionChangeRequest(adminSubmittedSandboxApplication) }
    "an administrator attempts to change a created sandbox application" should { behave like badLockedSubscriptionChangeRequest(adminCreatedSandboxApplication) }
    "a developer attempts to change a submitted-for-checking production application" should {
      behave like forbiddenLockedSubscriptionChangeRequest(developerSubmittedProductionApplication)
    }
    "a developer attempts to change a created production application" should { behave like badLockedSubscriptionChangeRequest(developerCreatedProductionApplication) }
    "a developer attempts to change a submitted-for-checking sandbox application" should { behave like badLockedSubscriptionChangeRequest(developerSubmittedSandboxApplication) }
    "a developer attempts to change a created sandbox application" should { behave like badLockedSubscriptionChangeRequest(devloperCreatedSandboxApplication) }
  }

  "changeLockedApiSubscriptionAction" when {
    def forbiddenLockedSubscriptionChangeRequest(app: => Application): Unit = {
      "return 403 Forbidden" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("confirm" -> "true")

        fetchByApplicationIdReturns(appId, app)

        givenApplicationAction(ApplicationWithSubscriptionData(app, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
        givenAppIsSubscribedToApi(appId, apiIdentifier)

        val result = underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe FORBIDDEN

        verify(applicationServiceMock, never).isSubscribedToApi(eqTo(app.id), eqTo(apiIdentifier))(*)
      }
    }

    def badLockedSubscriptionChangeRequest(app: => Application): Unit = {
      "return 400 Bad Request" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("confirm" -> "true")

        fetchByApplicationIdReturns(appId, app)

        givenApplicationAction(ApplicationWithSubscriptionData(app, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
        givenAppIsSubscribedToApi(appId, apiIdentifier)

        val result = underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe BAD_REQUEST

        verify(applicationServiceMock, never).isSubscribedToApi(eqTo(app.id), eqTo(apiIdentifier))(*)
      }
    }

    def allowedLockedSubscriptionChangeRequest(app: => Application): Unit = {
      "successfully request to subscribe to the api" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("confirm" -> "true")

        fetchByApplicationIdReturns(appId, app)

        givenApplicationAction(ApplicationWithSubscriptionData(app, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
        givenAppIsNotSubscribedToApi(appId, apiIdentifier)
        when(underTest.subscriptionsService.requestApiSubscription(eqTo(loggedInDeveloper), eqTo(app), eqTo(apiName), eqTo(apiVersion))(*))
          .thenReturn(successful(mock[TicketResult]))
        when(underTest.subscriptionsService.requestApiUnsubscribe(eqTo(loggedInDeveloper), eqTo(app), eqTo(apiName), eqTo(apiVersion))(*))
          .thenReturn(successful(mock[TicketResult]))

        val result = underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe OK

        contentAsString(result) should include(s"success-request-subscribe-text")

        verify(applicationServiceMock).isSubscribedToApi(eqTo(app.id), eqTo(apiIdentifier))(*)
        verify(underTest.subscriptionsService).requestApiSubscription(eqTo(loggedInDeveloper), eqTo(app), eqTo(apiName), eqTo(apiVersion))(*)
        verify(underTest.subscriptionsService, never).requestApiUnsubscribe(eqTo(loggedInDeveloper), eqTo(app), eqTo(apiName), eqTo(apiVersion))(*)

      }

      "successfully request to unsubscribe from the api" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("confirm" -> "true")

        fetchByApplicationIdReturns(appId, app)

        givenApplicationAction(ApplicationWithSubscriptionData(app, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
        givenAppIsSubscribedToApi(appId, apiIdentifier)
        when(underTest.subscriptionsService.requestApiSubscription(eqTo(loggedInDeveloper), eqTo(app), eqTo(apiName), eqTo(apiVersion))(*))
          .thenReturn(successful(mock[TicketResult]))
        when(underTest.subscriptionsService.requestApiUnsubscribe(eqTo(loggedInDeveloper), eqTo(app), eqTo(apiName), eqTo(apiVersion))(*))
          .thenReturn(successful(mock[TicketResult]))

        val result = underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe OK

        contentAsString(result) should include(s"success-request-unsubscribe-text")

        verify(applicationServiceMock).isSubscribedToApi(eqTo(app.id), eqTo(apiIdentifier))(*)
        verify(underTest.subscriptionsService, never).requestApiSubscription(eqTo(loggedInDeveloper), eqTo(app), eqTo(apiName), eqTo(apiVersion))(*)
        verify(underTest.subscriptionsService).requestApiUnsubscribe(eqTo(loggedInDeveloper), eqTo(app), eqTo(apiName), eqTo(apiVersion))(*)
      }

      "return a Bad Request without requesting a change to the subscription when the form is invalid" in new Setup {
        val redirectTo = "APPLICATION_CHECK_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody()

        fetchByApplicationIdReturns(appId, app)

        givenApplicationAction(ApplicationWithSubscriptionData(app, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)
        givenAppIsSubscribedToApi(appId, apiIdentifier)
        when(underTest.subscriptionsService.requestApiSubscription(eqTo(loggedInDeveloper), eqTo(app), eqTo(apiName), eqTo(apiVersion))(*))
          .thenReturn(successful(mock[TicketResult]))
        when(underTest.subscriptionsService.requestApiUnsubscribe(eqTo(loggedInDeveloper), eqTo(app), eqTo(apiName), eqTo(apiVersion))(*))
          .thenReturn(successful(mock[TicketResult]))

        val result = underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe BAD_REQUEST

        verify(applicationServiceMock).isSubscribedToApi(eqTo(app.id), eqTo(apiIdentifier))(*)
        verify(underTest.subscriptionsService, never).requestApiSubscription(eqTo(loggedInDeveloper), eqTo(app), eqTo(apiName), eqTo(apiVersion))(*)
        verify(underTest.subscriptionsService, never).requestApiUnsubscribe(eqTo(loggedInDeveloper), eqTo(app), eqTo(apiName), eqTo(apiVersion))(*)
      }
    }

    "an administrator attempts to change a submitted-for-checking production application" should {
      behave like allowedLockedSubscriptionChangeRequest(adminSubmittedProductionApplication)
    }
    "an administrator attempts to change a created production application" should { behave like badLockedSubscriptionChangeRequest(adminCreatedProductionApplication) }
    "an administrator attempts to change a submitted-for-checking sandbox application" should { behave like badLockedSubscriptionChangeRequest(adminSubmittedSandboxApplication) }
    "an administrator attempts to change a created sandbox application" should { behave like badLockedSubscriptionChangeRequest(adminCreatedSandboxApplication) }
    "a developer attempts to change a submitted-for-checking production application" should {
      behave like forbiddenLockedSubscriptionChangeRequest(developerSubmittedProductionApplication)
    }
    "a developer attempts to change a created production application" should { behave like badLockedSubscriptionChangeRequest(developerCreatedProductionApplication) }
    "a developer attempts to change a submitted-for-checking sandbox application" should { behave like badLockedSubscriptionChangeRequest(developerSubmittedSandboxApplication) }
    "a developer attempts to change a created sandbox application" should { behave like badLockedSubscriptionChangeRequest(devloperCreatedSandboxApplication) }
  }

  "Authorization" should {
    val apiContext = ApiContext("api/test")
    val apiVersion = ApiVersion("1.0")
    val apiAccessType = "PUBLIC"

    "unauthorized user should get 404 Not Found on unsubscribe to an API" in new Setup {
      val alteredActiveApplication = activeApplication.copy(collaborators = Set("randomEmail".asAdministratorCollaborator))

      when(underTest.sessionService.fetch(eqTo(sessionId))(*)).thenReturn(successful(Some(session)))
      fetchByApplicationIdReturns(appId, alteredActiveApplication)
      givenApplicationAction(ApplicationWithSubscriptionData(alteredActiveApplication, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)

      val request: FakeRequest[AnyContentAsEmpty.type] =
        FakeRequest("GET", s"developer/applications/$appId/subscribe?context=${apiContext.value}&version=${apiVersion.value}&accessType=$apiAccessType&tab=subscriptions")
          .withLoggedIn(underTest, implicitly)(sessionId)

      val result = underTest.changeApiSubscription(appId, apiContext, apiVersion, apiAccessType)(request)
      status(result) shouldBe NOT_FOUND
      verify(applicationServiceMock, never).updateCheckInformation(eqTo(alteredActiveApplication), eqTo(CheckInformation()))(*)
    }
  }

  private def titleOf(result: Future[Result]) = {
    val titleRegEx = """<title[^>]*>(.*)</title>""".r
    val title = titleRegEx.findFirstMatchIn(contentAsString(result)).map(_.group(1))
    title.isDefined shouldBe true
    title.get
  }

  private def aClientSecret() = ClientSecret(randomUUID.toString, randomUUID.toString, DateTimeUtils.now.withZone(DateTimeZone.getDefault))

}
