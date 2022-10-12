/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.scope.gradle.forms

import spock.lang.Specification

import java.nio.file.FileSystems
import java.nio.file.Paths

class FormSpec extends Specification {

    def base = new FormPath(
        normalize_path("/src"),
        normalize_path("/out"))

    /**
     * Makes tests run on Windows as well
     * @param path a path given as string
     * @return a platform-specific absolute path if {@code path} starts with "/",
     *         or a relative path otherwise
     */
    def normalize_path(String path) {
        if (path.startsWith("/")) {
            return FileSystems.default.getPath(path).toAbsolutePath()
        }
        return Paths.get(path)
    }

    void "Smart constructor"() {
        expect:
        new Form(base, normalize_path(PATH)).path == Paths.get(FORM)
        where:
        PATH | FORM
        "myform" | "myform"
        "my.form" | "my.form"
        "my form" | "my form"

        "/src/my.form.jrxml" | "my.form"
        "/src/my form.jrxml" | "my form"
        "myform.jrxml" | "myform"
        "my.form.jrxml" | "my.form"
        "my form.jrxml" | "my form"
        "/src/my.form.jrxml" | "my.form"
        "/src/my form.jrxml" | "my form"

        "myform.jasper" | "myform"
        "my.form.jasper" | "my.form"
        "my form.jasper" | "my form"
        "/out/my.form.jasper" | "my.form"
        "/out/my form.jasper" | "my form"
    }

    void "Smart constructor rejects wrong path"() {
        when:
        new Form(base, normalize_path(PATH))
        then:
        thrown(IllegalArgumentException)
        where:
        PATH | DUMMY
        "/src/my.form.jasper" | ""
        "/src/my form.jasper" | ""
        "/out/my.form.jrxml" | ""
        "/out/my form.jrxml" | ""
    }

    void "path is relative"() {
        expect:
        new Form(base, normalize_path("relative/to")).path == Paths.get("relative/to")
        new Form(base, normalize_path("/src/form")).path == Paths.get("form")
        new Form(base, normalize_path("/out/form")).path == Paths.get("form")
    }

    void "src/out path"() {
        expect:
        new Form(base, Paths.get(NAME)).src == normalize_path(SRC)
        new Form(base, Paths.get(NAME)).out == normalize_path(OUT)
        where:
        NAME | SRC | OUT
        "myform" | "/src/myform.jrxml"| "/out/myform.jasper"
        "sub/a/form" | "/src/sub/a/form.jrxml"| "/out/sub/a/form.jasper"
    }

    void "relative src/out path"() {
        expect:
        new Form(base, Paths.get(NAME)).relativeSrc == Paths.get(SRC)
        new Form(base, Paths.get(NAME)).relativeOut == Paths.get(OUT)
        where:
        NAME | SRC | OUT
        "myform" | "myform.jrxml"| "myform.jasper"
        "sub/a/form" | "sub/a/form.jrxml"| "sub/a/form.jasper"
    }

    void "localized version"() {
        setup:
        def de = Locale.GERMAN
        def fr = Locale.FRENCH
        def form = new Form(base, Paths.get("path/to/form"))

        expect:
        form.localize(null).path == Paths.get("path/to/form")
        form.localize(de).path == Paths.get("path/to/form_de")
        form.localize(fr).path == Paths.get("path/to/form_fr")
    }
}
