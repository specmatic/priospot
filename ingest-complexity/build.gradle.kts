plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":model"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.3")

    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.28.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.3")
}
