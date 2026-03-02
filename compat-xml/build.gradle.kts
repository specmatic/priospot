plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":model"))

    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.28.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.14.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.14.3")
}
