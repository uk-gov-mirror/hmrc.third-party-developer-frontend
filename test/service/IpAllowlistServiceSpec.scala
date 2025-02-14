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

package service

import java.util.UUID.randomUUID

import connectors.ThirdPartyApplicationConnector
import domain.ApplicationUpdateSuccessful
import domain.models.applications.{Application, IpAllowlist}
import domain.models.flows.FlowType.IP_ALLOW_LIST
import domain.models.flows.IpAllowlistFlow
import org.scalatest.Matchers
import repositories.FlowRepository
import repositories.ReactiveMongoFormatters.formatIpAllowlistFlow
import service.PushPullNotificationsService.PushPullNotificationsConnector
import service.SubscriptionFieldsService.SubscriptionFieldsConnector
import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful
import utils._
import builder.DeveloperBuilder

class IpAllowlistServiceSpec
    extends AsyncHmrcSpec 
    with Matchers 
    with TestApplications
    with CollaboratorTracker
    with DeveloperBuilder 
    with LocalUserIdTracker {

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val sessionId: String = randomUUID.toString

    val mockFlowRepository: FlowRepository = mock[FlowRepository]
    val mockThirdPartyApplicationConnector: ThirdPartyApplicationConnector = mock[ThirdPartyApplicationConnector]
    val mockConnectorsWrapper: ConnectorsWrapper = mock[ConnectorsWrapper]
    when(mockConnectorsWrapper.forEnvironment(*))
      .thenReturn(Connectors(mockThirdPartyApplicationConnector, mock[SubscriptionFieldsConnector], mock[PushPullNotificationsConnector]))

    val underTest = new IpAllowlistService(mockFlowRepository, mockConnectorsWrapper)
  }

  "getIpAllowlistFlow" should {
    "return existing flow" in new Setup {
      val expectedFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24"))
      when(mockFlowRepository.fetchBySessionIdAndFlowType[IpAllowlistFlow](sessionId, IP_ALLOW_LIST)).thenReturn(successful(Some(expectedFlow)))
      when(mockFlowRepository.saveFlow(expectedFlow)).thenReturn(successful(expectedFlow))

      val result: IpAllowlistFlow = await(underTest.getIpAllowlistFlow(anApplication(), sessionId))

      result shouldBe expectedFlow
    }

    "create new flow if it does not exist" in new Setup {
      val ipAllowlist = Set("1.1.1.1/24")
      val expectedFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, ipAllowlist)
      when(mockFlowRepository.fetchBySessionIdAndFlowType[IpAllowlistFlow](sessionId, IP_ALLOW_LIST)).thenReturn(successful(None))
      when(mockFlowRepository.saveFlow(expectedFlow)).thenReturn(successful(expectedFlow))

      val result: IpAllowlistFlow = await(underTest.getIpAllowlistFlow(anApplication(ipAllowlist = IpAllowlist(allowlist = ipAllowlist)), sessionId))

      result shouldBe expectedFlow
      verify(mockFlowRepository).saveFlow(expectedFlow)
    }
  }

  "discardIpAllowlistFlow" should {
    "delete the flow in the repository" in new Setup {
      when(mockFlowRepository.deleteBySessionIdAndFlowType(sessionId, IP_ALLOW_LIST)).thenReturn(successful(true))

      val result: Boolean = await(underTest.discardIpAllowlistFlow(sessionId))

      result shouldBe true
      verify(mockFlowRepository).deleteBySessionIdAndFlowType(sessionId, IP_ALLOW_LIST)
    }
  }

  "addCidrBlock" should {
    val newCidrBlock = "2.2.2.1/32"

    "add the cidr block to the existing flow" in new Setup {
      val existingFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24"))
      val expectedFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24", newCidrBlock))
      when(mockFlowRepository.fetchBySessionIdAndFlowType[IpAllowlistFlow](sessionId, IP_ALLOW_LIST)).thenReturn(successful(Some(existingFlow)))
      when(mockFlowRepository.saveFlow(expectedFlow)).thenReturn(successful(expectedFlow))

      val result: IpAllowlistFlow = await(underTest.addCidrBlock(newCidrBlock, anApplication(), sessionId))

      result shouldBe expectedFlow
    }

    "add the cidr block to a new flow if it does not exist yet" in new Setup {
      val expectedFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set(newCidrBlock))
      when(mockFlowRepository.fetchBySessionIdAndFlowType[IpAllowlistFlow](sessionId, IP_ALLOW_LIST)).thenReturn(successful(None))
      when(mockFlowRepository.saveFlow(expectedFlow)).thenReturn(successful(expectedFlow))

      val result: IpAllowlistFlow = await(underTest.addCidrBlock(newCidrBlock, anApplication(), sessionId))

      result shouldBe expectedFlow
    }
  }

  "removeCidrBlock" should {
    val cidrBlockToRemove = "2.2.2.1/32"

    "remove cidr block from the existing flow" in new Setup {
      val existingFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24", cidrBlockToRemove))
      val expectedFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24"))
      when(mockFlowRepository.fetchBySessionIdAndFlowType[IpAllowlistFlow](sessionId, IP_ALLOW_LIST)).thenReturn(successful(Some(existingFlow)))
      when(mockFlowRepository.saveFlow(expectedFlow)).thenReturn(successful(expectedFlow))

      val result: IpAllowlistFlow = await(underTest.removeCidrBlock(cidrBlockToRemove, sessionId))

      result shouldBe expectedFlow
    }

    "fail when no flow exists for the given session ID" in new Setup {
      when(mockFlowRepository.fetchBySessionIdAndFlowType[IpAllowlistFlow](sessionId, IP_ALLOW_LIST)).thenReturn(successful(None))

      val expectedException: IllegalStateException = intercept[IllegalStateException] {
        await(underTest.removeCidrBlock(cidrBlockToRemove, sessionId))
      }

      expectedException.getMessage shouldBe s"No IP allowlist flow exists for session ID $sessionId"
    }
  }

  "activateIpAllowlist" should {
    "save the allowlist in TPA" in new Setup {
      val app: Application = anApplication()
      val existingFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24"))
      when(mockFlowRepository.fetchBySessionIdAndFlowType[IpAllowlistFlow](sessionId, IP_ALLOW_LIST)).thenReturn(successful(Some(existingFlow)))
      when(mockThirdPartyApplicationConnector.updateIpAllowlist(app.id, app.ipAllowlist.required, existingFlow.allowlist))
        .thenReturn(successful(ApplicationUpdateSuccessful))
      when(mockFlowRepository.deleteBySessionIdAndFlowType(sessionId, IP_ALLOW_LIST)).thenReturn(successful(true))

      val result: ApplicationUpdateSuccessful = await(underTest.activateIpAllowlist(app, sessionId))

      result shouldBe ApplicationUpdateSuccessful
      verify(mockThirdPartyApplicationConnector).updateIpAllowlist(app.id, app.ipAllowlist.required, existingFlow.allowlist)
      verify(mockFlowRepository).deleteBySessionIdAndFlowType(sessionId, IP_ALLOW_LIST)
    }

    "fail when activating an empty allowlist" in new Setup {
      val existingFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set())
      when(mockFlowRepository.fetchBySessionIdAndFlowType[IpAllowlistFlow](sessionId, IP_ALLOW_LIST)).thenReturn(successful(Some(existingFlow)))

      val expectedException: ForbiddenException = intercept[ForbiddenException] {
        await(underTest.activateIpAllowlist(anApplication(), sessionId))
      }

      expectedException.getMessage shouldBe s"IP allowlist for session ID $sessionId cannot be activated because it is empty"
      verifyZeroInteractions(mockThirdPartyApplicationConnector)
    }

    "fail when no flow exists for the given session ID" in new Setup {
      when(mockFlowRepository.fetchBySessionIdAndFlowType[IpAllowlistFlow](sessionId, IP_ALLOW_LIST)).thenReturn(successful(None))

      val expectedException: IllegalStateException = intercept[IllegalStateException] {
        await(underTest.activateIpAllowlist(anApplication(), sessionId))
      }

      expectedException.getMessage shouldBe s"No IP allowlist flow exists for session ID $sessionId"
      verifyZeroInteractions(mockThirdPartyApplicationConnector)
    }
  }

  "deactivateIpAllowlist" should {
    "update the allowlist in TPA with an empty set" in new Setup {
      val app: Application = anApplication()
      when(mockThirdPartyApplicationConnector.updateIpAllowlist(app.id, app.ipAllowlist.required, Set.empty))
        .thenReturn(successful(ApplicationUpdateSuccessful))
      when(mockFlowRepository.deleteBySessionIdAndFlowType(sessionId, IP_ALLOW_LIST)).thenReturn(successful(true))

      val result: ApplicationUpdateSuccessful = await(underTest.deactivateIpAllowlist(app, sessionId))

      result shouldBe ApplicationUpdateSuccessful
      verify(mockThirdPartyApplicationConnector).updateIpAllowlist(app.id, app.ipAllowlist.required, Set.empty)
      verify(mockFlowRepository).deleteBySessionIdAndFlowType(sessionId, IP_ALLOW_LIST)
    }

    "fail when the IP allowlist is required" in new Setup {
      val app: Application = anApplication(ipAllowlist = IpAllowlist(required = true))

      val expectedException: ForbiddenException = intercept[ForbiddenException] {
        await(underTest.deactivateIpAllowlist(app, sessionId))
      }

      expectedException.getMessage shouldBe s"IP allowlist for session ID $sessionId cannot be deactivated because it is required"
      verifyZeroInteractions(mockThirdPartyApplicationConnector)
    }
  }
}
