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
    jvmToolchain(17)
}

dependencies {
    // 🌟 FIX: CORS Dependency (Ktor 2.3.8 is currently the latest stable version)
    implementation("io.ktor:ktor-server-cors-jvm:2.3.8")
    
    // Ktor Dependencies
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.config.yaml)

    // Testing
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)

    // Hashing
    implementation("org.mindrot:jbcrypt:0.4")
    
    // Kotlinx Serialization Dependencies
    // 💡 Note: Only need the first one if libs.ktor.serialization.kotlinx.json is working
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Database
    implementation("org.jetbrains.exposed:exposed-core:0.41.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")
    implementation("org.jetbrains.exposed:exposed-java-time:0.41.1") 
    implementation("org.postgresql:postgresql:42.5.0")
    implementation("com.zaxxer:HikariCP:5.0.1")

// Ktor Client dependencies needed by SupabaseService.kt
implementation("io.ktor:ktor-client-core:\$ktor_version")
implementation("io.ktor:ktor-client-cio:\$ktor_version") // For the CIO HTTP Engine
implementation("io.ktor:ktor-client-content-negotiation:\$ktor_version")
implementation("io.ktor:ktor-serialization-kotlinx-json:\$ktor_version")

// You might also need this import if you don't have it already
implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")

    // Firebase
    implementation("com.google.firebase:firebase-admin:9.2.0")
}
