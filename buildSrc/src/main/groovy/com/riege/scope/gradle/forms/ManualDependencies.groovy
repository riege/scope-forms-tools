/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.scope.gradle.forms

import java.nio.file.Path
import java.nio.file.Paths

class ManualDependencies {

    private static Map<Path, List<Path>> dependencies

    static def getDEPENDENCIES() {
        if (dependencies == null) {
            init()
        }
        return dependencies
    }

    private static def init() {
        dependencies = [:]
        ManualDependencies.class.getResource("dependencies.txt").text
            .readLines()
            .findAll { !it.startsWith("#") && !it.isAllWhitespace() }
            .each { line ->
                def (key, value) = line.split("->", 2).collect { it.trim() }
                key = Paths.get(key)
                if (!dependencies.containsKey(key)) {
                    dependencies[key] = []
                }
                dependencies[key].add(Paths.get(value))
            }
    }
}
