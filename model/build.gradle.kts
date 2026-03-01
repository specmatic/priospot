plugins {
    kotlin("jvm")
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.21")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.1")
    testImplementation(kotlin("test"))
}
