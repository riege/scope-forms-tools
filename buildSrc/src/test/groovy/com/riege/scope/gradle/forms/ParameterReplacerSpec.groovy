package com.riege.scope.gradle.forms

import com.riege.jasperservice.LocalJasperService
import scala.collection.JavaConverters
import spock.lang.Specification

import java.nio.file.Paths

class ParameterReplacerSpec extends Specification {

    def testResources = Paths.get("src/test/resources").toAbsolutePath()
    def formPath = new FormPath(testResources, testResources)
    def factory = new FormRenderDataFactory(
        dataDir: testResources,
        formPath: formPath)

    def testJsonFile = factory.dataDir.resolve('rawData.json')
    def testJsonWithTextFile = factory.dataDir.resolve('pdfWithText.json')
    def testJsonTextFile = factory.dataDir.resolve('pdfWithText.text.json')

    def "replaceCMRTextlines keeps PDFRawData metadata unchanged"() {
        setup:
        LocalJasperService.startUp(formPath.out.toString())
        def baseData = LocalJasperService.instance().read(testJsonFile.toString())

        when:
        def replaced = ParameterReplacer.replaceCMRTextlines(baseData, "x")

        then:
        replaced.context() == baseData.context()
        replaced.encryptPDF() == baseData.encryptPDF()
        replaced.pdfa() == baseData.pdfa()
        replaced.formName() == baseData.formName()
        replaced.dataSourceParameterName() == baseData.dataSourceParameterName()
        replaced.backgroundImage() == baseData.backgroundImage()
    }

    def "replaceCMRTextlines replaces mainreport datasource with expected lines"() {
        setup:
        LocalJasperService.startUp(formPath.out.toString())
        def baseData = LocalJasperService.instance().read(testJsonFile.toString())

        when:
        def replaced = ParameterReplacer.replaceCMRTextlines(baseData, "first\n\nthird")
        def javaMap = JavaConverters.mapAsJavaMap(replaced.data())
        def scalaLines = javaMap.get("mainreport.dataSource")
        def lines = JavaConverters.seqAsJavaList(scalaLines)
        def row1 = JavaConverters.mapAsJavaMap(lines[0])
        def row2 = JavaConverters.mapAsJavaMap(lines[1])
        def row3 = JavaConverters.mapAsJavaMap(lines[2])

        then:
        lines.size() == 3
        row1.get("cmrTextLine") == "first"
        row2.get("cmrTextLine") == ""
        row3.get("cmrTextLine") == "third"
    }

    def "createSingleEntryScalaMap creates scala map with one entry"() {
        when:
        def scalaMap = ParameterReplacer.createSingleEntryScalaMap("cmrTextLine", "abc")
        def javaMap = JavaConverters.mapAsJavaMap(scalaMap)

        then:
        javaMap.size() == 1
        javaMap.get("cmrTextLine") == "abc"
    }

    def "buildCmrTextLineRows creates scala list of scala maps"() {
        when:
        def scalaLines = ParameterReplacer.buildCmrTextLineRows("a\n\nc")
        def lines = JavaConverters.seqAsJavaList(scalaLines)
        def row1 = JavaConverters.mapAsJavaMap(lines[0])
        def row2 = JavaConverters.mapAsJavaMap(lines[1])
        def row3 = JavaConverters.mapAsJavaMap(lines[2])

        then:
        lines.size() == 3
        row1.get("cmrTextLine") == "a"
        row2.get("cmrTextLine") == ""
        row3.get("cmrTextLine") == "c"
    }
}
