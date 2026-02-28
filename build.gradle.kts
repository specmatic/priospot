import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    kotlin("jvm") version "2.0.21" apply false
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
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(17)
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
