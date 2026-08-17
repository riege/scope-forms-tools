/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.scope.gradle.forms

import com.riege.jasperservice.LocalJasperService
import com.riege.jasperservice.backend.FormsLoader
import com.riege.jasperservice.backend.FormsLoader$
import com.riege.jasperservice.model.DocumentContext
import com.riege.jasperservice.model.Image
import com.riege.jasperservice.model.PDFRawData
import com.riege.jasperservice.model.Report
import scala.Option$
import scala.collection.JavaConverters
import scala.collection.immutable.Map$

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class FormRenderDataFactory {

    Path dataDir
    FormPath formPath

    FormRenderData load(File html) {
        load(html.toPath())
    }

    FormRenderData load(Path file) {
        return loadJson(file)
    }

    private FormRenderData loadJson(Path file) {
        LocalJasperService.startUp(formPath.out.toString())
        DocumentContext context
        String formName
        def result = new FormRenderData(
            file: file.toFile(),
            fileName: dataDir.relativize(file),
        )

        if (file.toString().endsWith(".text.json")) {
            result.textData = LocalJasperService.instance().readText(file.toString())
            context = result.textData.context()
            formName = result.textData.formName()
        } else {
            result.jasperServiceData = LocalJasperService.instance().read(file.toString())
            context = result.jasperServiceData.context()
            formName = result.jasperServiceData.formName()

            if (PDFWithTextSupport.isFormSupported(formName)) {
                def textFile = Paths.get(file.toString().replaceFirst("\\.json\$", ".text.json"))
                if(Files.exists(textFile)) {
                    result.textData = LocalJasperService.instance().readText(textFile.toString())
                }
                result.addDependency(textFile)
            }
        }

        def localeOption = Option$.MODULE$.apply(context.locale())
        def loader = new FormsLoader(context, formPath.out.toString(), false)
        def formDirUri = loader
            .getForm(formName, localeOption)
            .getProperty(FormsLoader$.MODULE$.PROPERTY_FORM_DIR())
        def formDir = Paths.get(new URI(formDirUri).normalize())
        result.addDependency(file)

        if (result.jasperServiceData != null) {
            def formFile = loader.getFile(result.jasperServiceData.formName() + ".jasper", localeOption).get().toPath()
            result.addDependency(formFile)
            addFileDependencies(result, formDir, new Form(getFormPath(), formFile), loader)
            addParameterDependencies(result, JavaConverters.mapAsJavaMap(result.jasperServiceData.data()), loader)
        }

        if (result.textData != null) {
            def formFile = loader.getFile(result.textData.formName() + ".jasper", localeOption).get().toPath()
            result.addDependency(formFile)
        }

        return result
    }

    def addFileDependencies(FormRenderData result, Path formDir, Form form, FormsLoader loader = null, Set<Path> visited = []) {
        if (form.relativeSrc in visited) {
            return
        }
        visited.add(form.relativeSrc)
        List<Path> deps = []
        if (form.src in ManualDependencies.DEPENDENCIES) {
            deps += ManualDependencies.DEPENDENCIES[form.src]
        }
        def source = form.src.getText("UTF-8")
        deps += extractFormDirReferences(source).collect { formDir.resolve(it.replaceFirst("^/", "")) }
        if (result.jasperServiceData != null) {
            deps += extractLoadedForms(loader, source).collect { formDir.resolve(it) }
            deps += extractLoadedImages(loader, source).collect { formDir.resolve(it) }
        }
        deps.each { dep ->
            if (dep.toString().endsWith(".jrxml") || dep.toString().endsWith(".jasper")) {
                def subform = new Form(form.basePath, dep)
                result.addDependency(subform.out)
                addFileDependencies(result, formDir, subform, loader, visited)
            } else {
                result.addDependency(formPath.out.resolve(dep))
            }
        }
    }

    def addParameterDependencies(FormRenderData result, Map<String, Object> parameters, FormsLoader loader) {
        parameters.each { k, v ->
            if (v instanceof Image) {
                def img = v as Image
                def imagePath = loader.getImage(img.name(), img.ext(), img.locale())._1
                result.addDependency(new File(imagePath))
            } else if (v instanceof Report) {
                def report = v as Report
                def form = loader.getForm(report.name(), report.language())
                def formPath = form.getProperty(FormsLoader$.MODULE$.PROPERTY_FORM_FILE())
                result.addDependency(new File(formPath))
            }
        }
    }

    static List<String> extractFormDirReferences(String content) {
        def pattern = ~'\\$P\\{formDir\\}\\s*\\+\\s*"([^"]*)"'
        content.findAll(pattern) { match, file ->
            file
        }
    }

    static List<String> extractLoadedForms(FormsLoader loader, String content) {
        def pattern = ~'LOAD_FORM\\("([^)]+)"\\)'
        def locale = Option$.MODULE$.apply(loader.ctx().locale())
        content.findAll(pattern) { match, name ->
            def form = loader.getForm(name, locale)
            def formPath = form.getProperty(FormsLoader$.MODULE$.PROPERTY_FORM_FILE())
            formPath
        }
    }

    static List<String> extractLoadedImages(FormsLoader loader, String content) {
        def pattern = ~'LOAD_IMAGE\\(\\s*"([^"]+)"\\s*(?:,\\s*"([^"]+)")?\\s*\\)'
        def locale = Option$.MODULE$.apply(loader.ctx().locale())
        content.findAll(pattern) { match, String name, String ext ->
            def optionalExt = Option$.MODULE$.apply(ext)
            def result = loader.getImage(name, optionalExt, locale)
            result._1
        }
    }

}
