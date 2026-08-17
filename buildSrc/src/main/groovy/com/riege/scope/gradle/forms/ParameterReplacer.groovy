package com.riege.scope.gradle.forms

import com.riege.jasperservice.model.PDFRawData
import scala.collection.JavaConverters
import scala.collection.immutable.Map$

class ParameterReplacer {

    static PDFRawData replaceCMRTextlines(PDFRawData pdfRawData, String text) {
        def scalaLines = buildCmrTextLineRows(text)
        def updatedData = pdfRawData.data().updated("mainreport.dataSource", scalaLines)
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
