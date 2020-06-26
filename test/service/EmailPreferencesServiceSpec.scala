package service

import connectors.{ApiPlatformMicroserviceConnector, ThirdPartyDeveloperConnector}
import domain.{Developer, EmailPreferences, EmailTopic, TaxRegimeInterests}
import model.{APICategory, UserEmailPreferences}
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verifyZeroInteractions
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import repository.EmailPreferenceSelectionsRepository
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EmailPreferencesServiceSpec extends UnitSpec with ScalaFutures with MockitoSugar with Matchers {

  trait Setup {
    val mockEmailPreferenceSelectionsRepository: EmailPreferenceSelectionsRepository = mock[EmailPreferenceSelectionsRepository]
    val mockThirdPartyDeveloperConnector: ThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]
    val mockApiPlatformMicroserviceConnector: ApiPlatformMicroserviceConnector = mock[ApiPlatformMicroserviceConnector]

    val serviceUnderTest: EmailPreferencesService =
      new EmailPreferencesService(mockEmailPreferenceSelectionsRepository, mockThirdPartyDeveloperConnector, mockApiPlatformMicroserviceConnector)
  }

  "currentUserEmailPreferences" should {
    "return local version from repository if it exists" in new Setup {
      val userEmail = "foo@bar.com"
      val localEmailPreferencesRecord: UserEmailPreferences = emailPreferences()

      given(mockEmailPreferenceSelectionsRepository.fetchByEmail(userEmail)).willReturn(Future.successful(Some(localEmailPreferencesRecord)))

      val returnedEmailPreferences: UserEmailPreferences = await(serviceUnderTest.currentUserEmailPreferences(userEmail))

      returnedEmailPreferences shouldBe theSameInstanceAs (localEmailPreferencesRecord)

      verifyZeroInteractions(mockThirdPartyDeveloperConnector, mockApiPlatformMicroserviceConnector)
    }

    "retrieve details from upstream services if local version does not exist" in new Setup {
      val userEmail = "foo@bar.com"
      val servicesAvailable: Map[APICategory, Set[String]] = Map(APICategory.CUSTOMS -> Set("cds-api-1"))

      given(mockEmailPreferenceSelectionsRepository.fetchByEmail(userEmail)).willReturn(Future.successful(None))
      given(mockApiPlatformMicroserviceConnector.fetchApiDefinitionsForCollaborator(userEmail)).willReturn(Future.successful(servicesAvailable))
      given(mockThirdPartyDeveloperConnector.fetchDeveloper(meq(userEmail))(any())).willReturn(Future.successful(Some(developer(userEmail, servicesAvailable, Set("TECHNICAL")))))
      given(mockEmailPreferenceSelectionsRepository.save(meq(userEmail), any())).willReturn(Future.successful(true))

      val returnedEmailPreferences: UserEmailPreferences = await(serviceUnderTest.currentUserEmailPreferences(userEmail))

      returnedEmailPreferences.servicesAvailableToUser shouldBe Map(APICategory.CUSTOMS -> Set("cds-api-1"))
      returnedEmailPreferences.servicesSelected shouldBe Map(APICategory.CUSTOMS -> Set("cds-api-1"))
      returnedEmailPreferences.topicsSelected should contain only "TECHNICAL"
    }
  }

  "updateSelectedTaxRegimes" should {
    "update local record with tax regimes that the user has selected" in new Setup {

    }
  }

  private def emailPreferences(servicesAvailableToUser: Map[APICategory, Set[String]] = Map.empty,
                               servicesSelected: Map[APICategory, Set[String]] = Map.empty,
                               topicsSelected: Set[String] = Set.empty): UserEmailPreferences =
    UserEmailPreferences(servicesAvailableToUser, servicesSelected, topicsSelected)

  private def developer(userEmail: String, servicesSelected: Map[APICategory, Set[String]], topicsSelected: Set[String] = Set.empty): Developer = {
    val interests = servicesSelected.map(service => TaxRegimeInterests(service._1.toString, service._2)).toList
    val topics = topicsSelected.map(EmailTopic.withName)

    Developer(userEmail, "Foo", "Bar", None, None, EmailPreferences(interests, topics))
  }
}
