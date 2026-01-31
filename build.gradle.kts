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

    implementation("org.ktorm:ktorm-core:4.1.1")
    implementation("org.ktorm:ktorm-support-postgresql:4.1.1")
    implementation("org.postgresql:postgresql:42.7.9")
    implementation("com.pgvector:pgvector:0.1.6")

    implementation("com.google.genai:google-genai:1.36.0")

    implementation("org.apache.lucene:lucene-core:10.3.2")
    implementation("org.apache.lucene:lucene-queryparser:10.3.2")
    implementation("org.apache.lucene:lucene-analysis-icu:10.3.2")
    implementation("org.apache.lucene:lucene-analysis-common:10.3.2")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}