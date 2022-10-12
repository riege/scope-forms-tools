/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.jasperservice

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import better.files.File
import com.riege.jasperservice.backend._
import com.riege.jasperservice.frontend.JasperServiceProtocol
import com.riege.jasperservice.model.{PDFDocument, PDFRawData}
import spray.json._

import scala.concurrent.duration.SECONDS
import scala.concurrent.{Await, Future}

object LocalJasperService extends JasperServiceProtocol {

  var instance: LocalJasperService = null

  def main(args: Array[String]): Unit = {
    val formsStore = "/Users/radig/workspaces/scope/forms/build/renderFormsTool/build/forms"
    startUp(formsStore)

    File("formData")
      .list(file => file.extension.contains(".json"))
      .map(file => Tuple2(file.toString() + ".pdf", instance.render(instance.read(file.toString()))))
      .foreach(result => {
        File(result._1).writeByteArray(result._2)
        instance.system.log.info("Written PDF to " + result._1)
      })

    instance.system.terminate()
  }

  def startUp(formsStore: String) = {
    val fileStore = Files.createTempDirectory("local-jasper-service")
    if (instance == null) {
      instance = new LocalJasperService(fileStore.toString, formsStore)
    }
  }

}

class LocalJasperService(val fileStore: String, val formsStore: String) extends JasperServiceProtocol {

  implicit val system: ActorSystem = ActorSystem("jasper-service-local")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(10, SECONDS)

  import system._

  log.info("Starting local Jasper Service ...")

  File(fileStore).createDirectoryIfNotExists(createParents = true)

//  val tmpFilesCleaner = system.actorOf(
//    TmpFilesCleaner(fileStore)
//  )

  val pdfProducer = actorOf(PDFProducer(formsStore, fileStore), "pdf-producer")

  //    pageDimensionResolver = actorOf(PageDimensionResolver(formsStore), "page-dimension-resolver")
  //    textProducer = actorOf(TextProducer(formsStore), "text-producer")

  // val labelProducer = system.actorOf(LabelProducer(formsStore, ???), "label-producer")

  def read(dataFile: String): PDFRawData = {
    val defaults : Map[String, JsValue] = Map(
      "encryptPDF" -> JsFalse,
      "pdfa" -> JsFalse,
    )
    val json = File(dataFile).lines(UTF_8).mkString.parseJson
    val fields = json.asJsObject.fields
    val jsonWithDefaults = JsObject(fields ++ defaults.map{ case (k,v) => k -> fields.getOrElse(k, v)})
    pdfRawDataFormat.read(jsonWithDefaults).copy(encryptPDF = false)
  }

  def render(data: PDFRawData): Array[Byte] = {
    val pdfFuture: Future[Any] = pdfProducer ? data
    val result = Await.result(pdfFuture, timeout.duration)
    val pdf = result.asInstanceOf[PDFDocument]
    val tmpFile = File(fileStore + "/" + pdf.contentFile)
    val bytes = tmpFile.loadBytes
    tmpFile.delete()
    bytes
  }
}
