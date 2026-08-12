/*
 * Copyright (c) 2017 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.jasperservice.backend

import java.lang.StringBuilder
import java.util

import scala.util.control.NonFatal

import akka.actor.{Actor, ActorLogging, Props, Status}

import com.riege.jasperservice.backend.FormsLoader.PROPERTY_FORM_DIR
import com.riege.jasperservice.functions.JasperServiceFunctions
import com.riege.jasperservice.model.{TextDocument, TextRawData}

import net.sf.jasperreports.engine.data.JRMapCollectionDataSource
import net.sf.jasperreports.engine.export.JRTextExporter
import net.sf.jasperreports.engine.{JRParameter, JasperFillManager, JasperPrint, JasperReport}
import net.sf.jasperreports.export.{SimpleExporterInput, SimpleTextExporterConfiguration, SimpleTextReportConfiguration, SimpleWriterExporterOutput}

object TextProducer {

  def apply(formsStore: String): Props =
    Props(new TextProducer(formsStore))

}

/**
 * @author <a href="mailto:golovnin@riege.com">Andrej Golovnin</a>
 */
class TextProducer(
  formsStore: String
) extends Actor with ActorLogging {

  override def receive: Receive = {
    case data: TextRawData =>
      try {
        val document = createDocument(data)
        sender() ! document
      } catch {
        case NonFatal(e) =>
          import com.riege.jasperservice.frontend.JasperServiceProtocol._

          log.error(e, "Failed to produce a text document for the data:\n{}\n",
            textRawDataFormat.write(data).prettyPrint)
          sender() ! Status.Failure(BackendException(e))
      }
  }

  private def createDocument(data: TextRawData): TextDocument = {
    val ctx = data.context
    val formLoader = new FormsLoader(ctx, formsStore)
    val form = formLoader.getForm(data.formName, Option(ctx.locale))
    val exporter = getJRExporter(data)
    val printable = createPrintable(data, formLoader, form)

    val out = new StringBuilder()
    exporter.setExporterInput(new SimpleExporterInput(printable))
    exporter.setExporterOutput(new SimpleWriterExporterOutput(out))
    exporter.exportReport()

    if (ctx.production) {
      TextDocument(out.toString)
    } else {
      TextDocument(out.toString, Some(form.getProperty(FormsLoader.PROPERTY_FORM_FILE)))
    }
  }

  private def getJRExporter(data: TextRawData): JRTextExporter = {
    val ec = new SimpleTextExporterConfiguration
    ec.setLineSeparator(data.lineSeparator.getOrElse("\n"))
    ec.setPageSeparator(data.pageSeparator.getOrElse("\n"))
    val rc = new SimpleTextReportConfiguration
    rc.setCharHeight(float2Float(data.charHeight.getOrElse(12.0f)))
    rc.setPageWidthInChars(int2Integer(data.pageWidthInChars.getOrElse(74)))
    rc.setPageHeightInChars(int2Integer(data.pageHeightInChars.getOrElse(64)))
    val exporter = new JRTextExporter
    exporter.setConfiguration(ec)
    exporter.setConfiguration(rc)
    exporter
  }

  private def createPrintable(
    data: TextRawData,
    formLoader: FormsLoader,
    form: JasperReport
  ): JasperPrint = {
    val locale = data.context.locale
    val localeOpt = Option(locale)
    val formDir = form.getProperty(PROPERTY_FORM_DIR)
    val jasperData = formLoader.toJasperData(data.data)
    val map: util.Map[String, Object] = jasperData.data
    map.put(PROPERTY_FORM_DIR, formDir)
    map.put(JRParameter.REPORT_LOCALE, locale)
    JasperServiceFunctions.setLoadFormHandler(
      formName => formLoader.getForm(formName, localeOpt)
    )
    try {
      var dataSource: JRMapCollectionDataSource = null
      val dataSourceData: util.Collection[util.Map[String, _]] =
        map.get(data.dataSourceParameterName)
          .asInstanceOf[util.Collection[util.Map[String, _]]]
      if (dataSourceData != null) {
        dataSource = new JRMapCollectionDataSource(dataSourceData)
      }

      val jasperPrint = JasperFillManager.fillReport(form, map, dataSource)

      data.pageHeight.foreach(jasperPrint.setPageHeight)
      data.pageWidth.foreach(jasperPrint.setPageWidth)

      jasperPrint
    } finally {
      JasperServiceFunctions.remove()
    }
  }

}
