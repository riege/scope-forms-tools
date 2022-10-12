/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.scope.gradle.forms

import spock.lang.Specification

import java.nio.file.Paths

class FormPathSpec extends Specification {

    void "paths are absolute"() {
        setup:
        def path = new FormPath(Paths.get("src/forms"), Paths.get("build/output/forms"))

        expect:
        path.src.isAbsolute()
        path.out.isAbsolute()
    }
}
