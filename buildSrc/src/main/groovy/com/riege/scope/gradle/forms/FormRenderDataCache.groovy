/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.scope.gradle.forms

class FormRenderDataCache {

    private Map<String, FormRenderData> cache = [:]

    void invalidate(Collection<File> dirtyFiles) {
        dirtyFiles.each { dirtyFile ->
            def it = cache.entrySet().iterator()
            it.forEachRemaining { entry ->
                if (entry.value.hasDependency(dirtyFile)) {
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
