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

package it

import akka.stream.Materializer
import config.SessionTimeoutFilterWithWhitelist
import connectors.{ConnectorMetrics, NoopConnectorMetrics}
import javax.inject.Inject
import org.joda.time.{DateTime, DateTimeUtils, DateTimeZone}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Writeable
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{DefaultSessionCookieBaker, Headers, Request, RequestHeader, Result, Session, SessionCookieBaker}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.crypto._
import uk.gov.hmrc.http.SessionKeys.{token, _}
import uk.gov.hmrc.play.bootstrap.filters.frontend.crypto.{SessionCookieCrypto, SessionCookieCryptoFilter}
import uk.gov.hmrc.play.bootstrap.filters.frontend.{SessionTimeoutFilter, SessionTimeoutFilterConfig}
import uk.gov.hmrc.play.test.UnitSpec
import play.api.test.CSRFTokenHelper._

import scala.concurrent.{ExecutionContext, Future}
import org.scalatest.BeforeAndAfterAll
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.scalatest.BeforeAndAfterEach
import domain.LoggedInState
import domain.Developer
import play.api.mvc.request.{AssignedCell, RequestAttrKey}

object SessionTimeoutFilterWithWhitelistIntegrationSpec {
  val fixedTime = new DateTime(2019, 1, 1, 0, 0)
  DateTimeUtils.setCurrentMillisFixed(fixedTime.getMillis)

  val whitelistedUrl = "/developer/login"
  val notWhitelistedUrl = "/developer/registration"

//  val stubPort = sys.env.getOrElse("WIREMOCK_PORT", "11111").toInt
//  private val stubHost = "localhost"
//  val wireMockUrl = s"http://$stubHost:$stubPort"
//  private val wireMockConfiguration = WireMockConfiguration.wireMockConfig().port(stubPort)

  class StaticDateSessionTimeoutFilterWithWhitelist @Inject()(config: SessionTimeoutFilterConfig)(implicit ec: ExecutionContext, mat: Materializer)
    extends SessionTimeoutFilterWithWhitelist(config)(ec, mat) {
    println(s"In StaticDateSessionTimeoutFilterWithWhitelist - Config : $config")
    println(s"Config.sessionTimeoutSeconds : ${config.timeoutDuration.toStandardSeconds}")

    override def clock(): DateTime = fixedTime

    override def apply(f: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {


      val updateTimestamp: (Result) => Result = {
        println(s"In updateTimestamp - clock().getMillis.toString - ${clock().getMillis.toString}")

        result => {
          val r = result.addingToSession(
            lastRequestTimestamp -> clock().getMillis.toString,
          "Hello" -> "Pete's hand!!")(rh)

          println(s"updated lastRequestTimestamp on result is ${r.session(rh).get(lastRequestTimestamp)}")
          println(s"updated Hello on result is ${r.session(rh).get("Hello")}")
          r
        }
      }

      val wipeAllFromSessionCookie: (Result) => Result = {
        println("In wipeAllFromSessionCookie")
        result => result.withSession(preservedSessionData(result.session(rh)): _*)
      }

      val wipeAuthRelatedKeysFromSessionCookie: (Result) => Result = {
        println("In wipeAuthRelatedKeysFromSessionCookie")
        result => result.withSession(wipeFromSession(result.session(rh), authRelatedKeys))
      }

      val timestamp = rh.session.get(lastRequestTimestamp)

      println(s"timestamp is $timestamp")

      (timestamp.flatMap(timestampToDatetime) match {
        case Some(ts) if hasExpired(ts) && config.onlyWipeAuthToken => {
          println(s"case 1 - hasExpired:${hasExpired(ts)} ")
          f(wipeAuthRelatedKeys(rh))
            .map(wipeAuthRelatedKeysFromSessionCookie)
        }
        case Some(ts) if hasExpired(ts) => {
          println(s"case 2- hasExpired:${hasExpired(ts)}")
          f(wipeSession(rh))
            .map(wipeAllFromSessionCookie)
        }
        case _ => {
          println("case 3")
          f(rh)
        }
      }).map(updateTimestamp)
    }

    private def preservedSessionData(session: Session): Seq[(String, String)] =
      for {
        key   <- (SessionTimeoutFilter.whitelistedSessionKeys ++ config.additionalSessionKeys).toSeq
        value <- session.get(key)
      } yield key -> value

    private def wipeFromSession(session: Session, keys: Seq[String]): Session = keys.foldLeft(session)((s, k) => s - k)

    private def timestampToDatetime(timestamp: String): Option[DateTime] =
      try {
        Some(new DateTime(timestamp.toLong, DateTimeZone.UTC))
      } catch {
        case e: NumberFormatException => None
      }

    private def hasExpired(timestamp: DateTime): Boolean = {
      val timeOfExpiry = timestamp plus config.timeoutDuration
      clock() isAfter timeOfExpiry
    }

    private def wipeSession(requestHeader: RequestHeader): RequestHeader = {
      val sessionMap: Map[String, String] = preservedSessionData(requestHeader.session).toMap
      requestWithUpdatedSession(requestHeader, new Session(sessionMap))
    }

    private def wipeAuthRelatedKeys(requestHeader: RequestHeader): RequestHeader =
      requestWithUpdatedSession(requestHeader, wipeFromSession(requestHeader.session, authRelatedKeys))

    private def requestWithUpdatedSession(requestHeader: RequestHeader, session: Session): RequestHeader =
      requestHeader.addAttr(
        key   = RequestAttrKey.Session,
        value = new AssignedCell(session)
      )

  }

//  val noCrypt = new Encrypter with Decrypter {
//    def encrypt(plain: PlainContent): Crypted = plain match {
//      case PlainText(value) => Crypted(value)
//      case PlainBytes(value) => Crypted(value.mkString)
//    }
//
//    def decrypt(reversiblyEncrypted: Crypted): PlainText = PlainText(reversiblyEncrypted.value)
//
//    def decryptAsBytes(reversiblyEncrypted: Crypted): PlainBytes = PlainBytes(reversiblyEncrypted.value.getBytes)
//  }
//
//  class PlainCookieCryptoFilter @Inject()(implicit override val mat: Materializer,
//                                          override val ec: ExecutionContext) extends SessionCookieCryptoFilter {
//    override protected lazy val encrypter: Encrypter = noCrypt
//    override protected lazy val decrypter: Decrypter = noCrypt
//
//    override protected def sessionBaker: SessionCookieBaker = new DefaultSessionCookieBaker
//
//    println("In PlainCookieCryptoFilter")
//  }
}

class SessionTimeoutFilterWithWhitelistIntegrationSpec extends UnitSpec with GuiceOneAppPerSuite
  with BeforeAndAfterAll with BeforeAndAfterEach with SessionCookieCryptoFilterWrapper {

  import SessionTimeoutFilterWithWhitelistIntegrationSpec._

  val token = "AUTH_TOKEN"
  val sessionTimeoutSeconds = 900
  val email = "thirdpartydeveloper@example.com"
  val config: Configuration = Configuration(
    "session.timeoutSeconds" -> sessionTimeoutSeconds,
    "session.wipeIdleSession" -> false,
    "session.additionalSessionKeysToKeep" -> Seq("access_uri")
  )

  val sessionCookieCrypto: SessionCookieCrypto = app.injector.instanceOf[SessionCookieCrypto]
  val cookieBaker: SessionCookieBaker = app.injector.instanceOf[SessionCookieBaker]

  val postBody = Seq("emailaddress" -> email, "password" -> "password1!")

//  val wireMockServer = new WireMockServer(wireMockConfiguration)

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(config)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics],
        bind[SessionTimeoutFilter].to[StaticDateSessionTimeoutFilterWithWhitelist]
//        bind[SessionCookieCryptoFilter].to[PlainCookieCryptoFilter]
      )
      .in(Mode.Test)
      .build()


  override def beforeAll() = {
    super.beforeAll()
//    wireMockServer.start()
  }

  override def afterAll() = {
//    wireMockServer.stop()
    super.afterAll()
  }

  class Setup[T](implicit request: Request[T], w: Writeable[T]) {
    import play.api.http.HeaderNames.AUTHORIZATION

//    WireMock.configureFor(stubPort)
//    stubs.ApplicationStub.configureUserApplications(email)

    val encryptedCookie = encryptCookie(Map(
      lastRequestTimestamp -> fixedTime.minusSeconds(sessionTimeoutSeconds * 2).getMillis.toString)
    )
    val headers = Headers(AUTHORIZATION -> token
    , COOKIE -> encryptedCookie
    )
    val developer = Developer(email, "bob", "smith", None, Some(false))
    //TODO: May need to add below line back
//    stubs.ThirdPartyDeveloperStub.configureAuthenticate(Some(domain.Session(sessionId, developer, LoggedInState.LOGGED_IN)))

//    val result: Option[Future[Result]] =  await(route(app, addCSRFToken(request.withHeaders(headers))))
//    val outputSession: Session = result.get.session
  }

  "SessionTimeoutFilterWithWhitelist" can {

//    "if the session has not expired" ignore {
//      val fixedPointInThePast = fixedTime.minusSeconds(sessionTimeoutSeconds / 2).getMillis.toString
//      val session = Seq(lastRequestTimestamp -> fixedPointInThePast, authToken -> token)
//
//      "making a GET request to the login page" should {
//        implicit lazy val request = FakeRequest(GET, whitelistedUrl).withSession(session: _*)
//
//        "ignore the session timeout" in new Setup {
//          outputSession.get(lastRequestTimestamp) shouldBe Some(fixedPointInThePast)
//        }
//
//        "preserve the session's auth token" in new Setup {
//          outputSession.get(authToken) shouldBe Some(token)
//        }
//      }
//
//      "making a POST request to the login page" should {
//        implicit lazy val request = FakeRequest(POST, whitelistedUrl).withSession(session: _*).withFormUrlEncodedBody(postBody: _*)
//
//        "ignore the session timeout" in new Setup {
//          outputSession.get(lastRequestTimestamp) shouldBe Some(fixedPointInThePast)
//        }
//
//        "preserve the session's auth token" in new Setup {
//          outputSession.get(authToken) shouldBe Some(token)
//        }
//      }
//
//      "making a request to a url not in the whitelist" should {
//        implicit lazy val request = FakeRequest(GET, notWhitelistedUrl).withSession(session: _*)
//
//        "update the session timeout" in new Setup {
//          outputSession.get(lastRequestTimestamp) shouldBe Some(fixedTime.getMillis.toString)
//        }
//
//        "preserve the session's auth token" in new Setup {
//          outputSession.get(authToken) shouldBe Some(token)
//        }
//      }
//    }

    "if session has expired" when {
      val fixedPointInTheDistantPast = fixedTime.minusSeconds(sessionTimeoutSeconds * 2).getMillis.toString
      val session = Seq(lastRequestTimestamp -> fixedPointInTheDistantPast, authToken -> token)

//      "making a GET request to the login page" should {
//        implicit lazy val request = FakeRequest(GET, whitelistedUrl).withSession(session: _*)
//
//        "ignore the session timeout" in new Setup {
//          outputSession.get(lastRequestTimestamp) shouldBe Some(fixedPointInTheDistantPast)
//        }
//
//        "preserve the session's auth token" in new Setup {
//          outputSession.get(authToken) shouldBe Some(token)
//        }
//      }

//      "making a POST request to the login page" should {
//        implicit lazy val request = FakeRequest(POST, whitelistedUrl).withSession(session: _*).withFormUrlEncodedBody(postBody: _*)
//
//        "ignore the session timeout" in new Setup {
//          outputSession.get(lastRequestTimestamp) shouldBe Some(fixedPointInTheDistantPast)
//        }
//
//        "preserve the session's auth token" in new Setup {
//          outputSession.get(authToken) shouldBe Some(token)
//        }
//      }

      "making a request to a url not in the whitelist" should {

        implicit lazy val request = FakeRequest(GET, notWhitelistedUrl).withSession(session: _*)

        println(s"request headers: ${request.headers}")

        "update the session timeout" in new Setup {

          val result =  await(route(app, addCSRFToken(request.withHeaders(headers))).get)
          println(s"******** result ${result}")

          val outputSession: Session = result.session

          println(s"Pomegranate - lastRequestTimestamp ${outputSession.get(lastRequestTimestamp)}")
          println(s"Pomegranate - fixedPointInTheDistantPast ${fixedPointInTheDistantPast}")

          outputSession.get(lastRequestTimestamp) shouldBe Some(fixedTime.getMillis.toString)
          outputSession.get("Hello") shouldBe Some("Pete's hand!!")


          outputSession.get(lastRequestTimestamp) should not be Some(fixedPointInTheDistantPast)

          outputSession.get(lastRequestTimestamp) shouldBe Some(fixedTime.getMillis.toString)
        }

//        "wipe the session's auth token" in new Setup {
//          outputSession.get(authToken) shouldBe None
//        }
      }
    }
  }
}
