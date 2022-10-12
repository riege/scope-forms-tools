/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.akka.json

import java.time.{Instant, LocalDate}
import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.util.{Base64, Currency, Locale, TimeZone, UUID}

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, deserializationError}

trait ExtDefaultJsonProtocol extends DefaultJsonProtocol with SprayJsonSupport {

  implicit def byteArrayFormat: JsonFormat[Array[Byte]] = new JsonFormat[Array[Byte]] {
    override def read(json: JsValue): Array[Byte] = json match {
      case x: JsString =>
        try {
          Base64.getMimeDecoder.decode(x.value)
        } catch {
          case e: IllegalArgumentException =>
            deserializationError(
              s"Unable to decode '$x' using Base64 MIME decoder",e)
        }
      case x => deserializationError(s"Expected JsString, not '${x.getClass}'")
    }

    override def write(obj: Array[Byte]): JsValue =
      JsString(Base64.getMimeEncoder.encodeToString(obj))
  }

  implicit def localDateFormat: JsonFormat[LocalDate] = new JsonFormat[LocalDate] {
    override def read(json: JsValue): LocalDate = json match {
      case x: JsString =>
        try {
          LocalDate.parse(x.value)
        } catch {
          case e: DateTimeParseException =>
            deserializationError(
              s"Unable to parse '$x' using format ${DateTimeFormatter.ISO_LOCAL_DATE}",
              e)
        }
      case x => deserializationError(s"Expected JsString, not '${x.getClass}'")
    }

    override def write(obj: LocalDate): JsValue =
      JsString(DateTimeFormatter.ISO_LOCAL_DATE.format(obj))
  }

  implicit def instantFormat: JsonFormat[Instant] = new JsonFormat[Instant] {
    override def read(json: JsValue): Instant = json match {
      case x: JsString =>
        try {
          Instant.parse(x.value)
        } catch {
          case e: DateTimeParseException =>
            deserializationError(
              s"Unable to parse '$x' using format ${DateTimeFormatter.ISO_INSTANT}",
              e)
        }
      case x => deserializationError(s"Expected JsString, not '${x.getClass}'")
    }

    override def write(obj: Instant): JsValue =
      JsString(DateTimeFormatter.ISO_INSTANT.format(obj))
  }

  implicit def localeFormat: JsonFormat[Locale] = new JsonFormat[Locale] {
    override def read(json: JsValue): Locale = json match {
      case x: JsString => Locale.forLanguageTag(x.value)
      case x => deserializationError(s"Expected JsString, not '${x.getClass}'")
    }

    override def write(obj: Locale): JsValue = JsString(obj.toLanguageTag)
  }

  def jsonEnum[T <: Enumeration](enum: T): JsonFormat[T#Value] = new JsonFormat[T#Value] {
    override def read(json: JsValue): T#Value = json match {
      case JsString(txt) =>
        try {
          enum.withName(txt)
        } catch {
          case e: NoSuchElementException =>
            deserializationError(s"Unknown enum value: $json", e)
        }
      case other => deserializationError(s"Expected a value from enum $enum instead of $other")
    }

    override def write(obj: T#Value) = JsString(obj.toString)
  }

  implicit def uuidFormat: JsonFormat[UUID] = new JsonFormat[UUID] {
    override def read(json: JsValue): UUID = json match {
      case x: JsString =>
        try {
          UUID.fromString(x.value)
        } catch {
          case e: IllegalArgumentException =>
            deserializationError(s"Unable to parse a UUID from $x", e)
        }
      case x => deserializationError(s"Expected JsString, not '${x.getClass}'")
    }

    override def write(obj: UUID): JsValue = JsString(obj.toString)
  }

  implicit def timeZoneFormat: JsonFormat[TimeZone] = new JsonFormat[TimeZone] {
    override def read(json: JsValue): TimeZone = json match {
      case x: JsString =>
        val id = x.value
        val tz = TimeZone.getTimeZone(id)
        if (!id.equals("GMT") && tz.getID.equals("GMT")) {
          deserializationError(s"Unknown time zone: $x")
        }
        tz
      case x => deserializationError(s"Expected JsString, not '${x.getClass}'")
    }

    override def write(obj: TimeZone): JsValue = JsString(obj.getID)
  }

  implicit def currencyFormat: JsonFormat[Currency] = new JsonFormat[Currency] {
    override def read(json: JsValue): Currency = json match {
      case x: JsString =>
        try {
          Currency.getInstance(x.value)
        } catch {
          case e: IllegalArgumentException =>
            deserializationError(s"Unknown currency: $x", e)
        }
      case x => deserializationError(s"Expected JsString, not '${x.getClass}'")
    }

    override def write(obj: Currency): JsValue = JsString(obj.getCurrencyCode)
  }

}
