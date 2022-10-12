/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.scope.gradle.forms

import org.gradle.api.tasks.incremental.InputFileDetails
import spock.lang.Specification

class FormRenderDataCacheSpec extends Specification {

    def file1 = new File("file1")
    def file2 = new File("file2")
    def data1 = new FormRenderData()
    def data2 = new FormRenderData()
    def testFiles = { file ->
        if (file == file1) return data1
        if (file == file2) return data2
        throw new FileNotFoundException("Not found: ${file}")
    }
    def cache = new FormRenderDataCache()

    def "cache loads file via closure"() {
        expect:
        cache.get(file1, testFiles) == data1
    }

    def "cache loads only once"() {
        setup:
        cache.get(file1, testFiles)
        when:
        def cachedVal = cache.get(file1, null)
        then:
        noExceptionThrown()
        cachedVal == data1
    }

    def "invalidate clears cache"() {
        setup:
        cache.get(file1, testFiles)
        cache.get(file2, testFiles)
        // change data
        data1 = new FormRenderData()
        data2 = new FormRenderData()
        when:
        cache.invalidate()
        then:
        cache.get(file1, testFiles) == data1
        cache.get(file2, testFiles) == data2
    }

    def "invalidate by dependency"() {
        setup:
        def dep1 = new File("dep1")
        def dep2 = new File("dep2")
        data1.addDependency(dep1)
        data2.addDependency(dep2)
        cache.get(file1, testFiles)
        cache.get(file2, testFiles)
        // change data
        data1 = new FormRenderData()
        def oldData2 = data2
        data2 = new FormRenderData()
        when:
        cache.invalidate([[file: dep1]] as List<InputFileDetails>)
        then:
        cache.get(file1, testFiles) == data1
        cache.get(file2, testFiles) == oldData2
    }
}
