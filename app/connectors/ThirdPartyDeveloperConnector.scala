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

import config.ApplicationConfig
import connectors.ThirdPartyDeveloperConnector.UnregisteredUserCreationRequest
import domain._
import domain.models.connectors._
import domain.models.developers._
import javax.inject.{Inject, Singleton}
import play.api.http.HeaderNames.CONTENT_LENGTH
import play.api.http.Status._
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.http.{UserId => _, _}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.metrics.API
import scala.concurrent.{ExecutionContext, Future}
import domain.models.emailpreferences.EmailPreferences
import connectors.ThirdPartyDeveloperConnector.RemoveMfaRequest

import uk.gov.hmrc.http.HttpReads.Implicits._


object ThirdPartyDeveloperConnector {
  private[connectors] case class UnregisteredUserCreationRequest(email: String)

  case class RemoveMfaRequest(removedBy: String)
  case class CreateMfaResponse(secret: String)
  case class EmailForResetResponse(email: String)

  case class FindUserIdRequest(email: String)
  case class FindUserIdResponse(userId: UserId)

  case class CoreUserDetails(email: String, id: UserId)
  
  object JsonFormatters {
    implicit val formatUnregisteredUserCreationRequest: Format[UnregisteredUserCreationRequest] = Json.format[UnregisteredUserCreationRequest]
    implicit val FindUserIdRequestWrites = Json.writes[FindUserIdRequest]
    implicit val FindUserIdResponseReads = Json.reads[FindUserIdResponse]
  }
}

@Singleton
class ThirdPartyDeveloperConnector @Inject()(http: HttpClient, encryptedJson: EncryptedJson, config: ApplicationConfig, metrics: ConnectorMetrics
                                            )(implicit val ec: ExecutionContext) extends CommonResponseHandlers {

  import ThirdPartyDeveloperConnector._
  import ThirdPartyDeveloperConnector.JsonFormatters._

  def authenticate(loginRequest: LoginRequest)(implicit hc: HeaderCarrier): Future[UserAuthenticationResponse] = metrics.record(api) {
    encryptedJson.secretRequest(
      loginRequest,
      http.POST[SecretRequest, ErrorOr[UserAuthenticationResponse]](s"$serviceBaseUrl/authenticate", _))
      .map {
        case Right(response) => response
        case Left(UpstreamErrorResponse(_, UNAUTHORIZED, _, _)) => throw new InvalidCredentials
        case Left(UpstreamErrorResponse(_, FORBIDDEN, _, _)) => throw new UnverifiedAccount
        case Left(UpstreamErrorResponse(_, LOCKED, _, _)) => throw new LockedAccount
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _)) => throw new InvalidEmail
        case Left(err) => throw err
      }
  }

  def authenticateTotp(totpAuthenticationRequest: TotpAuthenticationRequest)(implicit hc: HeaderCarrier): Future[Session] = metrics.record(api) {
    encryptedJson.secretRequest(
      totpAuthenticationRequest,
      http.POST[SecretRequest, ErrorOr[Session]](s"$serviceBaseUrl/authenticate-totp", _))
      .map {
        case Right(response) => response
        case Left(UpstreamErrorResponse(_, BAD_REQUEST, _, _)) => throw new InvalidCredentials
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _)) => throw new InvalidEmail
        case Left(err) => throw err
      }
  }

  lazy val serviceBaseUrl: String = config.thirdPartyDeveloperUrl
  val api = API("third-party-developer")

  def register(registration: Registration)(implicit hc: HeaderCarrier): Future[RegistrationDownstreamResponse] = metrics.record(api) {
    encryptedJson.secretRequest(
      registration,
      http.POST[SecretRequest,ErrorOr[HttpResponse]](s"$serviceBaseUrl/developer", _)
      .map {
        case Right(response) if(response.status == CREATED) => RegistrationSuccessful
        case Right(response) => throw new InternalServerException("Unexpected 2xx code")
        case Left(UpstreamErrorResponse(_, CONFLICT, _, _)) => EmailAlreadyInUse
        case Left(err) => throw err
      }
    )
  }

  def createUnregisteredUser(email: String)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    encryptedJson.secretRequest(
      UnregisteredUserCreationRequest(email),
      http.POST[SecretRequest, ErrorOr[HttpResponse]](s"$serviceBaseUrl/unregistered-developer", _)
      .map {
        case Right(response) => response.status
        case Left(err) => throw err
      }
    )
  }

  def reset(reset: PasswordReset)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    encryptedJson.secretRequest(
      reset,
      http.POST[SecretRequest, ErrorOr[HttpResponse]](s"$serviceBaseUrl/reset-password", _)
      .map {
        case Right(response) => response.status
        case Left(UpstreamErrorResponse(_, FORBIDDEN, _, _)) => throw new UnverifiedAccount
        case Left(err) => throw err
      }
    )
  }

  def changePassword(change: ChangePassword)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    encryptedJson.secretRequest(
      change,
      http.POST[SecretRequest, ErrorOr[HttpResponse]](s"$serviceBaseUrl/change-password", _)
      .map {
        case Right(response) => response.status
        case Left(UpstreamErrorResponse(_, UNAUTHORIZED, _, _)) => throw new InvalidCredentials
        case Left(UpstreamErrorResponse(_, FORBIDDEN, _, _)) => throw new UnverifiedAccount
        case Left(UpstreamErrorResponse(_, LOCKED, _, _)) => throw new LockedAccount
        case Left(err) => throw err
      }
    )
  }

  def requestReset(email: String)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.POST[PasswordResetRequest, Either[UpstreamErrorResponse, HttpResponse]](s"$serviceBaseUrl/password-reset-request", PasswordResetRequest(email), Seq((CONTENT_LENGTH -> "0")))
    .map {
      case Right(response) => response.status
      case Left(UpstreamErrorResponse(_,FORBIDDEN,_,_)) => throw new UnverifiedAccount
      case Left(err) => throw err
    }
  }

  def updateSessionLoggedInState(sessionId: String, request: UpdateLoggedInStateRequest)(implicit hc: HeaderCarrier): Future[Session] = metrics.record(api) {
    http.PUT[String, Option[Session]](s"$serviceBaseUrl/session/$sessionId/loggedInState/${request.loggedInState}", "")
      .map {
        case Some(session) => session
        case None => throw new SessionInvalid
      }
  }

  def fetchEmailForResetCode(code: String)(implicit hc: HeaderCarrier): Future[String] = {
    implicit val EmailForResetResponseReads = Json.reads[EmailForResetResponse]
    
    metrics.record(api) {
      http.GET[ErrorOr[EmailForResetResponse]](s"$serviceBaseUrl/reset-password?code=$code")
      .map {
        case Right(e) => e.email
        case Left(UpstreamErrorResponse(_,BAD_REQUEST,_,_)) => throw new InvalidResetCode
        case Left(UpstreamErrorResponse(_,FORBIDDEN,_,_)) => throw new UnverifiedAccount
        case Left(err) =>throw err
      }
    }
  }

  def updateProfile(userId: UserId, profile: UpdateProfileRequest)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.POST[UpdateProfileRequest, ErrorOr[HttpResponse]](s"$serviceBaseUrl/developer/${userId.value}", profile)
      .map {
        case Right(response) => response.status
        case Left(err) => throw err
      }
  }

  def findUserId(email: String)(implicit hc: HeaderCarrier): Future[Option[CoreUserDetails]] = {
    http.POST[FindUserIdRequest, Option[FindUserIdResponse]](s"$serviceBaseUrl/developers/find-user-id", FindUserIdRequest(email))
    .map {
      case Some(response) => Some(CoreUserDetails(email, response.userId))
      case None => None
    }
  }

  def fetchUserId(email: String)(implicit hc: HeaderCarrier): Future[CoreUserDetails] = {
    http.POST[FindUserIdRequest, FindUserIdResponse](s"$serviceBaseUrl/developers/find-user-id", FindUserIdRequest(email))
    .map(response => CoreUserDetails(email, response.userId))
  }

  def resendVerificationEmail(email: String)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    for {
      coreUserDetails <- fetchUserId(email)
      userId = coreUserDetails.id.value
      response <- http.POSTEmpty[ErrorOr[HttpResponse]](s"$serviceBaseUrl/$userId/resend-verification", Seq(CONTENT_LENGTH -> "0"))
        .map {
          case Right(response) => response.status
          case Left(err) => throw err
        }
    } yield response
  }

  def verify(code: String)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.GET[ErrorOr[HttpResponse]](s"$serviceBaseUrl/verification", Seq("code" -> code))
      .map {
        case Right(response) => response.status
        case Left(err) => throw err
      }
  }

  def fetchSession(sessionId: String)(implicit hc: HeaderCarrier): Future[Session] = metrics.record(api) {
    http.GET[Option[Session]](s"$serviceBaseUrl/session/$sessionId")
    .map {
      case Some(session) => session
      case None => throw new SessionInvalid
    }
  }

  def deleteSession(sessionId: String)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.DELETE[ErrorOr[HttpResponse]](s"$serviceBaseUrl/session/$sessionId")
      .map {
        case Right(response) => response.status
        // treat session not found as successfully destroyed
        case Left(UpstreamErrorResponse(_,NOT_FOUND,_,_)) => NO_CONTENT
        case Left(err) => throw err
      }
  }

  def updateRoles(userId: UserId, roles: AccountSetupRequest)(implicit hc: HeaderCarrier): Future[Developer] =
    metrics.record(api) {
      http.PUT[AccountSetupRequest,Developer](s"$serviceBaseUrl/developer/account-setup/${userId.value}/roles", roles)
    }


  def updateServices(userId: UserId, services: AccountSetupRequest)(implicit hc: HeaderCarrier): Future[Developer] =
    metrics.record(api) {
      http.PUT[AccountSetupRequest,Developer](s"$serviceBaseUrl/developer/account-setup/${userId.value}/services", services)
    }

  def updateTargets(userId: UserId, targets: AccountSetupRequest)(implicit hc: HeaderCarrier): Future[Developer] =
    metrics.record(api) {
      http.PUT[AccountSetupRequest,Developer](s"$serviceBaseUrl/developer/account-setup/${userId.value}/targets", targets)
    }

  def completeAccountSetup(userId: UserId)(implicit hc: HeaderCarrier): Future[Developer] =
    metrics.record(api) {
      http.POSTEmpty[Developer](s"$serviceBaseUrl/developer/account-setup/${userId.value}/complete", Seq((CONTENT_LENGTH -> "0")))
    }

  def fetchDeveloper(id: UserId)(implicit hc: HeaderCarrier): Future[Option[Developer]] = {
    metrics.record(api) {
      http.GET[Option[Developer]](s"$serviceBaseUrl/developer", Seq("developerId" -> id.asText))
    }
  }

  def fetchByEmails(emails: Set[String])(implicit hc: HeaderCarrier): Future[Seq[User]] = {
    http.POST[Set[String], Seq[User]](s"$serviceBaseUrl/developers/get-by-emails", emails)
  }

  def createMfaSecret(userId: UserId)(implicit hc: HeaderCarrier): Future[String] = {
    implicit val CreateMfaResponseReads = Json.reads[CreateMfaResponse]

    metrics.record(api) {
      http.POSTEmpty[CreateMfaResponse](s"$serviceBaseUrl/developer/${userId.value}/mfa", Seq((CONTENT_LENGTH -> "0")))
      .map(_.secret)
    }
  }

  def verifyMfa(userId: UserId, code: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    metrics.record(api) {
      http.POST[VerifyMfaRequest, ErrorOrUnit](s"$serviceBaseUrl/developer/${userId.value}/mfa/verification", VerifyMfaRequest(code))
      .map {
        case Right(()) => true
        case Left(UpstreamErrorResponse(_,BAD_REQUEST,_,_)) => false
        case Left(err) => throw err
      }
    }
  }

  def enableMfa(userId: UserId)(implicit hc: HeaderCarrier): Future[Unit] = {
    metrics.record(api) {
      http.PUT[String, ErrorOrUnit](s"$serviceBaseUrl/developer/${userId.value}/mfa/enable", "")
      .map(throwOrUnit)
    }
  }

  def removeMfa(userId: UserId, email: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    implicit val RemoveMfaRequestFormat = Json.format[RemoveMfaRequest]
    metrics.record(api) {
      http.POST[RemoveMfaRequest, ErrorOrUnit](s"$serviceBaseUrl/developer/${userId.value}/mfa/remove", RemoveMfaRequest(email))
      .map(throwOrUnit)
    }
  }

  def removeEmailPreferences(userId: UserId)(implicit hc: HeaderCarrier): Future[Boolean] = metrics.record(api) {
      http.DELETE[ErrorOrUnit](s"$serviceBaseUrl/developer/${userId.value}/email-preferences")
      .map(throwOrOptionOf)
      .map {
        case Some(_) => true
        case None => throw new InvalidEmail
      }
  }

  def updateEmailPreferences(userId: UserId, emailPreferences: EmailPreferences)
      (implicit hc: HeaderCarrier): Future[Boolean] = metrics.record(api) {
    val url = s"$serviceBaseUrl/developer/${userId.value}/email-preferences"

    http.PUT[EmailPreferences, ErrorOrUnit](url, emailPreferences)
      .map {
        case Right(_) => true
        case Left(UpstreamErrorResponse(_,NOT_FOUND,_,_)) => throw new InvalidEmail
        case Left(err) => throw err
      }
  }
}

