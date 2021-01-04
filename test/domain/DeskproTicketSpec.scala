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

package domain

import controllers.SupportEnquiryForm
import domain.models.connectors.DeskproTicket
import play.api.test.FakeRequest
import utils.AsyncHmrcSpec

class DeskproTicketSpec extends AsyncHmrcSpec {

  implicit val fakeRequest = FakeRequest()

  "A DeskproTicket created from a support enquiry" should {
    "have the email address of the party submitting the enquiry" in {

      val form = SupportEnquiryForm("A Developer", "developer@example.com", "My comments")

      val ticket = DeskproTicket.createFromSupportEnquiry(form, "Developer Hub")

      ticket.email shouldBe "developer@example.com"
    }
  }
}
