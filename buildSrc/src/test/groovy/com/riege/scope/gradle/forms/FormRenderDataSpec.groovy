/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.scope.gradle.forms

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

}
