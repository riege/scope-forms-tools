/*
 * Copyright (c) 2022 Riege Software. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.scope.gradle.tasks

import net.sf.jasperreports.engine.JRException
import net.sf.jasperreports.engine.JasperCompileManager
import net.sf.jasperreports.engine.SimpleJasperReportsContext
import net.sf.jasperreports.engine.design.JRCompiler
import net.sf.jasperreports.engine.xml.JRReportSaxParserFactory
import org.gradle.api.DefaultTask
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.*
import org.gradle.api.tasks.incremental.IncrementalTaskInputs

import java.awt.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.RecursiveAction

/**
 * JasperReports compile task.
 */
class JasperReportsCompile extends DefaultTask {

    @InputDirectory
    File srcDir

    @OutputDirectory
    File outDir

    @Input
    String srcExt = '.jrxml'

    @Input
    String outExt = '.jasper'

    boolean verbose = false

    Logger log = getLogger()

    protected ClassLoader cachingClassLoader

    @TaskAction
    def execute(IncrementalTaskInputs inputs) {
        // Disable logging for JasperReports
        // TODO: this does not work as Gradle uses its own logging
        //java.util.logging.Logger.getLogger("net.sf.jasperreports")
        //        .setLevel(Level.SEVERE)

        // Pre-loads AWT classes to avoid strange deadlock in the JVM.
        def color = new Color(0)
        log.debug("Loaded colors {}", (Object) color)

        cachingClassLoader = new CachingClassLoader(getClass().classLoader)

        if (!outDir.exists()) {
            outDir.mkdirs()
        }

        def jasperReportsContext = new SimpleJasperReportsContext()

        jasperReportsContext.setProperty(JRCompiler.COMPILER_KEEP_JAVA_FILE,
            String.valueOf(false))

        jasperReportsContext.setProperty(JRCompiler.COMPILER_CLASS,
            'net.sf.jasperreports.engine.design.JRJdtCompiler')

        jasperReportsContext.setProperty(
            JRReportSaxParserFactory.COMPILER_XML_VALIDATION,
            String.valueOf(false))

        def manager = JasperCompileManager.getInstance(jasperReportsContext)

        def pool = new ForkJoinPool(Runtime.runtime.availableProcessors())

        def findFormsTask = new FindFormsTask(manager, inputs)
        pool.execute(findFormsTask)
        findFormsTask.join()
    }

    class FindFormsTask extends RecursiveAction {

        private final JasperCompileManager manager
        private final IncrementalTaskInputs inputs

        FindFormsTask(JasperCompileManager manager, IncrementalTaskInputs inputs) {
            this.manager = manager
            this.inputs = inputs
        }

        @Override
        protected void compute() {
            def compilationTasks = []
            inputs.outOfDate { change ->
                if (change.file.name.endsWith(srcExt)) {
                    if (verbose) {
                        log.lifecycle "Found form ${change.file.name}"
                    }
                    def compileTask = new CompileFormTask(manager, change.file, toCompiledForm(change.file))
                    compileTask.fork()
                    compilationTasks << compileTask
                }
            }
            inputs.removed { change ->
                if (verbose) {
                    log.lifecycle "Removed file ${change.file.name}"
                }
                def fileToRemove = toCompiledForm(change.file)
                fileToRemove.delete()
            }
            compilationTasks.each { CompileFormTask task ->
                task.join()
            }
        }

        private File toCompiledForm(File src) {
            def form = src.absolutePath.replace(srcExt, outExt).substring(srcDir.absolutePath.length())
            def formPath = outDir.absolutePath
            if (!formPath.endsWith(File.separator)) {
                formPath += File.separator
            }
            formPath += form
            new File(formPath)
        }

    }

    class CompileFormTask extends RecursiveAction {

        private final JasperCompileManager manager
        private final File sourceForm
        private final File compiledForm

        CompileFormTask(JasperCompileManager manager, File sourceForm,
                        File compiledForm)
        {
            this.manager = manager
            this.sourceForm = sourceForm
            this.compiledForm = compiledForm
        }

        @Override
        protected void compute() {
            Thread.currentThread().setContextClassLoader(cachingClassLoader)
            if (verbose) {
                log.lifecycle "Compiling form ${sourceForm}"
            }
            try {
                def destFileParent = compiledForm.getParentFile()
                if (!destFileParent.exists()) {
                    destFileParent.mkdirs()
                }
                manager.compileToFile(sourceForm.absolutePath, compiledForm.absolutePath)
            } catch (JRException e) {
                log.lifecycle("Compiling report design '" + sourceForm.absolutePath
                        + "' failed due to:\n" + e.getMessage())
                throw new TaskExecutionException(JasperReportsCompile.this, e)
            }
        }

    }

    private static final class CachingClassLoader extends ClassLoader {

        private static final URL NULL_URL

        static {
            try {
                NULL_URL = new URL("http://localhost")
            } catch (MalformedURLException e) {
                throw new Error(e)
            }
        }

        private final ConcurrentHashMap<String, URL> resources

        CachingClassLoader(ClassLoader parent) {
            super(parent)
            this.resources = new ConcurrentHashMap<>(208)
        }

        @Override
        URL getResource(String name) {
            URL url = resources.get(name)
            if (url == null) {
                url = super.getResource(name)
                resources.putIfAbsent(name, url != null ? url : NULL_URL)
            }
            return url != NULL_URL ? url : null
        }

        @Override
        InputStream getResourceAsStream(String name) {
            URL url = resources.get(name)
            if (url == NULL_URL) {
                return null
            }
            return super.getResourceAsStream(name)
        }

    }

}
