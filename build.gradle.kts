import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("io.specmatic.gradle")
    id("io.specmatic.priospot")
    id("org.jetbrains.kotlinx.kover") version "0.9.7"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        apply(plugin = "org.jetbrains.kotlinx.kover")
        apply(plugin = "io.gitlab.arturbosch.detekt")
        configure<DetektExtension> {
            buildUponDefaultConfig = true
            allRules = false
            ignoreFailures = true
            parallel = true
        }
    }

    tasks.withType<Test> {
        maxParallelForks = 4
    }
}

specmatic {
    kotlinVersion = "2.3.10"
    kotlinApiVersion = KotlinVersion.KOTLIN_2_3

    releasePublishTasks =
        listOf(
            "gradle-plugin:publishPlugins",
            "publishAllPublicationsToMavenCentralRepository",
        )

    listOf(
        project(":model"),
        project(":ingest-source"),
        project(":ingest-churn"),
        project(":ingest-coverage"),
        project(":ingest-complexity"),
        project(":compute-c3"),
        project(":report-svg"),
        project(":engine"),
        project(":compat-xml"),
    ).forEach { moduleProject ->
        withOSSLibrary(moduleProject) {
            publishToMavenCentral()
            githubRelease()

            publish {
                pom {
                    name.set("PrioSpot - ${moduleProject.name} Library")
                    description.set("PrioSpot - ${moduleProject.name} module (for internal use by priospot)")
                    url.set("https://github.com/specmatic/priospot")
                    licenses {
                        license {
                            name.set("MIT")
                            url.set("https://opensource.org/license/mit")
                        }
                    }

                    developers {
                        developer {
                            id = "specmaticBuilders"
                            name = "Specmatic Builders"
                            email = "info@specmatic.io"
                        }
                    }
                    scm {
                        connection = "https://github.com/specmatic/priospot"
                        url = "https://github.com/specmatic/priospot"
                    }
                }
            }
        }
    }

    withOSSApplication(project(":cli")) {
        mainClass = "io.github.priospot.cli.MainKt"
        publishToMavenCentral()
        githubRelease {
            addFile("unobfuscatedShadowJar", "priospot.jar")
        }
        dockerBuild {
            imageName = "priospot"
        }

        publish {
            pom {
                name.set("PrioSpot - CLI")
                description.set("PrioSpot - CLI application")
                url.set("https://github.com/specmatic/priospot")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/license/mit")
                    }
                }

                developers {
                    developer {
                        id = "specmaticBuilders"
                        name = "Specmatic Builders"
                        email = "info@specmatic.io"
                    }
                }
                scm {
                    connection = "https://github.com/specmatic/priospot"
                    url = "https://github.com/specmatic/priospot"
                }
            }
        }
    }

    withOSSLibrary(project(":gradle-plugin")) {
        publishToMavenCentral()
        githubRelease()

        publishGradle {
            pom {
                name = "PrioSpot Gradle Plugin"
                description = "Computes C3 hotspots and treemap reports"
                url = "https://github.com/specmatic/priospot"
                licenses {
                    license {
                        name = "MIT"
                        url = "https://opensource.org/license/mit"
                    }
                }
                developers {
                    developer {
                        id = "specmaticBuilders"
                        name = "Specmatic Builders"
                        email = "info@specmatic.io"
                    }
                }
                scm {
                    connection = "https://github.com/specmatic/priospot"
                    url = "https://github.com/specmatic/priospot"
                }
            }
        }
    }
}
