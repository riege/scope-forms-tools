/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.jasperservice.model

import java.io.IOException
import java.nio.file.{Files, Paths}
import java.util
import java.util.{Locale, UUID}

import scala.util.Random
import scala.util.control.NonFatal

import akka.actor.{ActorRef, Status}

import com.riege.jasperservice.FileUtils.newTmpFileName
import com.riege.jasperservice.backend.{BackendException, PrintException}

object Simulations {

  sealed abstract class Simulation(val formName: String) {
    def execute(fileStore: String, sender: ActorRef, rawData: PDFRawData): Unit
  }

  object SimulateFormOfDeath extends Simulation("$SimulateFormOfDeath$") {

    /**
     * We must store it in a field because in other case HotSpot would optimize
     * all allocations away.
     */
    private[this] var garbage: util.List[Array[Int]] = _

    override def execute(fileStore: String, sender: ActorRef, rawData: PDFRawData): Unit = {
      garbage = new util.ArrayList[Array[Int]]()
      while (true) {
        garbage.add(Array.ofDim[Int](1024 * 1024))
      }
    }

  }

  object SimulateFailure extends Simulation("$SimulateFailure$") {

    override def execute(fileStore: String, sender: ActorRef, rawData: PDFRawData): Unit = {
      try {
        simulateFailure()
      } catch {
        case NonFatal(e) =>
          sender.tell(Status.Failure(BackendException(e)), ActorRef.noSender)
          throw e
      }
    }

    private def simulateFailure(): Unit = {
      try {
        Files.readAllBytes(Paths.get(newTmpFileName))
      } catch {
        case e: IOException =>
          throw new PrintException("Simulated failure", e)
      }
    }

  }

  object SimulateHungProcess extends Simulation("$SimulateHungProcess$") {

    override def execute(fileStore: String, sender: ActorRef, rawData: PDFRawData): Unit = {
      Thread.sleep(Integer.MAX_VALUE)
    }

  }

  object SimulateDocument extends Simulation("$SimulateDocument$") {

    override def execute(fileStore: String, sender: ActorRef, rawData: PDFRawData): Unit = {
      val size = 256 * 1024
      val random = new Random()
      val content = Array.ofDim[Byte](size)
      val contentWithBackground = Array.ofDim[Byte](size)
      random.nextBytes(content)
      random.nextBytes(contentWithBackground)

      val contentFile = newTmpFileName
      Files.write(Paths.get(fileStore, contentFile), content)
      val contentWithBackgroundFile = newTmpFileName
      Files.write(Paths.get(fileStore, contentWithBackgroundFile), contentWithBackground)

      val document = PDFDocument(
        contentFile = contentFile,
        contentWithBackgroundFile = Some(contentWithBackgroundFile)
      )
      sender.tell(document, ActorRef.noSender)
    }

  }

  private val simulations = Seq(
    SimulateFormOfDeath,
    SimulateFailure,
    SimulateHungProcess,
    SimulateDocument
  )

  def simulation(rawData: PDFRawData): Option[Simulation] =
    simulations.find(_.formName == rawData.formName)

  private lazy val ctx = {
    DocumentContext(
      "9.7.0",
      "ORG_Live",
      "SCOPE-ORG-LIVE",
      production = true,
      "the_user",
      "826cb224-f528-11de-bd43-00163e3a7df9",
      "ORG",
      "48509bb5-275f-4c68-96fa-cf97868c643e",
      "ORG",
      "04d542fe-8dab-42eb-8db7-71ffa999fbef",
      "ORGFRA",
      "04d542fe-8dab-42eb-8db7-71ffa9234bef",
      "DE",
      Locale.GERMANY,
      "00001877-c6e2-4b78-bb64-fc2474ff1cef",
      "x-scope/invoice-receivable",
      "Invoice"
    )
  }

  private def generateData(large: Boolean = false): Map[String, Any] = {

    def createMap(size: Int, addSubMaps: Boolean): Map[String, Any] = {
      val b = Map.newBuilder[String, Any]
      b.sizeHint(size)
      if (addSubMaps) {
        for (i <- 0 until size) {
          if (i % 2 == 0) {
            b += UUID.randomUUID().toString -> createMap(size, addSubMaps = false)
          } else {
            b += UUID.randomUUID().toString -> UUID.randomUUID().toString
          }
        }
      } else {
        for (_ <- 0 until size) {
          b += UUID.randomUUID().toString -> UUID.randomUUID().toString
        }
      }
      b += UUID.randomUUID().toString -> 666
      b += UUID.randomUUID().toString -> Image("background", Some(".svg"), Some(Locale.GERMANY))
      b += UUID.randomUUID().toString -> Locale.GERMANY
      b += UUID.randomUUID().toString -> BigInt(1)
      b += UUID.randomUUID().toString -> BigDecimal(2)
      b += UUID.randomUUID().toString -> Report("footer", Some(Locale.GERMANY))
      b += UUID.randomUUID().toString -> UploadedImage("background.svg", svg = true, Random.nextString(100).getBytes)
      b.result()
    }

    val size = if (large) {
        1000
      } else {
        10
      }
    createMap(size, addSubMaps = true)
  }

  def simulateFormOfDeath: PDFRawData =
    PDFRawData(
      ctx,
      encryptPDF = true,
      pdfa = false,
      SimulateFormOfDeath.formName,
      "mainreport.dataSource",
      backgroundImage = None,
      generateData()
    )

  def simulateFailure: PDFRawData =
    PDFRawData(
      ctx,
      encryptPDF = true,
      pdfa = false,
      SimulateFailure.formName,
      "mainreport.dataSource",
      backgroundImage = None,
      generateData()
    )

  def simulateHungProcess: PDFRawData =
    PDFRawData(
      ctx,
      encryptPDF = true,
      pdfa = false,
      SimulateHungProcess.formName,
      "mainreport.dataSource",
      backgroundImage = None,
      generateData()
    )

  def simulateDocument: PDFRawData =
    PDFRawData(
      ctx,
      encryptPDF = true,
      pdfa = false,
      SimulateDocument.formName,
      "mainreport.dataSource",
      backgroundImage = None,
      generateData()
    )

  def simulateLargeDocument: PDFRawData =
    PDFRawData(
      ctx,
      encryptPDF = true,
      pdfa = false,
      SimulateDocument.formName,
      "mainreport.dataSource",
      backgroundImage = None,
      generateData(true)
    )

}
