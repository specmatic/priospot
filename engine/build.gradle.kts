plugins { kotlin("jvm") }

dependencies {
    implementation(project(":model"))
    implementation(project(":ingest-source"))
    implementation(project(":ingest-churn"))
    implementation(project(":ingest-coverage"))
    implementation(project(":ingest-complexity"))
    implementation(project(":compute-c3"))
    implementation(project(":report-svg"))
    implementation(project(":compat-xml"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
