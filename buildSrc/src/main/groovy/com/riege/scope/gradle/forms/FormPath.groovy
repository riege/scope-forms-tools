/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.scope.gradle.forms

import java.nio.file.Path

class FormPath {

    Path src
    Path out

    FormPath(Path src, Path out) {
        this.src = src.toAbsolutePath()
        this.out = out.toAbsolutePath()
    }

    Path relativize(Path path) {
        if (path.startsWith(src)) {
            return src.relativize(path)
        }
        if (path.startsWith(out)) {
            return out.relativize(path)
        }
        return path
    }

    String toString() {
        String.format("FormPath(${src}, ${out})")
    }
}
