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

package config

import akka.stream.Materializer
import javax.inject.Inject
import org.joda.time.DateTime
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.filters.frontend.{SessionTimeoutFilter, SessionTimeoutFilterConfig}

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class WhitelistedCall(uri: String, method: String)

class SessionTimeoutFilterWithWhitelist @Inject()(config: SessionTimeoutFilterConfig)(implicit ec: ExecutionContext, override val mat: Materializer)
  extends SessionTimeoutFilter(config) {

  val loginUrl = "/developer/login" //controllers.routes.UserLoginAccount.login().url
  val whitelistedCalls: Set[WhitelistedCall] = Set(WhitelistedCall(loginUrl, "GET"), WhitelistedCall(loginUrl, "POST"))

  override def apply(f: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {
    println("in SessionTimeoutFilterWithWhitelist.apply - Checking whitelist")
    if (whitelistedCalls.contains(WhitelistedCall(rh.path, rh.method))) {
      println(s"in SessionTimeoutFilterWithWhitelist if whitelisted")
      println(s"whitelistedCalls.contains(WhitelistedCall(rh.path, rh.method)) is: ${whitelistedCalls.contains(WhitelistedCall(rh.path, rh.method))}")
      println(s"rh.path is: ${rh.path}")
      println(s"rh.method is: ${rh.method}")
      println(s"f(rh) is: ${f}")

      f(rh)
    }
    else {
      println("in SessionTimeoutFilterWithWhitelist.apply - if not whitelisted")
      println(s"rh.path is: ${rh.path}")
      println(s"rh.method is: ${rh.method}")
      println(s"f(rh) is: ${f}")
      println(s"lastRequestTimestamp: ${rh.session.get(uk.gov.hmrc.http.SessionKeys.lastRequestTimestamp)}")
      println(s"CLOCK in super: ${super.clock().getMillis.toString}")
      println(s"CLOCK in this: ${this.clock().getMillis.toString}")

      super.apply(f)(rh)
    }
  }
}
