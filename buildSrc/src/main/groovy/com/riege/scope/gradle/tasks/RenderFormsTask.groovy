/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.scope.gradle.tasks

import com.riege.jasperservice.LocalJasperService
import com.riege.jasperservice.LocalJasperService$
import com.riege.scope.gradle.forms.FormPath
import com.riege.scope.gradle.forms.FormRenderData
import com.riege.scope.gradle.forms.FormRenderDataCache
import com.riege.scope.gradle.forms.FormRenderDataFactory
import com.riege.scope.gradle.forms.PdfCreator
import net.sf.jasperreports.engine.JasperReport
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.api.tasks.incremental.InputFileDetails

import java.util.concurrent.TimeoutException

class RenderFormsTask extends DefaultTask {

    @InputDirectory
    File formSrcDir
    @InputDirectory
    File localFormDir
    @InputDirectory
    File dataDir
    @OutputDirectory
    File outputDir
    @InputFile
    File errorForm
    JasperReport errorReport

    static FormRenderDataCache gurkenCache = new FormRenderDataCache()

    def getFormPath() {
        new FormPath(formSrcDir.toPath(), localFormDir.toPath())
    }

    @TaskAction
    def render(IncrementalTaskInputs inputs) {
        LocalJasperService$.MODULE$.startUp(localFormDir.toString())
        List<InputFileDetails> outOfDate = []
        List<InputFileDetails> removed = []
        inputs.outOfDate { outOfDate << it }
        inputs.removed { removed << it }
        if (inputs.isIncremental()) {
            gurkenCache.invalidate(outOfDate)
            gurkenCache.invalidate(removed)
        } else {
            gurkenCache.invalidate()
        }
        def renderList = readRenderData()
        def rebuildSet = inputs.incremental \
                       ? calculateRebuildSet(renderList, outOfDate, removed)
                       : renderList
        rebuild(rebuildSet)
    }

    List<FormRenderData> readRenderData() {
        def factory = new FormRenderDataFactory(formPath: getFormPath(), dataDir: dataDir.toPath())
        def renderList = []
        dataDir.eachFileRecurse { file ->
            if (isLegacyFile(file)) {
                logger.warn("Ignoring ${file}. HTML files are no longer supported. Please use the JSON file instead.")
            }
            if (isRenderData(file)) {
                try {
                    renderList << gurkenCache.get(file, factory.&load)
                } catch (Exception e) {
                    handleRendererException(file, e)
                }
            }
        }
        renderList
    }

    static boolean isLegacyFile(File file) {
        file.name.matches(".*\\.html?") && file.isFile()
    }

    static boolean isRenderData(File file) {
        file.name.matches(".*\\.json") && file.isFile()
    }

    Set<FormRenderData> calculateRebuildSet(renderList, ArrayList<InputFileDetails> outOfDate, ArrayList<InputFileDetails> removed) {
        def rebuildSet = new HashSet<FormRenderData>()
        outOfDate.each { change ->
            rebuildSet.addAll(renderList.findAll { it.dependencies.contains(change.file) })
        }
        removed.each { change ->
            if (change.file.getCanonicalPath().startsWith(dataDir.getCanonicalPath())) {
                def relativeFile = new File(outputDir, "${relativeToDataDir(change.file)}.pdf")
                println "Removing ${relativeFile.absolutePath}"
                relativeFile.delete()
            }
        }
        return rebuildSet
    }

    void rebuild(Collection<FormRenderData> rebuildSet) {
        rebuildSet.each { data ->
            println "Rebuilding ${data.file}"
            try {
                byte[] pdf
                pdf = LocalJasperService.instance().render(data.jasperServiceData)
                pdf = PdfCreator.addTestOverlay(pdf)
                writePdfToOutputDir(data.outputName, pdf)
            } catch (Exception e) {
                handleRendererException(data.file, e)
            }
        }
    }

    void writePdfToOutputDir(String fileName, byte[] pdf) {
        def outputFile = new File(outputDir, fileName)
        outputFile.parentFile.mkdirs()
        outputFile.withOutputStream { it.write(pdf) }
    }

    def handleRendererException(File renderDataFile, Exception exception) {
        try {
            def outputName = new FormRenderData(fileName: relativeToDataDir(renderDataFile)).outputName
            def parameters = [:]
            parameters["exception"] = exception
            parameters["exception.stacktrace"] = exception.stackTrace.join("\n")
            parameters["data.file"] = renderDataFile
            writePdfToOutputDir(outputName, PdfCreator.createErrorPDF(loadErrorForm(), parameters))
            println("Unable to render ${renderDataFile}. See ${outputDir}/${outputName} for more information.")
        } catch (Exception errorPDFException) {
            exception.printStackTrace()
            println("Unable to create error PDF")
            errorPDFException.printStackTrace()
        } finally {
            if (exception instanceof TimeoutException) {
                logger.error("Possible Form-of-Death: Timeout while rendering $renderDataFile. Exiting.", exception)
                System.exit(1)
            }
        }
    }

    JasperReport loadErrorForm() {
        if (errorReport == null) {
            errorReport = errorForm.toPath().withObjectInputStream(Thread.currentThread().getContextClassLoader()) {
                it.readObject() as JasperReport
            }
        }
        errorReport
    }

    def relativeToDataDir(File dataFile) {
        dataDir.toPath().relativize(dataFile.toPath()).toFile()
    }

}
