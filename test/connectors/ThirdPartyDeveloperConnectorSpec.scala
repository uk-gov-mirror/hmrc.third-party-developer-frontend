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

package connectors

import builder.DeveloperBuilder
import config.ApplicationConfig
import connectors.ThirdPartyDeveloperConnector.JsonFormatters._
import connectors.ThirdPartyDeveloperConnector.UnregisteredUserCreationRequest
import domain.models.connectors._
import domain.models.developers._
import domain.models.emailpreferences.EmailTopic._
import domain.models.emailpreferences.{EmailPreferences, TaxRegimeInterests}
import domain.{InvalidCredentials, InvalidEmail, LockedAccount, UnverifiedAccount}
import play.api.http.Status._
import play.api.http.Status
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.metrics.API
import utils.AsyncHmrcSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful
import connectors.ThirdPartyDeveloperConnector.CreateMfaResponse
import connectors.ThirdPartyDeveloperConnector.FindUserIdRequest
import connectors.ThirdPartyDeveloperConnector.FindUserIdResponse
import utils.LocalUserIdTracker

class ThirdPartyDeveloperConnectorSpec 
    extends AsyncHmrcSpec 
    with CommonResponseHandlers 
    with DeveloperBuilder 
    with LocalUserIdTracker { 

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockHttp: HttpClient = mock[HttpClient]
    val mockPayloadEncryption: PayloadEncryption = mock[PayloadEncryption]
    val encryptedJson = new EncryptedJson(mockPayloadEncryption)
    val mockAppConfig: ApplicationConfig = mock[ApplicationConfig]
    val mockMetrics = new NoopConnectorMetrics()
    val encryptedString: JsString = JsString("someEncryptedStringOfData")
    val encryptedBody = SecretRequest(encryptedString.as[String])

    when(mockAppConfig.thirdPartyDeveloperUrl).thenReturn("http://THIRD_PARTY_DEVELOPER:9000")
    when(mockPayloadEncryption.encrypt(*)(*)).thenReturn(encryptedString)

    val connector = new ThirdPartyDeveloperConnector(mockHttp, encryptedJson, mockAppConfig, mockMetrics)

    def endpoint(path: String) = s"${connector.serviceBaseUrl}/$path"
    val userId = UserId.random
  }

  "api" should {
    "be deskpro" in new Setup {
      connector.api shouldEqual API("third-party-developer")
    }
  }

  "register" should {
    "successfully register a developer" in new Setup {
      val registrationToTest = Registration("john", "smith", "john.smith@example.com", "XXXYYYY")

      when(mockHttp.POST[SecretRequest,ErrorOr[HttpResponse]](eqTo(endpoint("developer")), eqTo(encryptedBody), *)(*, *, *, *))
        .thenReturn(successful(Right(HttpResponse(Status.CREATED,""))))

      await(connector.register(registrationToTest)) shouldBe RegistrationSuccessful

      verify(mockPayloadEncryption).encrypt(eqTo(Json.toJson(registrationToTest)))(*)
    }

    "fail to register a developer when the email address is already in use" in new Setup {
      val registrationToTest = Registration("john", "smith", "john.smith@example.com", "XXXYYYY")

      when(mockHttp.POST[SecretRequest,ErrorOr[HttpResponse]](eqTo(endpoint("developer")), eqTo(encryptedBody), *)(*, *, *, *))
        .thenReturn(successful(Left(UpstreamErrorResponse("409 exception", Status.CONFLICT, Status.CONFLICT))))

      await(connector.register(registrationToTest)) shouldBe EmailAlreadyInUse

      verify(mockPayloadEncryption).encrypt(eqTo(Json.toJson(registrationToTest)))(*)
    }

    "successfully verify a developer" in new Setup {
      val code = "A1234"

      when(mockHttp.GET[ErrorOr[HttpResponse]](eqTo(endpoint(s"verification")), eqTo(Seq("code" -> code)))(*,*,*))
      .thenReturn(successful(Right(HttpResponse(Status.OK,""))))

      await(connector.verify(code)) shouldBe Status.OK

      verify(mockHttp).GET[ErrorOr[HttpResponse]](eqTo(endpoint(s"verification")), eqTo(Seq("code" -> code)))(*,*,*)
    }
  }

  "createUnregisteredUser" should {
    val email = "john.smith@example.com"

    "successfully create an unregistered user" in new Setup {
      when(mockHttp.POST[SecretRequest, ErrorOr[HttpResponse]](eqTo(endpoint("unregistered-developer")), eqTo(encryptedBody), *)(*, *, *, *))
        .thenReturn(successful(Right(HttpResponse(Status.OK,""))))

      val result = await(connector.createUnregisteredUser(email))

      result shouldBe Status.OK
      verify(mockPayloadEncryption).encrypt(eqTo(Json.toJson(UnregisteredUserCreationRequest(email))))(*)
    }

    "propagate error when the request fails" in new Setup {
      when(mockHttp.POST[SecretRequest, ErrorOr[HttpResponse]](eqTo(endpoint("unregistered-developer")), eqTo(encryptedBody), *)(*, *, *, *))
        .thenReturn(successful(Left(UpstreamErrorResponse("Internal server error", Status.INTERNAL_SERVER_ERROR, Status.INTERNAL_SERVER_ERROR))))

      intercept[UpstreamErrorResponse] {
        await(connector.createUnregisteredUser(email))
      }
    }
  }

  "fetchSession" should {
      val sessionId = "sessionId"

      "return session" in new Setup {
      val session = Session(sessionId, buildDeveloper(), LoggedInState.LOGGED_IN)

      when(mockHttp.GET[Option[Session]](eqTo(endpoint(s"session/$sessionId")))(*,*,*))
        .thenReturn(successful(Some(session)))

      private val fetchedSession = await(connector.fetchSession(sessionId))
      fetchedSession shouldBe session
    }

    "error with SessionInvalid if we get a 404 response" in new Setup {
      when(mockHttp.GET[Option[Session]](eqTo(endpoint(s"session/$sessionId")))(*,*,*))
      .thenReturn(successful(None))

      intercept[SessionInvalid]{
        await(connector.fetchSession(sessionId))
      }
    }
  }

  "deleteSession" should {
    val sessionId = "sessionId"

    "delete the session" in new Setup {
      when(mockHttp.DELETE[ErrorOr[HttpResponse]](eqTo(endpoint(s"session/$sessionId")),*)(*,*,*))
      .thenReturn(successful(Right(HttpResponse(Status.NO_CONTENT,""))))

      await(connector.deleteSession(sessionId)) shouldBe Status.NO_CONTENT
    }

    "be successful when not found" in new Setup {
      when(mockHttp.DELETE[ErrorOr[HttpResponse]](eqTo(endpoint(s"session/$sessionId")),*)(*,*,*))
      .thenReturn(successful(Left(UpstreamErrorResponse("",NOT_FOUND))))

      await(connector.deleteSession(sessionId)) shouldBe Status.NO_CONTENT
    }
  }

  "updateSessionLoggedInState" should {
    val sessionId = "sessionId"

    "update session logged in state" in new Setup {
      val updateLoggedInStateRequest = UpdateLoggedInStateRequest(LoggedInState.LOGGED_IN)
      val session = Session(sessionId, buildDeveloper(), LoggedInState.LOGGED_IN)

      when(mockHttp.PUT[String, Option[Session]](eqTo(endpoint(s"session/$sessionId/loggedInState/LOGGED_IN")), eqTo(""), *)(*,*,*,*))
        .thenReturn(successful(Some(session)))

      private val updatedSession = await(connector.updateSessionLoggedInState(sessionId, updateLoggedInStateRequest))
      updatedSession shouldBe session
    }

    "error with SessionInvalid if we get a 404 response" in new Setup {
      val updateLoggedInStateRequest = UpdateLoggedInStateRequest(LoggedInState.LOGGED_IN)

      when(mockHttp.PUT[String, Option[Session]](eqTo(endpoint(s"session/$sessionId/loggedInState/LOGGED_IN")), eqTo(""), *)(*,*,*,*))
        .thenReturn(successful(None))

      intercept[SessionInvalid]{
        await(connector.updateSessionLoggedInState(sessionId, updateLoggedInStateRequest))
      }
    }
  }

  "Update profile" should {
    "update profile" in new Setup {
      val updated = UpdateProfileRequest("First", "Last")

      when(mockHttp.POST[UpdateProfileRequest, ErrorOr[HttpResponse]](eqTo(endpoint(s"developer/${userId.value}")), eqTo(updated), *)(*,*,*,*))
      .thenReturn(successful(Right(HttpResponse(Status.OK,""))))

      await(connector.updateProfile(userId, updated)) shouldBe Status.OK
    }
  }

  "Resend verification" should {
    "send verification mail" in new Setup {
      val email = "john.smith@example.com"

      when(mockHttp.POST[FindUserIdRequest, FindUserIdResponse](eqTo(endpoint("developers/find-user-id")), *, *)(*, *, *, *)).thenReturn(successful(FindUserIdResponse(userId)))
      when(mockHttp.POSTEmpty[ErrorOr[HttpResponse]](eqTo(endpoint(s"${userId.value}/resend-verification")), *)(*, *, *)).thenReturn(successful(Right(HttpResponse(Status.OK,""))))

      await(connector.resendVerificationEmail(email)) shouldBe Status.OK

      verify(mockHttp).POSTEmpty[ErrorOr[HttpResponse]](eqTo(endpoint(s"${userId.value}/resend-verification")), *)(*, *, *)
    }
  }

  "Reset password" should {
    "successfully request reset" in new Setup {
      val email = "user@example.com"
      when(mockHttp.POST[PasswordResetRequest, Either[UpstreamErrorResponse, HttpResponse]](eqTo(endpoint("password-reset-request")), *, *)(*, *, *, *)).thenReturn(successful(Right(HttpResponse(Status.OK,""))))

      await(connector.requestReset(email))

      verify(mockHttp).POST[PasswordResetRequest, Either[UpstreamErrorResponse, HttpResponse]](eqTo(endpoint("password-reset-request")), *, *)(*, *, *, *)
    }

    "forbidden response results in UnverifiedAccount exception for request reset" in new Setup {
      val email = "user@example.com"
      when(mockHttp.POST[PasswordResetRequest, Either[UpstreamErrorResponse, HttpResponse]](eqTo(endpoint("password-reset-request")), *, *)(*, *, *, *)).thenReturn(successful(Left(UpstreamErrorResponse("Forbidden", Status.FORBIDDEN))))

      intercept[UnverifiedAccount] {
        await(connector.requestReset(email))
      }
    }

    "successfully validate reset code" in new Setup {
      val email = "user@example.com"
      val code = "ABC123"
      import ThirdPartyDeveloperConnector.EmailForResetResponse

      when(mockHttp.GET[ErrorOr[EmailForResetResponse]](eqTo(endpoint(s"reset-password?code=$code")))(*,*,*))
      .thenReturn(successful(Right(EmailForResetResponse(email))))

      await(connector.fetchEmailForResetCode(code)) shouldBe email

      verify(mockHttp).GET[ErrorOr[EmailForResetResponse]](eqTo(endpoint(s"reset-password?code=$code")))(*,*,*)
    }

    "successfully reset password" in new Setup {
      val passwordReset = PasswordReset("user@example.com", "newPassword")

      when(mockHttp.POST[SecretRequest, ErrorOr[HttpResponse]](eqTo(endpoint("reset-password")), eqTo(encryptedBody), *)(*, *, *, *))
        .thenReturn(successful(Right(HttpResponse(Status.OK,""))))

      await(connector.reset(passwordReset))

      verify(mockPayloadEncryption).encrypt(eqTo(Json.toJson(passwordReset)))(*)
    }
  }

  // TODO - remove this to integration testing
  "accountSetupQuestions" should {
    "successfully complete a developer account setup" in new Setup {
      val developer = buildDeveloper()
  
      when(mockHttp.POSTEmpty[Developer](eqTo(endpoint(s"developer/account-setup/${developer.userId.value}/complete")), *)(*, *, *))
      .thenReturn(successful(developer))

      await(connector.completeAccountSetup(developer.userId)) shouldBe developer
    }

    "successfully update roles" in new Setup {
      val developer = buildDeveloper()

      private val request = AccountSetupRequest(roles = Some(List("aRole")), rolesOther = Some("otherRole"))
      when(mockHttp.PUT[AccountSetupRequest,Developer](eqTo(endpoint(s"developer/account-setup/${developer.userId.value}/roles")), eqTo(request),*)(*,*,*,*)).thenReturn(successful(developer))

      await(connector.updateRoles(developer.userId, request)) shouldBe developer
    }

    "successfully update services" in new Setup {
      val developer = buildDeveloper()

      private val request = AccountSetupRequest(services = Some(List("aService")), servicesOther = Some("otherService"))
      when(mockHttp.PUT[AccountSetupRequest,Developer](eqTo(endpoint(s"developer/account-setup/${developer.userId.value}/services")), eqTo(request),*)(*,*,*,*)).thenReturn(successful(developer))

      await(connector.updateServices(developer.userId, request)) shouldBe developer
    }

    "successfully update targets" in new Setup {
      val developer = buildDeveloper()
      
      private val request = AccountSetupRequest(targets = Some(List("aTarget")), targetsOther = Some("otherTargets"))
      when(mockHttp.PUT[AccountSetupRequest,Developer](eqTo(endpoint(s"developer/account-setup/${developer.userId.value}/targets")), eqTo(request),*)(*,*,*,*)).thenReturn(successful(developer))

      await(connector.updateTargets(developer.userId, request)) shouldBe developer
    }
  }

  "change password" should {
    val changePasswordRequest = ChangePassword("email@example.com", "oldPassword123", "newPassword321")

    "throw Invalid Credentials if the response is Unauthorised" in new Setup {
      when(mockHttp.POST[SecretRequest, ErrorOr[HttpResponse]](eqTo(endpoint("change-password")), eqTo(encryptedBody), *)(*, *, *, *))
        .thenReturn(successful(Left(UpstreamErrorResponse("Unauthorised error", Status.UNAUTHORIZED, Status.UNAUTHORIZED))))

      await(connector.changePassword(changePasswordRequest).failed) shouldBe a[InvalidCredentials]

      verify(mockPayloadEncryption).encrypt(eqTo(Json.toJson(changePasswordRequest)))(*)
    }

    "throw Unverified Account if the response is Forbidden" in new Setup {
      when(mockHttp.POST[SecretRequest, ErrorOr[HttpResponse]](eqTo(endpoint("change-password")), eqTo(encryptedBody), *)(*, *, *, *))
        .thenReturn(successful(Left(UpstreamErrorResponse("Forbidden error", Status.FORBIDDEN, Status.FORBIDDEN))))

      await(connector.changePassword(changePasswordRequest).failed) shouldBe a[UnverifiedAccount]

      verify(mockPayloadEncryption).encrypt(eqTo(Json.toJson(changePasswordRequest)))(*)
    }

    "throw Locked Account if the response is Locked" in new Setup {
      when(mockHttp.POST[SecretRequest, ErrorOr[HttpResponse]](eqTo(endpoint("change-password")), eqTo(encryptedBody), *)(*, *, *, *))
        .thenReturn(successful(Left(UpstreamErrorResponse("Locked error", Status.LOCKED, Status.LOCKED))))

      await(connector.changePassword(changePasswordRequest).failed) shouldBe a[LockedAccount]

      verify(mockPayloadEncryption).encrypt(eqTo(Json.toJson(changePasswordRequest)))(*)
    }
  }

  "create MFA" should {

    "return the created secret" in new Setup {
      val expectedSecret = "ABCDEF"

      when(mockHttp.POSTEmpty[CreateMfaResponse](eqTo(endpoint(s"developer/${userId.value}/mfa")), *)(*, *, *)).thenReturn(successful(CreateMfaResponse(expectedSecret)))

      await(connector.createMfaSecret(userId)) shouldBe expectedSecret

      verify(mockHttp).POSTEmpty[HttpResponse](eqTo(endpoint(s"developer/${userId.value}/mfa")), *)(*, *, *)
    }
  }

  "verify MFA" should {
    val code = "12341234"
    val verifyMfaRequest = VerifyMfaRequest(code)

    "return false if verification fails due to InvalidCode" in new Setup {
      when(mockHttp.POST[VerifyMfaRequest, ErrorOrUnit](eqTo(endpoint(s"developer/${userId.value}/mfa/verification")), eqTo(verifyMfaRequest), *)(*,*,*,*))
        .thenReturn(successful(Left(UpstreamErrorResponse("",BAD_REQUEST))))

      await(connector.verifyMfa(userId, code)) shouldBe false
    }

    "return true if verification is successful" in new Setup {
      when(mockHttp.POST[VerifyMfaRequest, ErrorOrUnit](eqTo(endpoint(s"developer/${userId.value}/mfa/verification")), eqTo(verifyMfaRequest), *)(*,*,*,*))
        .thenReturn(successful(Right(())))

      await(connector.verifyMfa(userId, code)) shouldBe true
    }

    "throw if verification fails due to error" in new Setup {
      when(mockHttp.POST[VerifyMfaRequest, ErrorOrUnit](eqTo(endpoint(s"developer/${userId.value}/mfa/verification")), eqTo(verifyMfaRequest), *)(*,*,*,*))
        .thenReturn(successful(Left(UpstreamErrorResponse("Internal server error", Status.INTERNAL_SERVER_ERROR))))

      intercept[UpstreamErrorResponse] {
        await(connector.verifyMfa(userId, code))
      }
    }
  }

  "enableMFA" should {
    "return no_content if successfully enabled" in new Setup {
      when(mockHttp.PUT[String, ErrorOrUnit](eqTo(endpoint(s"developer/${userId.value}/mfa/enable")), eqTo(""), *)(*,*,*,*)).thenReturn(successful(Right(())))

      await(connector.enableMfa(userId))
    }
  }

  "removeEmailPreferences" should {
    "return true when connector receives NO-CONTENT in response from TPD" in new Setup {
      when(mockHttp.DELETE[ErrorOrUnit](eqTo(endpoint(s"developer/${userId.value}/email-preferences")), *)(*,*,*)).thenReturn(successful(Right(())))
      
      await(connector.removeEmailPreferences(userId))
    }

    "throw InvalidEmail exception if email address not found in TPD" in new Setup {
      when(mockHttp.DELETE[ErrorOrUnit](eqTo(endpoint(s"developer/${userId.value}/email-preferences")), *)(*,*,*)).thenReturn(successful(Left(UpstreamErrorResponse("",NOT_FOUND))))

      intercept[InvalidEmail] {
        await(connector.removeEmailPreferences(userId))
      }
    }

    "throw UpstreamErrorResponse exception for other issues with TPD" in new Setup {
      when(mockHttp.DELETE[ErrorOrUnit](eqTo(endpoint(s"developer/${userId.value}/email-preferences")), *)(*,*,*)).thenReturn(successful(Left(UpstreamErrorResponse("",INTERNAL_SERVER_ERROR))))

      intercept[UpstreamErrorResponse] {
        await(connector.removeEmailPreferences(userId))
      }
    }
  }

  "updateEmailPreferences" should {
    val emailPreferences = EmailPreferences(List(TaxRegimeInterests("VAT", Set("API1", "API2"))), Set(BUSINESS_AND_POLICY))

    "return true when connector receives NO-CONTENT in response from TPD" in new Setup {
      when(mockHttp.PUT[EmailPreferences, ErrorOrUnit](eqTo(endpoint(s"developer/${userId.value}/email-preferences")), eqTo(emailPreferences), *)(*, *, *, *))
        .thenReturn(successful(Right(())))
      private val result = await(connector.updateEmailPreferences(userId, emailPreferences))

      result shouldBe true
    }

    "throw InvalidEmail exception if email address not found in TPD" in new Setup {
      when(mockHttp.PUT[EmailPreferences, ErrorOrUnit](eqTo(endpoint(s"developer/${userId.value}/email-preferences")), eqTo(emailPreferences), *)(*, *, *, *))
        .thenReturn(successful(Left(UpstreamErrorResponse("",NOT_FOUND))))

      intercept[InvalidEmail] {
        await(connector.updateEmailPreferences(userId, emailPreferences))
      }
    }
  }
}
