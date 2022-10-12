/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.scope.gradle.forms


import java.nio.file.Path
import java.nio.file.Paths

class Form {

    FormPath basePath
    Path path

    Form(FormPath base, String path) {
        this(base, Paths.get(path))
    }

    Form(FormPath base, Path path) {
        basePath = base
        path = stripExtension(base.src, path, ".jrxml")
        path = stripExtension(base.out, path, ".jasper")
        path = base.relativize(path)
        this.path = path
    }
    static Path stripExtension(Path base, Path path, String ext) {
        if (path.toString().endsWith(ext)) {
            if (path.isAbsolute() && !path.startsWith(base)) {
                throw new IllegalArgumentException("Absolute path ends with ${ext} but is not in ${base}")
            }
            path = path.resolveSibling(path.fileName.toString().replaceFirst("${ext}\$", ""))
        }
        path
    }

    def getSrc() {
        basePath.src.resolve(relativeSrc)
    }

    def getOut() {
        basePath.out.resolve(relativeOut)
    }

    def getRelativeSrc() {
        Paths.get(path.toString() + ".jrxml")
    }

    def getRelativeOut() {
        Paths.get(path.toString() + ".jasper")
    }

    def localize(Locale locale) {
        if (locale == null) {
            return this
        }
        def localizedPath = Paths.get("${path.toString()}_${locale.getLanguage()}")
        new Form(basePath, localizedPath)
    }

}
