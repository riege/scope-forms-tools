/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.scope.gradle.forms

import com.lowagie.text.Element
import com.lowagie.text.pdf.BaseFont
import com.lowagie.text.pdf.PdfContentByte
import com.lowagie.text.pdf.PdfGState
import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.PdfStamper
import net.sf.jasperreports.engine.JREmptyDataSource
import net.sf.jasperreports.engine.JasperFillManager
import net.sf.jasperreports.engine.JasperPrint
import net.sf.jasperreports.engine.JasperReport
import net.sf.jasperreports.engine.export.JRPdfExporter
import net.sf.jasperreports.export.SimpleExporterInput
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput
import net.sf.jasperreports.export.SimplePdfExporterConfiguration

import java.awt.*
import java.text.DateFormat

class PdfCreator {

    static final def OVERLAY_STRING_TEST = "TEST PDF"
    static final def OVERLAY_STRING_ERROR = "ERROR"
    static final def HELVETICA_BOLD = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, false)
    static final def FONT_SIZE = 80

    /*
     * based on PDFUtils.addOverlayString(...)
     */
    static byte[] addOverlay(byte[] pdf, String overlayString) {
        PdfReader reader = new PdfReader(pdf)
        int pageCount = reader.getNumberOfPages()
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(pdf.length)
        PdfStamper stamper = new PdfStamper(reader, outputStream)
        float width = reader.getPageSize(1).getWidth()
        float height = reader.getPageSize(1).getHeight()
        for (int i = 1; i <= pageCount; i++) {
            addOverlay(stamper.getOverContent(i), overlayString, width, height)
        }
        stamper.close()
        return outputStream.toByteArray()
    }

    /*
     * based on PDFUtils.drawText(...)
     */
    static def addOverlay(PdfContentByte canvas, String overlayString, float imageWidth, float imageHeight) {
        def timestamp = DateFormat.getDateTimeInstance().format(new Date())
        int tsFontSize = FONT_SIZE/4
        PdfGState state = new PdfGState()
        state.setFillOpacity(0.4f)
        canvas.setGState(state)
        canvas.beginText()
        canvas.setFontAndSize(HELVETICA_BOLD, FONT_SIZE)
        canvas.setColorFill(Color.PINK)
        canvas.showTextAligned(Element.ALIGN_CENTER, overlayString, (float) (imageWidth/2), (float) (imageHeight/2), 45f)
        canvas.setFontAndSize(HELVETICA_BOLD, tsFontSize)
        canvas.showTextAligned(Element.ALIGN_CENTER, timestamp, (float) (imageWidth/2), 5f, 0f)
        canvas.showTextAligned(Element.ALIGN_CENTER, timestamp, (float) (imageWidth/2), (float) (imageHeight-tsFontSize-5), 0f)
        canvas.endText()
    }

    /*
     * based on PDFDocumentProduces.createContent(...)
     */
    static byte[] createPDF(JasperReport form, Map parameters) throws Exception {
        JRPdfExporter exporter = new JRPdfExporter()
        SimplePdfExporterConfiguration ec = new SimplePdfExporterConfiguration()
        ec.setCompressed(Boolean.TRUE)
        ec.setMetadataAuthor(System.getProperty("user.name"))
        ec.setMetadataCreator("Forms Test Renderer")
        ec.setTagLanguage(Locale.default.language)
        exporter.setConfiguration(ec)

        JasperPrint print = JasperFillManager.fillReport(form, parameters, new JREmptyDataSource())
        exporter.setExporterInput(new SimpleExporterInput(print))

        ByteArrayOutputStream out = new ByteArrayOutputStream(32 * 1024)
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out))
        exporter.exportReport()
        return out.toByteArray()
    }

    static byte[] addTestOverlay(byte[] pdf) {
        addOverlay(pdf, OVERLAY_STRING_TEST)
    }

    static byte[] createErrorPDF(JasperReport form, Map parameters) {
        addOverlay(createPDF(form, parameters), OVERLAY_STRING_ERROR)
    }

}
