/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.scope.gradle.forms


import com.riege.jasperservice.LocalJasperService
import spock.lang.Specification

import java.nio.file.Paths

class FormRenderDataFactorySpec extends Specification {

    def testResources = Paths.get("src/test/resources").toAbsolutePath()
    def formPath = new FormPath(testResources, testResources)
    def factory = new FormRenderDataFactory(
        dataDir: testResources,
        formPath: formPath)
    def testJsonFile = factory.dataDir.resolve('rawData.json')
    def testJsonTextFile = factory.dataDir.resolve('rawData.text.json')

    def "extract pdf meta data from JSON document"() {
        when:
        def result = factory.load(testJsonFile)
        then:
        result.fileName == "rawData.json"
        result.outputName == "rawData.json.pdf"
        result.hasDependency(factory.formPath.out.resolve("testformdir/EExpDatPosition.jasper"))
        result.hasDependency(factory.formPath.out.resolve("testformdir/EExpDatPositionLoaded.jasper"))
        result.hasDependency(factory.formPath.out.resolve("testformdir/EExpDatPositionLoadedLocale_nl.jasper"))
        result.hasDependency(factory.formPath.out.resolve("testformdir/EExpDatPosition.png"))
        result.hasDependency(factory.formPath.out.resolve("testformdir/background_loaded.svg"))
        result.hasDependency(factory.formPath.out.resolve("background_loaded.png"))
        result.hasDependency(factory.formPath.out.resolve("testformdir/background_loaded_locale_nl.svg"))
        result.hasDependency(factory.formPath.out.resolve("testformdir/EExpDat.jasper"))
        result.hasDependency(factory.formPath.out.resolve("testformdir/background.svg"))
        result.hasDependency(factory.formPath.out.resolve("background.png"))
        result.hasDependency(factory.formPath.out.resolve("optionalBackground.svg"))
        result.hasDependency(factory.dataDir.resolve("rawData.json"))
        result.hasDependency(factory.formPath.out.resolve("header.jasper"))
        result.hasDependency(factory.formPath.out.resolve("footer.jasper"))
    }

    def "extract files referenced via \$P{formDir} expressions"() {
        setup:
        def testFile = '''
            <subreportExpression><![CDATA[$P{formDir}+"usaes7525vDocumentVehicleDetails.jasper"]]></subreportExpression>
            <subreportExpression><![CDATA[$P{formDir}+"seaHandlingUnits_de.jasper"]]></subreportExpression>
            <subreportExpression><![CDATA[$P{formDir} + "bookingConfirmationAccruals.jasper"]]></subreportExpression>
            <subreportExpression><![CDATA[$P{formDir}]]></subreportExpression>
            <imageExpression><![CDATA[$P{formDir} + "iata-logo.png"]]></imageExpression>
            CDATA[$P{formDir} + "iata-logo.png"]]></imageExpression
            <subreportExpression class="java.lang.String"><![CDATA[$P{formDir}+"deliveryOrderSea_de.package.dimension.jasper"]]></subreportExpression>
'''
        expect:
        factory.extractFormDirReferences(testFile) == [
            "usaes7525vDocumentVehicleDetails.jasper",
            "seaHandlingUnits_de.jasper",
            "bookingConfirmationAccruals.jasper",
            "iata-logo.png",
            "iata-logo.png",
            "deliveryOrderSea_de.package.dimension.jasper"
        ]
    }

    def "detect circular dependency"() {
        setup:
        def circularTestFile = factory.dataDir.resolve("rawData_circular_ref.json")

        when:
        def result = factory.load(circularTestFile)

        then:
        result.hasDependency(formPath.out.resolve("testformdir/EExpDat_circular.jasper"))
    }

    def "extract text meta data from JSON document"() {
        when:
        def result = factory.load(testJsonTextFile)
        then:
        result.fileName == "rawData.text.json"
        result.outputName == "rawData.text.json.txt"
        result.textData != null
        result.jasperServiceData == null
    }

    def "replaceCMRTextlines keeps PDFRawData metadata unchanged"() {
        setup:
        LocalJasperService.startUp(formPath.out.toString())
        def baseData = LocalJasperService.instance().read(testJsonFile.toString())

        when:
        def replaced = factory.replaceCMRTextlines(baseData, "x")

        then:
        replaced.context() == baseData.context()
        replaced.encryptPDF() == baseData.encryptPDF()
        replaced.pdfa() == baseData.pdfa()
        replaced.formName() == baseData.formName()
        replaced.dataSourceParameterName() == baseData.dataSourceParameterName()
        replaced.backgroundImage() == baseData.backgroundImage()
    }
}
