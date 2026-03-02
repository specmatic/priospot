package io.github.priospot.gradle

import io.github.priospot.engine.PriospotConfig
import io.github.priospot.engine.PriospotEngine
import java.io.File
import java.nio.file.Path
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class PriospotTask : DefaultTask() {
    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    @get:Optional
    abstract val projectVersion: Property<String>

    @get:Input
    abstract val sourceRoots: ListProperty<String>

    @get:Input
    abstract val coverageReports: ListProperty<String>

    @get:Input
    abstract val complexityReports: ListProperty<String>

    @get:Input
    abstract val churnDays: Property<Int>

    @get:InputFile
    @get:Optional
    abstract val churnLog: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val emitCompatibilityXml: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val deterministicTimestamp: Property<String>

    @TaskAction
    fun run() {
        val base =
            project.layout.projectDirectory.asFile
                .toPath()
        val resolvedSourceRoots =
            if (sourceRoots.get().isEmpty()) {
                discoverSourceRoots(project)
            } else {
                sourceRoots.get().map { base.resolve(it) }
            }
        val resolvedCoverageReports =
            if (coverageReports.get().isEmpty()) {
                discoverExistingReports(project, "build/reports/kover/report.xml")
            } else {
                coverageReports.get().map { base.resolve(it) }
            }
        val resolvedComplexityReports =
            if (complexityReports.get().isEmpty()) {
                discoverExistingReports(project, "build/reports/detekt/detekt.xml")
            } else {
                complexityReports.get().map { base.resolve(it) }
            }

        val config =
            PriospotConfig(
                projectName = projectName.get(),
                projectVersion = projectVersion.orNull,
                sourceRoots = resolvedSourceRoots,
                coverageReports = resolvedCoverageReports,
                complexityReports = resolvedComplexityReports,
                churnDays = churnDays.get(),
                churnLog = churnLog.orNull?.asFile?.toPath(),
                outputDir = outputDir.get().asFile.toPath(),
                emitCompatibilityXml = emitCompatibilityXml.get(),
                deterministicTimestamp = deterministicTimestamp.orNull,
                basePath = base,
            )

        val result = PriospotEngine().run(config)
        logger.lifecycle("PrioSpot completed: {}", result.priospotJson)
        result.summary.forEach { (k, v) -> logger.lifecycle("{}={}", k, v) }
        result.diagnostics.forEach { logger.warn(it) }
    }

    private fun discoverSourceRoots(project: Project): List<Path> = project.subprojects
        .flatMap { subproject ->
            listOf(
                subproject.file("src/main/kotlin"),
                subproject.file("src/test/kotlin"),
            )
        }.filter(File::exists)
        .map { it.toPath() }
        .distinct()

    private fun discoverExistingReports(project: Project, relativePath: String): List<Path> = project.subprojects
        .map { it.file(relativePath) }
        .filter(File::exists)
        .map { it.toPath() }
        .distinct()
}
