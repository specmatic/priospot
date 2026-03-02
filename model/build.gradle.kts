plugins {
    kotlin("jvm")
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.21")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.1")

    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.28.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.14.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.14.3")
}
