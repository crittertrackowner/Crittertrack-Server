// fileName: Security.kt (NEW CONTENT for Self-Hosted JWT)
package com.example.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.http.*
import io.ktor.server.response.*
import java.util.*

// --- JWT Configuration Constants ---
// NOTE: These values (especially SECRET) MUST be loaded from application.conf or environment variables 
// in a production app. They are hardcoded here for simplicity.
object TokenConfig {
    const val AUDIENCE = "crittertrack-users"
    const val ISSUER = "http://0.0.0.0:8080/" 
    // This SECRET key is used to sign and verify tokens. It must be strong!
    const val SECRET = "a_very_long_secret_key_for_signing_tokens_4096_bits" 
    const val EXPIRATION_TIME_MS = 3_600_000 // 1 hour
}

// --- Principal Definition ---
// This replaces FirebasePrincipal.
data class JwtPrincipal(val userId: String) : Principal 

// --- Core Security Configuration ---
fun Application.configureSecurity() {
    install(Authentication) {
        // Name the scheme "auth-jwt" for use in routes
        jwt("auth-jwt") {
            realm = "CritterTrack Server"
            
            // 1. Define the Token Validator (Verifier)
            verifier(
                JWT.require(Algorithm.HMAC256(TokenConfig.SECRET))
                    .withAudience(TokenConfig.AUDIENCE)
                    .withIssuer(TokenConfig.ISSUER)
                    .build()
            )
            
            // 2. Define how to extract the Principal from the validated token payload
            validate { credential -> 
                // The 'userId' claim is what we saved in the generateToken function
                val userId = credential.payload.getClaim("userId").asString()
                
                if (userId != null) {
                    // Success: return our new JwtPrincipal
                    JwtPrincipal(userId) 
                } else {
                    null // Validation failure
                }
            }

            // 3. Define the Challenge Response (401 Unauthorized)
            challenge { _, _ -> 
                call.respond(HttpStatusCode.Unauthorized, "JWT Token is invalid, missing, or expired.")
            }
        }
    }
}

// --- Token Generation Utility ---
// This function will be called in the login route to issue a token.
fun generateToken(userId: String): String {
    return JWT.create()
        .withAudience(TokenConfig.AUDIENCE)
        .withIssuer(TokenConfig.ISSUER)
        .withClaim("userId", userId) // CRITICAL: This links the token back to the user
        .withExpiresAt(Date(System.currentTimeMillis() + TokenConfig.EXPIRATION_TIME_MS))
        .sign(Algorithm.HMAC256(TokenConfig.SECRET))
}