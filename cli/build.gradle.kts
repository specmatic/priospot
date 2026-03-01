plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":model"))
    implementation(project(":report-svg"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("info.picocli:picocli:4.7.7")
    testImplementation(kotlin("test"))
}
