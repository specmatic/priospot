import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("io.specmatic.gradle")
    id("org.jetbrains.kotlinx.kover") version "0.8.3" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
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

specmatic {
    kotlinVersion = "2.3.10"
    kotlinApiVersion = KotlinVersion.KOTLIN_2_3

    releasePublishTasks =
        listOf(
            "gradle-plugin:publishPlugins",
            "publishAllPublicationsToMavenCentralRepository"
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
        }
    }

    withOSSApplication(project(":cli")) {
        mainClass = "io.github.priospot.cli.MainKt"
        publishToMavenCentral()
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
