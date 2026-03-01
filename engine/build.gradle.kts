plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":model"))
    implementation(project(":ingest-source"))
    implementation(project(":ingest-churn"))
    implementation(project(":ingest-coverage"))
    implementation(project(":ingest-complexity"))
    implementation(project(":compute-c3"))
    implementation(project(":report-svg"))
    implementation(project(":compat-xml"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.1")

    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.28.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}
