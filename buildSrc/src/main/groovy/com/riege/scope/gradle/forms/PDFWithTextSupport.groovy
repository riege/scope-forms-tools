package com.riege.scope.gradle.forms

import com.riege.jasperservice.model.PDFRawData
import scala.collection.JavaConverters
import scala.collection.immutable.Map$

class PDFWithTextSupport {

    static boolean isFormSupported(String formName) {
        return formName == "CmrWaybill"
    }

    static PDFRawData replaceEmbeddedText(PDFRawData data, String renderedText) {
        if (data.formName() == "CmrWaybill") {
            return replaceParameter(data, "mainreport.dataSource", buildCmrTextLineRows(renderedText))
        }
        return data
    }

    private static PDFRawData replaceParameter(PDFRawData pdfRawData, String name, Object scalaLines) {
        def updatedData = pdfRawData.data().updated(name, scalaLines)
        return new PDFRawData(
            pdfRawData.context(),
            pdfRawData.encryptPDF(),
            pdfRawData.pdfa(),
            pdfRawData.formName(),
            pdfRawData.dataSourceParameterName(),
            pdfRawData.backgroundImage(),
            updatedData
        )
    }

    static def buildCmrTextLineRows(String text) {
        def scalaLineMaps = (text?.readLines() ?: []).collect { line ->
            createSingleEntryScalaMap("cmrTextLine", line ?: "")
        }
        JavaConverters.asScalaBuffer(scalaLineMaps).toList()
    }

    static def createSingleEntryScalaMap(String key, String value) {
        def tupleSeq = JavaConverters.asScalaBuffer([
            new scala.Tuple2(key, value)
        ]).toSeq()
        Map$.MODULE$.apply(tupleSeq)
    }
}
