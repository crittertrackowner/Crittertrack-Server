plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
    id("com.google.cloud.tools.jib") version "3.4.0"
}

group = "com.example"
version = "0.0.1"

application {
    mainClass = "com.example.ApplicationKt"
}
kotlin {
jvmToolchain(17)}

dependencies {
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.config.yaml)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
    implementation("org.mindrot:jbcrypt:0.4")
    // Kotlinx Serialization Dependencies
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")       // Added version
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.6.0")    // Added version
    implementation("io.ktor:ktor-serialization-kotlinx-json") // Ktor-specific serializer

    // Database
    implementation("org.jetbrains.exposed:exposed-core:0.41.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")
    // ✅ FIX: ADDED exposed-java-time to enable date() function for LocalDate
    implementation("org.jetbrains.exposed:exposed-java-time:0.41.1") 
    implementation("org.postgresql:postgresql:42.5.0")
    implementation("com.zaxxer:HikariCP:5.0.1")

    // Firebase
    implementation("com.google.firebase:firebase-admin:9.2.0")
}