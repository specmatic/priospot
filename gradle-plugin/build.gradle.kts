plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

dependencies {
    implementation(gradleApi())
    implementation(project(":engine"))
    implementation(project(":model"))
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("priospotPlugin") {
            id = "io.specmatic.priospot"
            implementationClass = "io.github.priospot.gradle.PriospotPlugin"
            displayName = "PrioSpot"
            description = "Computes C3 hotspots and treemap reports"
        }
    }
}

tasks.test { useJUnitPlatform() }
