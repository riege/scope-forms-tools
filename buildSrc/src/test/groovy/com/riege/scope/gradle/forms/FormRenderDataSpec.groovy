/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.scope.gradle.forms

import com.riege.jasperservice.model.PDFRawData
import com.riege.jasperservice.model.TextRawData
import spock.lang.Specification

class FormRenderDataSpec extends Specification {

    def data = new FormRenderData()

    def "Output name throws NPE"() {
        when:
        data.outputName
        then:
        thrown(NullPointerException)
    }

    def "Output name adds .pdf"() {
        data.fileName = INPUT_NAME
        expect:
        data.outputName == OUTPUT_NAME
        where:
        INPUT_NAME  | OUTPUT_NAME
        "test.html" | "test.html.pdf"
        "bla"       | "bla.pdf"
    }

    def "dependencies are unique"() {
        when:
        data.addDependency(new File("/FormRenderDataSpec/test"))
        data.addDependency(new File("/FormRenderDataSpec/test2"))
        data.addDependency(new File("/FormRenderDataSpec/test"))
        data.addDependency(new File("test"))
        then:
        data.dependencies.size() == 3
        data.hasDependency(new File("/FormRenderDataSpec/test"))
        data.hasDependency(new File("/FormRenderDataSpec/test2"))
        data.hasDependency(new File("test"))
        data.dependencies.every {it.isAbsolute()}
    }

    def "Output name adds .txt for plain text"() {
        data.fileName = INPUT_NAME
        data.textData = new TextRawData(null, "testForm", null, null, null, null, null, null, null, null, null)
        expect:
        data.outputName == OUTPUT_NAME
        where:
        INPUT_NAME  | OUTPUT_NAME
        "test.html" | "test.html.txt"
        "bla"       | "bla.txt"
    }

    def "Output name adds .pdf for PDF with embedded plain text"() {
        data.fileName = INPUT_NAME
        data.jasperServiceData = new PDFRawData(null, false, false, "testPDFForm", null, null, null)
        data.textData = new TextRawData(null, "testForm", null, null, null, null, null, null, null, null, null)
        expect:
        data.outputName == OUTPUT_NAME
        where:
        INPUT_NAME  | OUTPUT_NAME
        "test.html" | "test.html.pdf"
        "bla"       | "bla.pdf"
    }

}
