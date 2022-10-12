/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.jasperservice.model

import java.io.File
import java.util.{Locale, StringJoiner}

object DocumentContext {

  /**
   * Creates and returns a minimalist context using given values.
   *
   * @param organizationCode  the organization code of the new context.
   * @param legalEntityCode   the legal entity code of the new context.
   * @param branchCode        the branch code of the new context.
   * @param countryCode       the country code of the new context.
   * @param production        indicates whether it is a production system or not.
   * @param locale            the locale of the new context.
   *
   * @return a minimalist context using given values.
   */
  def createDocumentContext(
    organizationCode: String,
    legalEntityCode: String,
    branchCode: String,
    countryCode: String,
    production: Boolean,
    locale: Option[Locale]
  ): DocumentContext = DocumentContext(
    version = "",
    domain = "",
    systemID = "",
    production = production,
    userID = "",
    organizationOID = "",
    organizationCode = organizationCode,
    legalEntityOID = "",
    legalEntityCode = legalEntityCode,
    branchOID = "",
    branchCode = branchCode,
    countryOID = "",
    countryCode = countryCode,
    locale = locale.orNull,
    entityOID = "",
    mimeType = "",
    mimeTypeDescription = ""
  )

}

/**
 * Represents a document context in Scope.
 *
 * @param version              the version of the Scope system.
 * @param domain               the Scope domain.
 * @param systemID             the ID of the Scope system.
 * @param production           indicates whether it is a production system or not.
 * @param organizationOID      the OID of the organization the branch belongs to.
 * @param organizationCode     the code of the organization the branch belongs to.
 * @param legalEntityOID       the OID of the legal entity the branch belongs to.
 * @param legalEntityCode      the code of the legal entity the branch belongs to.
 * @param branchOID            the OID of the branch in Scope.
 * @param branchCode           the code of the branch in Scope.
 * @param countryOID           the OID of the country in Scope.
 * @param countryCode          the code of the country in Scope.
 * @param locale               the locale of the document.
 * @param entityOID            the entity OID to create the document for.
 * @param mimeType             the MIME type of the document.
 * @param mimeTypeDescription  the description of the MIME type.
 */
final case class DocumentContext(
  version: String,
  domain: String,
  systemID: String,
  production: Boolean,
  userID: String,
  organizationOID: String,
  organizationCode: String,
  legalEntityOID: String,
  legalEntityCode: String,
  branchOID: String,
  branchCode: String,
  countryOID: String,
  countryCode: String,
  locale: Locale,
  entityOID: String,
  mimeType: String,
  mimeTypeDescription: String
) {

  def isCMRWaybill: Boolean = mimeType.startsWith("x-scope/fwd-cmrWaybill-page")

  def toCatchMessage(scopeURL: Option[String], formFile: Option[File]): String =
    new StringJoiner("\n")
      .add("h2. Environment")
      .add(s"|| Scope Domain | $domain |")
      .add(s"|| Scope URL | ${scopeURL.getOrElse(systemID)} |")
      .add(s"|| Scope Version | $version |")
      .add(s"|| Organization | $organizationCode {{($organizationOID)}} |")
      .add(s"|| Legal Entity | $legalEntityCode {{($legalEntityOID)}} |")
      .add(s"|| Branch | $branchCode {{($branchOID)}} |")
      .add(s"|| Country | $countryCode {{($countryOID)}} |")
      .add(s"|| Locale | $locale |")
      .add(s"|| User | $userID |")
      .add("")
      .add("h2. Printout")
      .add(s"|| Entity OID | {{$entityOID}} |")
      .add(s"|| MIME Type | $mimeTypeDescription {{($mimeType)}} |")
      .add(s"|| Form File | {{${formFile.map(_.getAbsolutePath).getOrElse("unknown")}}} |")
      .toString

}
