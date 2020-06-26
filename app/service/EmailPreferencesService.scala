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

package service

import connectors.{ApiPlatformMicroserviceConnector, ThirdPartyDeveloperConnector}
import domain.Developer
import javax.inject.{Inject, Singleton}
import model.{APICategory, UserEmailPreferences}
import repository.EmailPreferenceSelectionsRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailPreferencesService @Inject()(emailPreferenceSelectionsRepository: EmailPreferenceSelectionsRepository,
                                        thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
                                        apiPlatformMicroserviceConnector: ApiPlatformMicroserviceConnector)
                                       (implicit val ec: ExecutionContext) {

  def currentUserEmailPreferences(userEmail: String): Future[UserEmailPreferences] =
    emailPreferenceSelectionsRepository.fetchByEmail(userEmail)
      .flatMap(localUserEmailPreferences =>
        if (localUserEmailPreferences.isDefined) Future.successful(localUserEmailPreferences.head) else userEmailPreferencesFromSourceServices(userEmail))

  private def userEmailPreferencesFromSourceServices(userEmail: String): Future[UserEmailPreferences] = {
    val servicesAvailableToUser: Future[Map[APICategory, Set[String]]] = apiPlatformMicroserviceConnector.fetchApiDefinitionsForCollaborator(userEmail)
    val userDetails: Future[Option[Developer]] = thirdPartyDeveloperConnector.fetchDeveloper(userEmail)(HeaderCarrier())

    for {
      userServices <- servicesAvailableToUser
      userServicesSelected <- userDetails
      userEmailPreferences = UserEmailPreferences(
        servicesAvailableToUser = userServices,
        servicesSelected = userServicesSelected.get.emailPreferences.interests.map(t => (APICategory.withName(t.regime), t.services)).toMap,
        topicsSelected = userServicesSelected.get.emailPreferences.topics.map(_.toString))
      _ <- emailPreferenceSelectionsRepository.save(userEmail, userEmailPreferences) // Store in local repository for later
    } yield userEmailPreferences
  }

  def updateSelectedTaxRegimes(userEmail: String, selectedTaxRegimes: Set[APICategory]): Future[UserEmailPreferences] =
    for {
      currentEmailPreferences <- currentUserEmailPreferences(userEmail)
      _ <- emailPreferenceSelectionsRepository.removeSelectedTaxRegimes(userEmail, currentEmailPreferences.selectedTaxRegimes.diff(selectedTaxRegimes))
      updatedEmailPreferences <-
        emailPreferenceSelectionsRepository.addSelectedTaxRegimes(userEmail, selectedTaxRegimes.diff(currentEmailPreferences.selectedTaxRegimes))
    } yield updatedEmailPreferences
}

