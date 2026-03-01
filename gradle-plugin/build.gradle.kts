plugins {
    `maven-publish`
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.3.1"
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
            tags = listOf("c3", "hotspot", "priospot", "specmatic")
        }
    }

    website = "https://github.com/specmatic/priospot"
    vcsUrl = "https://github.com/specmatic/priospot"
}
