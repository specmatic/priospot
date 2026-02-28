plugins { kotlin("jvm") }

dependencies {
    implementation(project(":model"))
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
