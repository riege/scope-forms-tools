package com.riege.scope.gradle.forms

import com.riege.jasperservice.model.PDFRawData

import java.nio.file.Path

class FormRenderData {

    PDFRawData jasperServiceData
    File file
    String fileName
    Set<File> dependencies = new HashSet<>()

    void addDependency(File dep) {
        dependencies << dep.canonicalFile
    }

    void addDependency(Path dep) {
        addDependency(dep.toFile())
    }

    boolean hasDependency(File dep) {
        dependencies.contains(dep.canonicalFile)
    }

    boolean hasDependency(Path dep) {
        hasDependency(dep.toFile())
    }

    String getOutputName() {
        return fileName.concat(".pdf")
    }

}
