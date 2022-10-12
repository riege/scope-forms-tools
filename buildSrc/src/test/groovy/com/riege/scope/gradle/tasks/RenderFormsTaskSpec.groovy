/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.scope.gradle.tasks

import org.gradle.api.internal.changedetection.changes.RebuildIncrementalTaskInputs
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class RenderFormsTaskSpec extends Specification {

    def "valid render files"() {
        expect:
        RenderFormsTask.isRenderData(new File("src/test/resources/rawData.json"))
    }

    def "render file must have JSON extension"() {
        setup:
        def file = new File("src/test/resources/EExpDat.jasper")
        expect:
        file.exists()
        !RenderFormsTask.isRenderData(file)
    }

    def "render file must exist"() {
        expect:
        !RenderFormsTask.isRenderData(new File("doesNotExist"))
    }

    def "render file must be a file"() {
        setup:
        def dir = new File("src/test/resources")
        expect:
        dir.exists()
        !RenderFormsTask.isRenderData(dir)
    }

    def "test error PDF creation"() {
        setup:
        File tempDir = File.createTempDir("RenderFormsTaskSpec", "")
        def project = ProjectBuilder.builder().build()
        def task = project.task('testTask', type: RenderFormsTask) {
            dataDir = new File("src/test/resources")
            localFormDir = new File("src/test/resources")
            formSrcDir = new File("src/test/resources")
            outputDir = tempDir
            errorForm = new File("src/test/resources/ErrorPDF.jasper")
        }

        when:
        task.handleRendererException(new File("src/test/resources/errorTest.json"), new Exception("test exception"))

        then:
        new File(tempDir, "errorTest.json.pdf").exists()

        cleanup:
        tempDir.deleteDir()
    }

}
