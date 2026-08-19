/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.scope.gradle.tasks

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

    def "removed text json deletes txt output"() {
        setup:
        File tempOutputDir = File.createTempDir("RenderFormsTaskSpec", "")
        File tempDataDir = File.createTempDir("RenderFormsTaskSpecData", "")
        def nested = new File(tempDataDir, "nested")
        nested.mkdirs()
        def removedInput = new File(nested, "document.text.json")
        removedInput.text = "{}"
        def outputFile = new File(tempOutputDir, "nested/document.text.json.txt")
        outputFile.parentFile.mkdirs()
        outputFile.text = "stale"
        def project = ProjectBuilder.builder().build()
        def task = project.task('testTaskDeleteTxt', type: RenderFormsTask) {
            dataDir = tempDataDir
            outputDir = tempOutputDir
            localFormDir = dataDir
            formSrcDir = dataDir
            errorForm = new File("src/test/resources/ErrorPDF.jasper")
        }
        def removed = [removedInput]

        when:
        task.calculateRebuildSet([], [] as ArrayList, removed)

        then:
        !outputFile.exists()

        cleanup:
        tempOutputDir.deleteDir()
        tempDataDir.deleteDir()
    }

    def "removed text json rebuilds sibling pdf input"() {
        setup:
        File tempOutputDir = File.createTempDir("RenderFormsTaskSpec", "")
        File tempDataDir = File.createTempDir("RenderFormsTaskSpecData", "")
        def nested = new File(tempDataDir, "nested")
        nested.mkdirs()
        def pdfInput = new File(nested, "document.json")
        pdfInput.text = "{}"
        def removedInput = new File(nested, "document.text.json")
        removedInput.text = "{}"
        def renderData = new com.riege.scope.gradle.forms.FormRenderData(file: pdfInput, fileName: "nested/document.json")
        def project = ProjectBuilder.builder().build()
        def task = project.task('testTaskRebuildSiblingPdf', type: RenderFormsTask) {
            dataDir = tempDataDir
            outputDir = tempOutputDir
            localFormDir = dataDir
            formSrcDir = dataDir
            errorForm = new File("src/test/resources/ErrorPDF.jasper")
        }
        def removed = [removedInput]

        when:
        def rebuildSet = task.calculateRebuildSet([renderData], [] as ArrayList, removed)

        then:
        rebuildSet == [renderData] as Set

        cleanup:
        tempOutputDir.deleteDir()
        tempDataDir.deleteDir()
    }

}
