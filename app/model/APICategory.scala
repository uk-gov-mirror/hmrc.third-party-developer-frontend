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

package model

import enumeratum.values.{StringEnum, StringEnumEntry, StringPlayJsonValueEnum}

import scala.collection.immutable

/* API-4407: Is there a better way to do this - the StringEnumEntry approach was lifted from api-documentation-frontend in order to use the 'displayName'
 * element in DevHub. However, when serialising/deserialising (both from Mongo and over the wire) we want to use the capitalised name of the enum. Should
 * be able to remove the duplication of including it as the 'value' of the StringEnumEntry. */
sealed abstract class APICategory(val value: String, val displayName: String) extends StringEnumEntry

object APICategory extends StringEnum[APICategory] with StringPlayJsonValueEnum[APICategory] {
  //TODO MAYBE THIS WANTS THE FILTER / HEAD REFACTORING
  def withName(regime: String) = values.filter(_.value.equalsIgnoreCase(regime)).head

  val values: immutable.IndexedSeq[APICategory] = findValues

  case object EXAMPLE extends APICategory("EXAMPLE", "Example")
  case object AGENTS extends APICategory("AGENTS", "Agents")
  case object BUSINESS_RATES extends APICategory("BUSINESS_RATES", "Business Rates")
  case object CHARITIES extends APICategory("CHARITIES", "Charities")
  case object CONSTRUCTION_INDUSTRY_SCHEME extends APICategory("CONSTRUCTION_INDUSTRY_SCHEME", "Construction Industry Scheme")
  case object CORPORATION_TAX extends APICategory("CORPORATION_TAX", "Corporation Tax")
  case object CUSTOMS extends APICategory("CUSTOMS", "Customs")
  case object ESTATES extends APICategory("ESTATES", "Estates")
  case object HELP_TO_SAVE extends APICategory("HELP_TO_SAVE", "Help to Save")
  case object INCOME_TAX_MTD extends APICategory("INCOME_TAX_MTD", "Income Tax (Making Tax Digital)")
  case object LIFETIME_ISA extends APICategory("LIFETIME_ISA", "Lifetime ISA")
  case object MARRIAGE_ALLOWANCE extends  APICategory("MARRIAGE_ALLOWANCE", "Marriage Allowance")
  case object NATIONAL_INSURANCE extends APICategory("NATIONAL_INSURANCE", "National Insurance")
  case object PAYE extends APICategory("PAYE", "PAYE")
  case object PENSIONS extends APICategory("PENSIONS", "Pensions")
  case object PRIVATE_GOVERNMENT extends APICategory("PRIVATE_GOVERNMENT", "Private Government")
  case object RELIEF_AT_SOURCE extends APICategory("RELIEF_AT_SOURCE", "Relief at Source")
  case object SELF_ASSESSMENT extends APICategory("SELF_ASSESSMENT", "Self Assessment")
  case object STAMP_DUTY extends APICategory("STAMP_DUTY", "Stamp Duty")
  case object TRUSTS extends APICategory("TRUSTS", "Trusts")
  case object VAT_MTD extends APICategory("VAT_MTD", "VAT (Making Tax Digital)")
  case object VAT extends APICategory("VAT", "VAT")
  case object OTHER extends APICategory("OTHER", "Other")
}

