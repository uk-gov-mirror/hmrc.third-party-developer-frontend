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

package stubs

import java.net.URLEncoder

import com.github.tomakehurst.wiremock.client.WireMock._
import connectors.EncryptedJson
import domain.models.applications.ApplicationNameValidationJson.ApplicationNameValidationResult
import domain.models.applications.{Application, ApplicationToken, Environment}
import domain.models.developers.{Registration, UpdateProfileRequest}
import domain.services.ApiDefinitionsJsonFormatters._
import domain.services.SubscriptionsJsonFormatters._
import org.scalatest.Matchers
import play.api.Logger
import play.api.libs.json.{Json, Writes}
import play.api.http.Status._
import domain.models.apidefinitions.ApiIdentifier
import domain.models.apidefinitions.{ApiContext, ApiVersion}
import domain.models.applications.ClientId
import domain.models.applications.ApplicationId

object Stubs {

  def setupRequest(path: String, status: Int, response: String) = {
    Logger.info(s"Stubbing $path with $response")
    stubFor(
      get(urlEqualTo(path))
        .willReturn(aResponse().withStatus(status).withBody(response).withHeader("Content-type", "application/json"))
    )
  }

  def setupDeleteRequest(path: String, status: Int) =
    stubFor(delete(urlEqualTo(path)).willReturn(aResponse().withStatus(status)))

  def setupPostRequest(path: String, status: Int) =
    stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(status)))

  def setupPostRequest(path: String, status: Int, response: String) =
    stubFor(
      post(urlEqualTo(path))
        .willReturn(aResponse().withStatus(status).withBody(response))
    )

  def setupPostContaining(path: String, data: String, status: Int): Unit =
    stubFor(
      post(urlPathEqualTo(path))
        .withRequestBody(containing(data))
        .willReturn(aResponse().withStatus(status))
    )

  def setupEncryptedPostRequest[T](path: String, data: T, status: Int, response: String)(
      implicit writes: Writes[T],
      encryptedJson: EncryptedJson
  ) =
    stubFor(
      post(urlPathEqualTo(path))
        .withRequestBody(equalToJson(encryptedJson.toSecretRequestJson(data).toString()))
        .willReturn(aResponse().withStatus(status).withBody(response))
    )
}

object DeveloperStub {

  def register(registration: Registration, status: Int)(implicit encryptedJson: EncryptedJson) =
    stubFor(
      post(urlMatching(s"/developer"))
        .withRequestBody(equalToJson(encryptedJson.toSecretRequestJson(registration).toString()))
        .willReturn(aResponse().withStatus(status))
    )

  def update(email: String, profile: UpdateProfileRequest, status: Int) =
    stubFor(
      post(urlMatching(s"/developer/$email"))
        .withRequestBody(equalToJson(Json.toJson(profile).toString()))
        .willReturn(aResponse().withStatus(status))
    )

  def setupResend(email: String, status: Int) = {
    stubFor(
      post(urlPathEqualTo(s"/$email/resend-verification"))
        .willReturn(aResponse().withStatus(status))
    )
  }

  def verifyResetPassword(email: String) = {
    verify(1, postRequestedFor(urlPathEqualTo(s"/$email/password-reset-request")))
  }
}

object ApplicationStub {
  def setupApplicationNameValidation() = {
    val validNameResult = ApplicationNameValidationResult(None)

    Stubs.setupPostRequest("/application/name/validate", OK, Json.toJson(validNameResult).toString)
  }

  def setUpFetchApplication(id: ApplicationId, status: Int, response: String = "") = {
    stubFor(
      get(urlEqualTo(s"/application/${id.value}"))
        .willReturn(aResponse().withStatus(status).withBody(response))
    )
  }

  def setUpFetchEmptySubscriptions(id: ApplicationId, status: Int) = {
    stubFor(
      get(urlEqualTo(s"/application/${id.value}/subscription"))
        .willReturn(aResponse().withStatus(status).withBody("[]"))
    )
  }

  def setUpDeleteSubscription(id: ApplicationId, api: String, version: ApiVersion, status: Int) = {
    stubFor(
      delete(urlEqualTo(s"/application/${id.value}/subscription?context=$api&version=${version.value}"))
        .willReturn(aResponse().withStatus(status))
    )
  }

  def setUpExecuteSubscription(id: ApplicationId, api: String, version: ApiVersion, status: Int) = {
    stubFor(
      post(urlEqualTo(s"/application/${id.value}/subscription"))
        .withRequestBody(equalToJson(Json.toJson(ApiIdentifier(ApiContext(api), version)).toString()))
        .willReturn(aResponse().withStatus(status))
    )
  }

  def setUpUpdateApproval(id: ApplicationId) = {
    stubFor(
      post(urlEqualTo(s"/application/${id.value}/check-information"))
        .willReturn(aResponse().withStatus(OK))
    )
  }

  def configureUserApplications(email: String, applications: List[Application] = Nil, status: Int = OK) = {
    val encodedEmail = URLEncoder.encode(email, "UTF-8")

    def stubResponse(environment: Environment, applications: List[Application]) = {
      stubFor(
        get(urlPathEqualTo("/developer/applications"))
          .withQueryParam("emailAddress", equalTo(encodedEmail))
          .withQueryParam("environment", equalTo(environment.toString))
          .willReturn(
            aResponse()
              .withStatus(status)
              .withBody(Json.toJson(applications).toString())
          )
      )
    }

    val (prodApps, sandboxApps) = applications.partition(_.deployedTo == Environment.PRODUCTION)

    stubResponse(Environment.PRODUCTION, prodApps)
    stubResponse(Environment.SANDBOX, sandboxApps)
  }

  def configureApplicationCredentials(tokens: Map[String, ApplicationToken], status: Int = OK) = {
    tokens.foreach { entry =>
      stubFor(
        get(urlEqualTo(s"/application/${entry._1}/credentials"))
          .willReturn(
            aResponse()
              .withStatus(status)
              .withBody(Json.toJson(entry._2).toString())
              .withHeader("content-type", "application/json")
          )
      )
    }
  }
}

object DeskproStub extends Matchers {
  val deskproPath: String = "/deskpro/ticket"
  val deskproFeedbackPath: String = "/deskpro/feedback"

  def setupTicketCreation(status: Int = OK) = {
    Stubs.setupPostRequest(deskproPath, status)
  }

  def verifyTicketCreationWithSubject(subject: String) = {
    verify(1, postRequestedFor(urlPathEqualTo(deskproPath)).withRequestBody(containing(s""""subject":"$subject"""")))
  }
}

object AuditStub extends Matchers {
  val auditPath: String = "/write/audit"
  val mergedAuditPath: String = "/write/audit/merged"

  def setupAudit(status: Int = NO_CONTENT, data: Option[String] = None) = {
    if (data.isDefined) {
      Stubs.setupPostContaining(auditPath, data.get, status)
      Stubs.setupPostContaining(mergedAuditPath, data.get, status)
    } else {
      Stubs.setupPostRequest(auditPath, status)
      Stubs.setupPostRequest(mergedAuditPath, status)
    }
  }
}

object ApiSubscriptionFieldsStub {

  def setUpDeleteSubscriptionFields(clientId: ClientId, apiContext: ApiContext, apiVersion: ApiVersion) = {
    stubFor(
      delete(urlEqualTo(fieldValuesUrl(clientId, apiContext, apiVersion)))
        .willReturn(aResponse().withStatus(NO_CONTENT))
    )
  }

  private def fieldValuesUrl(clientId: ClientId, apiContext: ApiContext, apiVersion: ApiVersion) = {
    s"/field/application/${clientId.value}/context/${apiContext.value}/version/${apiVersion.value}"
  }

  def noSubscriptionFields(apiContext: ApiContext, version: ApiVersion): Any = {
    stubFor(get(urlEqualTo(fieldDefinitionsUrl(apiContext, version))).willReturn(aResponse().withStatus(NOT_FOUND)))
  }

  private def fieldDefinitionsUrl(apiContext: ApiContext, version: ApiVersion) = {
    s"/definition/context/${apiContext.value}/version/${version.value}"
  }
}
