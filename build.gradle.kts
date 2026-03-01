import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.JavaExec
import java.io.File
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.Detekt

plugins {
    base
    kotlin("jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlinx.kover") version "0.8.3" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

allprojects {
    group = "io.specmatic.priospot"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "maven-publish")

    plugins.withId("org.jetbrains.kotlin.jvm") {
        apply(plugin = "org.jetbrains.kotlinx.kover")
        apply(plugin = "io.gitlab.arturbosch.detekt")

        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(17)
        }

        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            allRules = false
            ignoreFailures = true
            config.setFrom(rootProject.file("config/detekt-priospot.yml"))
            source.setFrom(
                files(
                    "src/main/kotlin",
                    "src/test/kotlin"
                )
            )
        }

        tasks.withType<Detekt>().configureEach {
            reports {
                xml.required.set(true)
                xml.outputLocation.set(file("${project.layout.buildDirectory.get()}/reports/detekt/detekt.xml"))
                html.required.set(false)
                sarif.required.set(false)
                md.required.set(false)
                txt.required.set(false)
            }
        }
    }

    plugins.withType<JavaPlugin> {
        if (!plugins.hasPlugin("java-gradle-plugin")) {
            extensions.configure<PublishingExtension> {
                publications {
                    if (findByName("mavenJava") == null) {
                        create<MavenPublication>("mavenJava") {
                            from(components["java"])
                        }
                    }
                }
            }
        }
    }
}

tasks.register("publishToMavenLocal") {
    group = "publishing"
    description = "Publishes all PrioSpot artifacts needed by consumers to Maven Local"
    dependsOn(subprojects.map { "${it.path}:publishToMavenLocal" })
}

tasks.register<JavaExec>("priospot") {
    group = "verification"
    description = "Runs PrioSpot analysis for this repository and generates reports"
    dependsOn(":cli:classes")

    val repoRoot = rootDir.toPath()
    val sourceRootsProvider = provider {
        subprojects
            .flatMap { subproject ->
                listOf(
                    File(subproject.projectDir, "src/main/kotlin"),
                    File(subproject.projectDir, "src/test/kotlin")
                )
            }
            .filter(File::exists)
            .joinToString(",") { repoRoot.relativize(it.toPath()).toString().replace(File.separatorChar, '/') }
    }
    val discoveredCoverageReports = provider {
        subprojects
            .map { File(it.projectDir, "build/reports/kover/report.xml") }
            .filter(File::exists)
            .map { repoRoot.relativize(it.toPath()).toString().replace(File.separatorChar, '/') }
    }
    val discoveredComplexityReports = provider {
        subprojects
            .map { File(it.projectDir, "build/reports/detekt/detekt.xml") }
            .filter(File::exists)
            .map { repoRoot.relativize(it.toPath()).toString().replace(File.separatorChar, '/') }
    }

    mainClass.set("io.github.priospot.cli.MainKt")
    classpath = project(":cli").extensions.getByType<SourceSetContainer>().named("main").get().runtimeClasspath
    dependsOn(
        subprojects
            .filter { it.plugins.hasPlugin("org.jetbrains.kotlin.jvm") }
            .map { "${it.path}:koverXmlReport" }
    )
    dependsOn(
        subprojects
            .filter { it.plugins.hasPlugin("org.jetbrains.kotlin.jvm") }
            .map { "${it.path}:detekt" }
    )
    doFirst {
        val coverageReports = discoveredCoverageReports.get()
        val complexityReports = discoveredComplexityReports.get()
        args = listOf(
            "analyze",
            "--project-name", rootProject.name,
            "--project-version", version.toString(),
            "--source-roots", sourceRootsProvider.get(),
            "--coverage-reports", coverageReports.joinToString(","),
            "--complexity-reports", complexityReports.joinToString(","),
            "--churn-days", "30",
            "--output-json", layout.buildDirectory.file("reports/priospot/priospot.json").get().asFile.path
        )
    }
}
