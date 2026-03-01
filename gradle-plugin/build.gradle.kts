plugins {
    `maven-publish`
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.3.1"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())
    implementation(project(":engine"))
    implementation(project(":model"))

    testImplementation(gradleTestKit())
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.28.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
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
