plugins {
    kotlin("jvm") version "2.3.0"
}

group = "info.skyblond"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("dev.langchain4j:langchain4j-google-ai-gemini:1.10.0")
    implementation("dev.langchain4j:langchain4j-open-ai:1.10.0")
    implementation("dev.langchain4j:langchain4j:1.10.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}