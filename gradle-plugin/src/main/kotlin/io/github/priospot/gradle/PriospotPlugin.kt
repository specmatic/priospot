package io.github.priospot.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.*

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
