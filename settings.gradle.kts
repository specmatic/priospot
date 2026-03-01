pluginManagement {
    val specmaticGradlePluginVersion = settings.extra["specmaticGradlePluginVersion"] as String
    plugins {
        id("io.specmatic.gradle") version (specmaticGradlePluginVersion)
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal {
            mavenContent {
                snapshotsOnly()
            }

            content {
                includeGroup("io.specmatic.gradle")
            }
        }

    }
}

rootProject.name = "priospot"

include(
    "model",
    "ingest-source",
    "ingest-churn",
    "ingest-coverage",
    "ingest-complexity",
    "compute-c3",
    "report-svg",
    "engine",
    "gradle-plugin",
    "cli",
    "compat-xml"
)
