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

import config.{ApplicationConfig, ErrorHandler}
import javax.inject.Inject
import model.APICategory
import play.api.i18n.MessagesApi
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent}
import service.{EmailPreferencesService, SessionService}
import views.html.emailpreferences._

import scala.concurrent.{ExecutionContext, Future}

class EmailPreferences @Inject()(val emailPreferencesService: EmailPreferencesService,
                                 val sessionService: SessionService,
                                 val messagesApi: MessagesApi,
                                 val errorHandler: ErrorHandler,
                                 val cookieSigner : CookieSigner)
                                (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig) extends LoggedInController {

  def emailPreferencesStartPage: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(emailPreferences()))
  }

  def taxRegimeSelectionPage: Action[AnyContent] = loggedInAction { implicit request =>
    emailPreferencesService.currentUserEmailPreferences(request.developerSession.email).map { userEmailPreferences =>
      Ok(taxRegimeSelection(userEmailPreferences.availableTaxRegimes, userEmailPreferences.selectedTaxRegimes))
    }
  }

  def taxRegimesSelectedAction: Action[AnyContent] = loggedInAction { implicit request =>
    val requestForm: TaxRegimeEmailPreferencesForm = TaxRegimeEmailPreferencesForm.bindFromRequest
    val selectedTaxRegimes: Set[APICategory] = requestForm.selectedTaxRegimes.map(APICategory.withName).toSet

    emailPreferencesService.updateSelectedTaxRegimes(request.developerSession.email, selectedTaxRegimes) map { updatedEmailPreferences =>
      updatedEmailPreferences.selectedTaxRegimes match {
        case selectedTaxRegimes if selectedTaxRegimes.nonEmpty => Redirect(routes.EmailPreferences.serviceSelectionPage())
        case _ => Redirect(routes.EmailPreferences.topicSelectionPage())
      }
    }
  }

  def serviceSelectionPage: Action[AnyContent] = loggedInAction { implicit request =>
    emailPreferencesService.currentUserEmailPreferences(request.developerSession.email).map { userEmailPreferences =>
      Ok(serviceSelection(userEmailPreferences))
    }
  }

  def topicSelectionPage: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(topicSelection()))
  }

  def emailPreferencesCompletePage: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(confirmation()))
  }

}
