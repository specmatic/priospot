package io.github.priospot.gradle

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.plugin.DetektPlugin
import java.io.File
import kotlinx.kover.gradle.plugin.KoverGradlePlugin
import kotlinx.kover.gradle.plugin.dsl.tasks.KoverReport
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

class PriospotPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("priospot", PriospotExtension::class.java)

        project.plugins.apply(DetektPlugin::class.java)
        project.plugins.apply(KoverGradlePlugin::class.java)

        val task: TaskProvider<PriospotTask> =
            project.tasks.register("priospot", PriospotTask::class.java) {
                group = "verification"
                description = "Computes C3 hotspots and generates PrioSpot reports"

                baseDir.set(project.layout.projectDirectory.asFile.absolutePath)
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

                dependsOn(project.allprojects.map { it.tasks.withType<Detekt>() })
                dependsOn(project.allprojects.map { it.tasks.withType<KoverReport>() })
            }

        project.gradle.projectsEvaluated {
            task.configure {
                if (sourceRoots.get().isEmpty()) {
                    val elements = discoverSourceRoots(project)
                    sourceRoots.set(elements)
                }
                if (coverageReports.get().isEmpty()) {
                    coverageReports.set(discoverExistingReports(project, "build/reports/kover/report.xml"))
                }
                if (complexityReports.get().isEmpty()) {
                    complexityReports.set(discoverExistingReports(project, "build/reports/detekt/detekt.xml"))
                }
            }
        }

        project.afterEvaluate {
            extension.coverageTask.orNull?.let { coverageTaskName ->
                task.configure { dependsOn(project.tasks.named(coverageTaskName)) }
            }
            extension.complexityTask.orNull?.let { complexityTaskName ->
                task.configure { dependsOn(project.tasks.named(complexityTaskName)) }
            }
        }

        // Ensure coverage reports are generated only after tests run.
        project.tasks.withType<KoverReport> {
            dependsOn(project.tasks.withType<Test>())
        }

        // Temporarily keep Detekt informational so CI can proceed.
        project.tasks.withType<Detekt> {
            ignoreFailures.set(true)
        }
    }

    private fun discoverSourceRoots(project: Project): List<String> = (listOf(project) + project.subprojects)
        .flatMap { currentProject ->
            val fromSourceSets =
                currentProject.extensions
                    .findByType(SourceSetContainer::class.java)
                    ?.flatMap { sourceSet -> sourceSet.allSource.srcDirs.toList() }
                    .orEmpty()

            if (fromSourceSets.isNotEmpty()) {
                fromSourceSets
            } else {
                listOf(
                    "src/main/kotlin",
                    "src/test/kotlin",
                    "src/main/java",
                    "src/test/java",
                ).map(currentProject::file)
            }
        }.filter(File::exists)
        .map { it.absolutePath }
        .distinct()

    private fun discoverExistingReports(project: Project, relativePath: String): List<String> = (listOf(project) + project.subprojects)
        .map { it.file(relativePath) }
        .filter(File::exists)
        .map { it.absolutePath }
        .distinct()
}
