plugins {
    `maven-publish`
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "2.1.1"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())
    implementation(project(":engine"))
    implementation(project(":model"))
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.8")
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:0.9.8")

    testImplementation(gradleTestKit())
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.28.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.14.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.14.4")
}

gradlePlugin {
    plugins {
        create("priospotPlugin") {
            id = "io.specmatic.priospot"
            implementationClass = "io.github.priospot.gradle.PriospotPlugin"
            displayName = "PrioSpot"
            description = "Computes C3 hotspots and treemap reports"
            tags =
                listOf(
                    "c3",
                    "hotspot",
                    "priospot",
                    "specmatic",
                    "code-quality",
                    "code-complexity",
                    "quality",
                    "complexity",
                    "static-analysis",
                )
        }
    }

    website = "https://github.com/specmatic/priospot"
    vcsUrl = "https://github.com/specmatic/priospot"
}
