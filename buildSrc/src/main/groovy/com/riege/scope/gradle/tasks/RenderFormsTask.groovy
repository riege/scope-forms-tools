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
import com.riege.scope.gradle.forms.PDFWithTextSupport
import com.riege.scope.gradle.forms.PdfCreator
import net.sf.jasperreports.engine.JasperReport
import org.gradle.api.file.FileType
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeoutException

class RenderFormsTask extends DefaultTask {

    @Incremental
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    File formSrcDir
    @Incremental
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    File localFormDir
    @Incremental
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    File dataDir
    @OutputDirectory
    File outputDir
    @InputFile
    File errorForm
    @Internal
    JasperReport errorReport

    static FormRenderDataCache gurkenCache = new FormRenderDataCache()

    def getFormPath() {
        new FormPath(formSrcDir.toPath(), localFormDir.toPath())
    }

    @TaskAction
    def render(InputChanges inputs) {
        LocalJasperService$.MODULE$.startUp(localFormDir.toString())
        List<File> outOfDate = []
        List<File> removed = []
        [formSrcDir, localFormDir, dataDir].each { inputDir ->
            inputs.getFileChanges(inputDir).each { FileChange change ->
                if (change.fileType != FileType.FILE) {
                    return
                }
                if (change.changeType == ChangeType.REMOVED) {
                    removed << change.file
                } else {
                    outOfDate << change.file
                }
            }
        }
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

    Set<FormRenderData> calculateRebuildSet(renderList, Collection<File> outOfDate, Collection<File> removed) {
        def rebuildSet = new HashSet<FormRenderData>()
        outOfDate.each { changedFile ->
            rebuildSet.addAll(renderList.findAll { it.dependencies.contains(changedFile) })
        }
        removed.each { removedFile ->
            rebuildSet.addAll(rebuildEntriesForRemovedFile(renderList, removedFile))
            if (removedFile.getCanonicalPath().startsWith(dataDir.getCanonicalPath())) {
                def relativeFile = new File(outputDir, removedOutputName(removedFile))
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
                byte[] renderedBytes
                if (data.jasperServiceData != null) {
                    def renderData = data.jasperServiceData
                    if (data.textData != null) {
                        def generatedText = LocalJasperService.instance().render(data.textData)
                        renderData = PDFWithTextSupport.replaceEmbeddedText(data.jasperServiceData, new String(generatedText, StandardCharsets.UTF_8))
                    }
                    renderedBytes = LocalJasperService.instance().render(renderData)
                    renderedBytes = PdfCreator.addTestOverlay(renderedBytes)
                } else {
                    renderedBytes = LocalJasperService.instance().render(data.textData)
                }
                writePdfToOutputDir(data.outputName, renderedBytes)
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

    Collection<FormRenderData> rebuildEntriesForRemovedFile(Collection<FormRenderData> renderList, File removedFile) {
        if (!removedFile.name.endsWith(".text.json")) {
            return []
        }

        def pdfDataFile = new File(removedFile.path.replaceFirst(/\.text\.json$/, '.json')).canonicalFile
        return renderList.findAll { it.file.canonicalFile == pdfDataFile }
    }

    String removedOutputName(File removedDataFile) {
        def suffix = removedDataFile.name.endsWith(".text.json") ? ".txt" : ".pdf"
        return "${relativeToDataDir(removedDataFile)}${suffix}"

    }

}
