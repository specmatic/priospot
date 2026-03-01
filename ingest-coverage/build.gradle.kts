plugins { kotlin("jvm") }

dependencies {
    implementation(project(":model"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.1")
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
