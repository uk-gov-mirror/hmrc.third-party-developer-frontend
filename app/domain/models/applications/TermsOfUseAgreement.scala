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

package domain.models.applications

import org.joda.time.DateTime

case class TermsOfUseAgreement(emailAddress: String, timeStamp: DateTime, version: String)

object TermsOfUseAgreement {

  import play.api.libs.json.Json
  import play.api.libs.json.JodaReads.DefaultJodaDateTimeReads
  import play.api.libs.json.JodaWrites.JodaDateTimeNumberWrites

  implicit val numberWrites = JodaDateTimeNumberWrites

  implicit val format = Json.format[TermsOfUseAgreement]
}
