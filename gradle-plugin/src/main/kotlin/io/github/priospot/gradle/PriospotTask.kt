package io.github.priospot.gradle

import io.github.priospot.engine.PriospotConfig
import io.github.priospot.engine.PriospotEngine
import java.nio.file.Path
import java.nio.file.Paths
import org.gradle.api.DefaultTask
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
    abstract val baseDir: Property<String>

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
        val base = Paths.get(baseDir.get())
        val resolvedSourceRoots = resolvePaths(base, sourceRoots.get())
        val resolvedCoverageReports = resolvePaths(base, coverageReports.get())
        val resolvedComplexityReports = resolvePaths(base, complexityReports.get())

        if (resolvedSourceRoots.isEmpty()) {
            logger.lifecycle("PrioSpot skipped: no source roots configured or discovered")
            return
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

    private fun resolvePaths(base: Path, values: List<String>): List<Path> = values.map { value ->
        Paths.get(value).let { if (it.isAbsolute) it else base.resolve(it) }
    }
}
