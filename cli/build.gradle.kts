plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":model"))
    implementation(project(":report-svg"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("io.github.priospot.cli.MainKt")
}

tasks.test { useJUnitPlatform() }
