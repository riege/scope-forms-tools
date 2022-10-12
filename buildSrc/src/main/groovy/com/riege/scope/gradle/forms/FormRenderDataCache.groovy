/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.scope.gradle.forms

import org.gradle.api.tasks.incremental.InputFileDetails

class FormRenderDataCache {

    private Map<String, FormRenderData> cache = [:]

    void invalidate(List<InputFileDetails> dirtyFiles) {
        dirtyFiles.each { change ->
            def it = cache.entrySet().iterator()
            it.forEachRemaining { entry ->
                if (entry.value.hasDependency(change.file)) {
                    it.remove()
                }
            }
        }
    }

    void invalidate() {
        cache.clear()
    }

    FormRenderData get(File file, Closure<FormRenderData> load) {
        def fullPath = file.getCanonicalPath()
        if (!cache.containsKey(fullPath)) {
            cache.put(fullPath, load(file))
        }
        return cache.get(fullPath)
    }

}
