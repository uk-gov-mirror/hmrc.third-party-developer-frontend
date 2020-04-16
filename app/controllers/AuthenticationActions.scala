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

package controllers

import play.api.mvc
import play.api.mvc.{ActionBuilder, ActionTransformer, Request, Result, WrappedRequest}

import scala.concurrent.Future

case class UserRequest[A](username: Option[String], request: Request[A]) extends WrappedRequest[A](request)

object UserAction extends
  ActionBuilder[UserRequest] with ActionTransformer[Request, UserRequest] {
  def transform[A](request: Request[A]) = Future.successful {
    UserRequest(Some("Bob"), request)
  }
}

//object LoggingAction extends ActionBuilder[Request] {
//  override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[mvc.Result]): Future[Result] = {
//
////    val userRequest = UserRequest(Some("Bob"), request)
//
//    block(request)
//  }
//}
