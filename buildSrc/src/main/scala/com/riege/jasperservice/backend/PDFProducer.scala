/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.jasperservice.backend

import java.io.BufferedOutputStream
import java.net.{URL, URLClassLoader}
import java.util

import scala.util.control.NonFatal

import akka.actor.{Actor, ActorLogging, Props, Status}

import com.riege.jasperservice.FileUtils
import com.riege.jasperservice.backend.FormsLoader.PROPERTY_FORM_DIR
import com.riege.jasperservice.functions.JasperServiceFunctions
import com.riege.jasperservice.model.{PDFDocument, PDFRawData, Simulations}

import better.files.{File, _}
import com.lowagie.text.DocumentException
import com.lowagie.text.pdf.PdfWriter
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource
import net.sf.jasperreports.engine.export.JRPdfExporter
import net.sf.jasperreports.engine.{JRParameter, JRPrintText, JasperFillManager, JasperPrint, JasperReport}
import net.sf.jasperreports.export.`type`.PdfaConformanceEnum
import net.sf.jasperreports.export.{SimpleExporterInput, SimpleOutputStreamExporterOutput, SimplePdfExporterConfiguration}
import org.apache.batik.ext.awt.image.spi.ImageTagRegistry

object PDFProducer {

  val PDF_PERMISSIONS: Int = PdfWriter.ALLOW_PRINTING |
                             PdfWriter.ALLOW_DEGRADED_PRINTING |
                             PdfWriter.ALLOW_COPY

  def apply(formsStore: String, fileStore: String): Props =
    Props(new PDFProducer(formsStore, fileStore))

}

class PDFProducer(
  formsStore: String,
  fileStore: String
) extends Actor with ActorLogging {

  override def receive: Receive = {
    case data: PDFRawData =>
      processRawData(data)
  }

  private def processRawData(data: PDFRawData): Unit = {
    Simulations.simulation(data) match {
      case Some(simulation) =>
        simulation.execute(fileStore, sender(), data)
      case None =>
        try {
          val pdfDocument = createDocument(data)
          sender() ! pdfDocument
        } catch {
          case NonFatal(e) =>
            import com.riege.jasperservice.frontend.JasperServiceProtocol._

            log.error(e, "Failed to produce a PDF document for the data:\n{}\n",
              pdfRawDataFormat.write(data).prettyPrint)
            sender() ! Status.Failure(BackendException(e))
        } finally {
          ImageTagRegistry.getRegistry.flushCache()
        }
    }
  }

  private def createDocument(data: PDFRawData): PDFDocument = {
    val ctx = data.context
    val formLoader = new FormsLoader(ctx, formsStore)
    val form = formLoader.getForm(data.formName, Option(ctx.locale))
    val exporter = getJRExporter(data)
    val printable = createPrintable(data, formLoader, form)
    val ec = new SimplePdfExporterConfiguration
    if (data.pdfa) {
      ec.setPdfaConformance(PdfaConformanceEnum.PDFA_1B)
      ec.setIccProfilePath(formLoader.getIccProfilePath)
    } else {
      if (data.encryptPDF) {
        ec.setPermissions(PDFProducer.PDF_PERMISSIONS)
        ec.setEncrypted(true)
        ec.set128BitKey(true)
        ec.setOwnerPassword(ctx.entityOID)
      }
      ec.setCompressed(true)
    }
    ec.setMetadataAuthor(ctx.branchCode + '.' + ctx.legalEntityCode)
    ec.setMetadataCreator("Scope " + ctx.version)
    ec.setTagLanguage(ctx.locale.getLanguage)
    exporter.setConfiguration(ec)
    exporter.setExporterInput(new SimpleExporterInput(printable._1))

    val contentFile = FileUtils.newTmpFileName

    for (
      out <- new BufferedOutputStream(File(fileStore, contentFile).newFileOutputStream()).autoClosed
    ) {
      exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out))
      exporter.exportReport()
    }

    val contentWithBackgroundFile = data.backgroundImage
      .map(image =>
        PDFUtils.addBackgroundImage(
          formLoader,
          fileStore,
          ctx.entityOID,
          contentFile,
          image,
          ctx.locale
        )
      )

    if (ctx.production) {
      PDFDocument(
        contentFile = contentFile,
        contentWithBackgroundFile = contentWithBackgroundFile
      )
    } else {
      PDFDocument(
        contentFile = contentFile,
        contentWithBackgroundFile = contentWithBackgroundFile,
        Some(form.getProperty(FormsLoader.PROPERTY_FORM_FILE)),
        printable._2
      )
    }
  }

  private def getJRExporter(data: PDFRawData): JRPdfExporter = {
    if (data.context.isCMRWaybill) {
      // CMR printing requires special handling.
      new JRPdfExporter() {

        @throws[DocumentException]
        override def exportText(text: JRPrintText): Unit = {
          if (text.getFontName.contains("Liberation Mono")) {
            pdfContentByte.setHorizontalScaling(118.0f)
          }
          super.exportText(text)
        }

      }
    } else {
      new JRPdfExporter
    }
  }

  private def createPrintable(
    data: PDFRawData,
    formLoader: FormsLoader,
    form: JasperReport
  ): (JasperPrint, Option[Map[String, Any]]) = {
    val locale = data.context.locale
    val localeOpt = Option(locale)
    val formDir = form.getProperty(PROPERTY_FORM_DIR)
    val jasperData = formLoader.toJasperData(data.data)

    val map: util.Map[String, Object] = jasperData.data
    map.put(PROPERTY_FORM_DIR, formDir)
    map.put(JRParameter.REPORT_LOCALE, locale)
    map.put(JRParameter.REPORT_CLASS_LOADER, getReportClassLoader(formDir))
    JasperServiceFunctions.setLoadFormHandler(
      formName => formLoader.getForm(formName, localeOpt)
    )
    JasperServiceFunctions.setLoadImageHandler(
      (imageName, ext) => formLoader.getImage(imageName, Option(ext), localeOpt)._2
    )
    try {
      val dataSource: JRMapCollectionDataSource =
        Option(map.get(data.dataSourceParameterName))
          .map(d => d.asInstanceOf[util.Collection[util.Map[String, _]]])
          .map(new JRMapCollectionDataSource(_))
          .orNull

      val jasperPrint = JasperFillManager.fillReport(form, map, dataSource)

      val sources = jasperData.sources.map(m =>
        m + (PROPERTY_FORM_DIR -> formDir, JRParameter.REPORT_LOCALE -> locale)
      )

      (jasperPrint, sources)
    } finally {
      JasperServiceFunctions.remove()
    }
  }

  private def getReportClassLoader(candidate: String): URLClassLoader = {
    val urls = Array(new URL(candidate))
    new URLClassLoader(urls, Thread.currentThread.getContextClassLoader)
  }

}
