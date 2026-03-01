package io.github.priospot.gradle

import io.github.priospot.engine.PriospotConfig
import io.github.priospot.engine.PriospotEngine
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

abstract class PriospotExtension
    @Inject
    constructor(objects: ObjectFactory) {
        val projectName: Property<String> = objects.property(String::class.java)
        val projectVersion: Property<String> = objects.property(String::class.java)
        val baseNamespace: Property<String> = objects.property(String::class.java)
        val sourceRoots: ListProperty<String> = objects.listProperty(String::class.java)
        val coverageReports: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
        val complexityReports: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
        val churnDays: Property<Int> = objects.property(Int::class.java).convention(30)
        val churnLog: RegularFileProperty = objects.fileProperty()
        val outputDir: DirectoryProperty = objects.directoryProperty()
        val emitCompatibilityXml: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
        val deterministicTimestamp: Property<String> = objects.property(String::class.java)
        val coverageTask: Property<String> = objects.property(String::class.java)
        val complexityTask: Property<String> = objects.property(String::class.java)
    }

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
                basePath = base
            )

        val result = PriospotEngine().run(config)
        logger.lifecycle("PrioSpot completed: {}", result.priospotJson)
        result.summary.forEach { (k, v) -> logger.lifecycle("{}={}", k, v) }
        result.diagnostics.forEach { logger.warn(it) }
    }

    private fun discoverSourceRoots(project: Project): List<java.nio.file.Path> = project.subprojects
        .flatMap { subproject ->
            listOf(
                subproject.file("src/main/kotlin"),
                subproject.file("src/test/kotlin")
            )
        }.filter(File::exists)
        .map { it.toPath() }
        .distinct()

    private fun discoverExistingReports(project: Project, relativePath: String): List<java.nio.file.Path> = project.subprojects
        .map { it.file(relativePath) }
        .filter(File::exists)
        .map { it.toPath() }
        .distinct()
}

class PriospotPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("priospot", PriospotExtension::class.java)

        val task: TaskProvider<PriospotTask> =
            project.tasks.register("priospot", PriospotTask::class.java) {
                group = "verification"
                description = "Computes C3 hotspots and generates PrioSpot reports"

                projectName.set(extension.projectName.orElse(project.name))
                projectVersion.set(extension.projectVersion.orElse(project.version.toString()))
                sourceRoots.set(extension.sourceRoots.orElse(emptyList()))
                coverageReports.set(extension.coverageReports)
                complexityReports.set(extension.complexityReports)
                churnDays.set(extension.churnDays)
                churnLog.set(extension.churnLog)
                outputDir.set(extension.outputDir.orElse(project.layout.buildDirectory.dir("reports/priospot")))
                emitCompatibilityXml.set(extension.emitCompatibilityXml)
                deterministicTimestamp.set(extension.deterministicTimestamp)
            }

        project.afterEvaluate {
            extension.coverageTask.orNull?.let { coverageTaskName ->
                task.configure { dependsOn(project.tasks.named(coverageTaskName)) }
            }
            extension.complexityTask.orNull?.let { complexityTaskName ->
                task.configure { dependsOn(project.tasks.named(complexityTaskName)) }
            }
        }
    }
}
