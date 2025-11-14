package com.example

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.example.plugins.*

fun main() {
    // 🚨 CRUCIAL CHANGE: Read the PORT from the environment variable 
    // Render (and Heroku) provides the port to use via an environment variable
    val port = System.getenv("PORT")?.toInt() ?: 8080 // Default to 8080 for local testing
    
    // Start the embedded Ktor server
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

// The module function that Ktor uses to configure the application
fun Application.module() {
    // 1. Initialize the database connection
    initDatabase() 
    
    // 2. Configure JWT Authentication
    configureSecurity()
    
    // 3. Configure JSON serialization
    configureSerialization()
    
    // 4. Configure routing and endpoints
    // Calling an extension function on 'Application' (this) implicitly passes the receiver,
    // which resolves the "No value passed for parameter 'app'" error.
    configureRouting()
}