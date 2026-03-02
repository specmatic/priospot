plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":model"))
    implementation(project(":report-svg"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.1")
    implementation("info.picocli:picocli:4.7.7")

    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.28.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.14.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.14.3")
}
