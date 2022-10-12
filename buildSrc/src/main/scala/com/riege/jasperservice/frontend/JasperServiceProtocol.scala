/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.jasperservice.frontend

import java.util.Locale

import spray.json._

import com.riege.akka.json.ExtDefaultJsonProtocol
import com.riege.jasperservice.model._

object ObjectType extends Enumeration {
  type ObjectType = Value
  val INT, BIGDECIMAL, BIGINTEGER, IMAGE, UPLOADED_IMAGE, REPORT, LOCALE = Value
}

import com.riege.jasperservice.frontend.ObjectType._

object TypedNumber {

  def apply(value: Int): TypedNumber =
    new TypedNumber(ObjectType.INT, BigDecimal(value))

  def apply(value: BigInt): TypedNumber =
    new TypedNumber(ObjectType.BIGINTEGER, BigDecimal(value))

  def apply(value: BigDecimal): TypedNumber =
    new TypedNumber(ObjectType.BIGDECIMAL, value)

}
final case class TypedNumber($otype: ObjectType, value: BigDecimal)

object TypedImage {

  def apply(img: Image): TypedImage =
    TypedImage(name = img.name, ext = img.ext, locale = img.locale)

}
final case class TypedImage(
  $otype: ObjectType = IMAGE,
  name: String,
  ext: Option[String],
  locale: Option[Locale] = None
) {

  def toImage = Image(name, ext, locale)

}

object TypedUploadedImage {

  def apply(img: UploadedImage): TypedUploadedImage =
    TypedUploadedImage(fileName = img.fileName, svg = img.svg, data = img.data)

}
final case class TypedUploadedImage(
  $otype: ObjectType = UPLOADED_IMAGE,
  fileName: String,
  svg: Boolean,
  data: Array[Byte]
) {

  def toUploadedImage = UploadedImage(fileName, svg, data)

}

object TypedReport {

  def apply(r: Report): TypedReport =
    TypedReport(name = r.name, language = r.language)

}
final case class TypedReport(
  $otype: ObjectType = REPORT,
  name: String,
  language: Option[Locale] = None
) {

  def toReport = Report(name, language)

}

object TypedLocale {

  def apply(l: Locale): TypedLocale = TypedLocale(LOCALE, l)

}
final case class TypedLocale(
  $otype: ObjectType,
  locale: Locale
)

object JasperServiceProtocol extends JasperServiceProtocol {

  // Allows to use formats without implementing the trait.

}

/**
 * Defines the Json protocol for PDF Service.
 */
trait JasperServiceProtocol extends ExtDefaultJsonProtocol {

  /**
   * Used to define the type of the JsObject.
   * The special name was chosen to avoid collisions with keys from Scope.
   */
  private val OBJECT_TYPE_KEY = "$otype"

  implicit object AnyJsonFormat extends JsonFormat[Any] {
    override def write(x: Any): JsValue = x match {
      case n: Int => typedNumberFormat.write(TypedNumber(n))
      case bi: BigInt => typedNumberFormat.write(TypedNumber(bi))
      case d: BigDecimal => typedNumberFormat.write(TypedNumber(d))
      case s: String => JsString(s)
      case l: List[_] =>
        val la: List[Any] = l.asInstanceOf[List[Any]]
        la.toJson
      case m: Map[String, _] =>
        val ma: Map[String, Any] = m.asInstanceOf[Map[String, Any]]
        ma.toJson
      case b: Boolean => JsBoolean(b)
      case i: Image => imageFormat.write(TypedImage(i))
      case u: UploadedImage => uploadedImageFormat.write(TypedUploadedImage(u))
      case r: Report => reportFormat.write(TypedReport(r))
      case loc: Locale => typedLocaleFormat.write(TypedLocale(loc))
      case z => serializationError("Unable to write an object of type " + z.getClass.getName + ": " + z)
    }
    override def read(value: JsValue): Any = value match {
      case JsNumber(n) => n.intValue()
      case JsString(s) => s
      case a: JsArray => listFormat[Any].read(a)
      case o: JsObject =>
        val typed = o.fields.get(OBJECT_TYPE_KEY)
          .map(v => {
            val $otype = objectTypeFormat.read(v)
            $otype match {
              case INT =>
                typedNumberFormat.read(o).value.intValue()
              case BIGINTEGER =>
                typedNumberFormat.read(o).value.toBigInt()
              case BIGDECIMAL =>
                typedNumberFormat.read(o).value
              case IMAGE =>
                imageFormat.read(o).toImage
              case UPLOADED_IMAGE =>
                uploadedImageFormat.read(o).toUploadedImage
              case REPORT =>
                reportFormat.read(o).toReport
              case LOCALE =>
                typedLocaleFormat.read(o).locale
              case x =>
                deserializationError("Unsupported object type " + x)
            }
          })

        if (typed.isDefined) {
          typed.get
        } else {
          mapFormat[String, Any].read(value)
        }
      case b: JsBoolean => b.value
      case x => deserializationError("Unable to read " + x)
    }
  }

  implicit val documentContextFormat: RootJsonFormat[DocumentContext] = jsonFormat17(DocumentContext.apply)
  implicit val objectTypeFormat: JsonFormat[ObjectType] = jsonEnum(ObjectType)
  implicit val typedNumberFormat: RootJsonFormat[TypedNumber] = jsonFormat2(TypedNumber.apply)
  implicit val typedLocaleFormat: RootJsonFormat[TypedLocale] = jsonFormat2(TypedLocale.apply)
  implicit val reportFormat: RootJsonFormat[TypedReport] = jsonFormat3(TypedReport.apply)
  implicit val imageFormat: RootJsonFormat[TypedImage] = jsonFormat4(TypedImage.apply)
  implicit val uploadedImageFormat: RootJsonFormat[TypedUploadedImage] = jsonFormat4(TypedUploadedImage.apply)
  implicit val pageDimensionFormat: RootJsonFormat[PageDimension] = jsonFormat2(PageDimension)
  implicit val pdfRawDataFormat: RootJsonFormat[PDFRawData] = jsonFormat7(PDFRawData)
  implicit val pdfDocumentFormat: RootJsonFormat[PDFDocument] = jsonFormat4(PDFDocument)
  implicit val labelRawDataFormat: RootJsonFormat[LabelRawData] = jsonFormat4(LabelRawData)
  implicit val labelDocumentFormat: RootJsonFormat[LabelDocument] = jsonFormat2(LabelDocument)
  implicit val textRawDataFormat: RootJsonFormat[TextRawData] = jsonFormat11(TextRawData)
  implicit val textDocumentFormat: RootJsonFormat[TextDocument] = jsonFormat2(TextDocument)

}
