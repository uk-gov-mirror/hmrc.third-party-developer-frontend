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

import domain.{Developer, DeveloperSession, LoggedInState, Session}
import play.api.mvc._

import scala.concurrent.Future

trait DevHubAuthWrapper extends Results {

  // TODO: Name :(
  implicit def loggedIn2(implicit req : UserRequest[_]) : DeveloperSession = {
    req.developerSession
  }

  def loggedInAction2(body: UserRequest[_] => Future[Result]): Action[AnyContent] = Action.async {
    implicit request: Request[AnyContent] =>

      val developer = Developer("my-email","bob", "bobson")
      val session = Session("my-session-id", developer, LoggedInState.LOGGED_IN)
      val developerSession = DeveloperSession(session)

      body(UserRequest(developerSession, request))

//      val login = controllers.routes.UserLoginAccount.login()
//      Future.successful(Redirect(login))
  }
}

case class UserRequest[A](developerSession: DeveloperSession, request: Request[A]) extends WrappedRequest[A](request)

//object UserAction extends ActionBuilder[UserRequest] with ActionTransformer[Request, UserRequest] {
//  private def decodeCookie(token : String) : Option[String] = {
//    val (hmac, value) = token.splitAt(40)
//
//    val signedValue = Crypto.sign(value)
//
//    if (MessageDigest.isEqual(signedValue.getBytes, hmac.getBytes)) {
//      Some(value)
//    } else {
//      None
//    }
//  }
//
//  def transform[A](request: Request[A]) = Future.successful {
//    request.headers.get("test") match {
//      case Some(value) => UserRequest(decodeCookie(value), request)
//      case None => UserRequest(None, request)
//    }
//  }
//}

//object LoggingAction extends ActionBuilder[Request] {
//  override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[mvc.Result]): Future[Result] = {
//
////    val userRequest = UserRequest(Some("Bob"), request)
//
//    block(request)
//  }
//}
