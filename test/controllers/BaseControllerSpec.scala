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

import akka.stream.Materializer
import config.ApplicationConfig
import mocks.service.ErrorHandlerMock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.i18n.MessagesApi
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.CookieSigner
import play.api.mvc.MessagesControllerComponents
import utils.{AsyncHmrcSpec, SharedMetricsClearDown}
import mocks.service.SessionServiceMock

class BaseControllerSpec 
    extends AsyncHmrcSpec 
    with GuiceOneAppPerSuite 
    with SharedMetricsClearDown 
    with ErrorHandlerMock 
    with SessionServiceMock {

  implicit val appConfig: ApplicationConfig = mock[ApplicationConfig]
  when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
  when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")

  implicit val cookieSigner: CookieSigner = app.injector.instanceOf[CookieSigner]

  implicit lazy val materializer: Materializer = app.materializer

  lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]

  val mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(("metrics.jvm", false))
      .build()
}
