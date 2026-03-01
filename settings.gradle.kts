pluginManagement {
    val specmaticGradlePluginVersion = settings.providers.gradleProperty("specmaticGradlePluginVersion").get()
    val priospotPluginVersion = settings.providers.gradleProperty("priospotPluginVersion").get()

    plugins {
        id("io.specmatic.gradle") version (specmaticGradlePluginVersion)
        id("io.specmatic.priospot") version (priospotPluginVersion)
    }

    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots")
            mavenContent {
                snapshotsOnly()
            }

            content {
                includeGroupAndSubgroups("io.specmatic")
            }
        }
        mavenLocal {
            mavenContent {
                snapshotsOnly()
            }
            content {
                includeGroupAndSubgroups("io.specmatic")
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
    "compat-xml",
)
