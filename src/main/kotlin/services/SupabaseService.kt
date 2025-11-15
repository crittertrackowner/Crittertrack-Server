package com.example.services

import com.example.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.time.LocalDate

// NOTE: These should be loaded from application.conf in a real deployment
// *** REMEMBER TO REPLACE THESE PLACEHOLDERS WITH YOUR ACTUAL SUPABASE KEYS ***
const val SUPABASE_URL = "YOUR_SUPABASE_PROJECT_URL" // e.g., https://yourproject.supabase.co
const val SUPABASE_ANON_KEY = "YOUR_SUPABASE_ANON_KEY" // The public API key

/**
 * Service to handle all communication with the Supabase PostgREST API.
 * The Ktor server acts as a secure intermediary (Backend-for-Frontend).
 */
class SupabaseService(private val baseUrl: String, private val apiKey: String) {

    // --- Ktor HTTP Client Setup ---
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        defaultRequest {
            url(baseUrl)
            header("apikey", apiKey) // Supabase API key authentication
            header("Content-Type", ContentType.Application.Json)
        }
    }

    // --- Helper function to map responses ---
    private suspend inline fun <reified T> handleResponse(response: HttpResponse): T? {
        return when (response.status) {
            HttpStatusCode.OK, HttpStatusCode.Created -> response.body()
            HttpStatusCode.NotFound -> null
            else -> {
                println("Supabase API Error [${response.status.value}]: ${response.bodyAsText()}")
                throw RuntimeException("Supabase API request failed with status: ${response.status}")
            }
        }
    }

    // --- Profile Operations (Assuming a 'profiles' table with RLS) ---
    
    suspend fun createOrGetProfile(userId: String, email: String, name: String?): Profile? {
        val path = "/rest/v1/profiles"
        
        // 1. Try to fetch the existing profile
        val fetchResponse = client.get(path) {
            parameter("user_id", "eq.$userId")
            parameter("select", "*")
        }

        val existingProfiles = handleResponse<List<Profile>>(fetchResponse)

        if (!existingProfiles.isNullOrEmpty()) {
            return existingProfiles.first()
        }
        
        // 2. If not found, create a new one
        val newProfile = Profile(
            userId = userId,
            name = name ?: email.substringBefore("@"),
            dateJoined = LocalDate.now(),
            role = "Basic",
            email = email,
            location = null, phone = null, website = null, facebook = null, instagram = null,
            profilePictureUrl = null,
            acceptsDonations = false,
            isVerified = false
        )

        val createResponse = client.post(path) {
            header("Prefer", "return=representation")
            setBody(newProfile)
        }
        return handleResponse<List<Profile>>(createResponse)?.firstOrNull()
    }

    suspend fun getProfile(userId: String): Profile? {
        val response = client.get("/rest/v1/profiles") {
            parameter("user_id", "eq.$userId")
            parameter("select", "*")
        }
        return handleResponse<List<Profile>>(response)?.firstOrNull()
    }

    suspend fun updateProfile(userId: String, request: UpdateProfileRequest): Boolean {
        val response = client.patch("/rest/v1/profiles") {
            parameter("user_id", "eq.$userId")
            setBody(request)
        }
        return response.status == HttpStatusCode.NoContent
    }

    // --- Animal Operations (Table name: animals) ---

    suspend fun getAllAnimals(userId: String): List<Animal> {
        val response = client.get("/rest/v1/animals") {
            parameter("user_id", "eq.$userId")
            parameter("select", "*")
        }
        return handleResponse<List<Animal>>(response) ?: emptyList()
    }

    suspend fun createAnimal(userId: String, request: AnimalCreateRequest): Animal? {
        val animalData = request.toAnimal(userId)
        val response = client.post("/rest/v1/animals") {
            header("Prefer", "return=representation")
            setBody(animalData)
        }
        return handleResponse<List<Animal>>(response)?.firstOrNull()
    }

    suspend fun getAnimalById(userId: String, animalId: Int): Animal? {
        val response = client.get("/rest/v1/animals") {
            parameter("id", "eq.$animalId")
            parameter("user_id", "eq.$userId")
            parameter("select", "*")
        }
        return handleResponse<List<Animal>>(response)?.firstOrNull()
    }

    suspend fun updateAnimal(userId: String, animalId: Int, request: AnimalCreateRequest): Boolean {
        val response = client.patch("/rest/v1/animals") {
            parameter("id", "eq.$animalId")
            parameter("user_id", "eq.$userId")
            setBody(request)
        }
        return response.status == HttpStatusCode.NoContent
    }

    suspend fun deleteAnimal(userId: String, animalId: Int): Boolean {
        val response = client.delete("/rest/v1/animals") {
            parameter("id", "eq.$animalId")
            parameter("user_id", "eq.$userId")
        }
        return response.status == HttpStatusCode.NoContent
    }
    
    // --- Litter Operations (Table name: litters) ---

    suspend fun getAllLitters(userId: String): List<Litter> {
        val response = client.get("/rest/v1/litters") {
            parameter("user_id", "eq.$userId")
            parameter("select", "*")
        }
        return handleResponse<List<Litter>>(response) ?: emptyList()
    }

    suspend fun createLitter(userId: String, request: Litter): Litter? {
        val response = client.post("/rest/v1/litters") {
            header("Prefer", "return=representation")
            setBody(request.copy(userId = userId))
        }
        return handleResponse<List<Litter>>(response)?.firstOrNull()
    }

    suspend fun getLitterById(userId: String, litterId: Int): Litter? {
        val response = client.get("/rest/v1/litters") {
            parameter("id", "eq.$litterId")
            parameter("user_id", "eq.$userId")
            parameter("select", "*")
        }
        return handleResponse<List<Litter>>(response)?.firstOrNull()
    }

    suspend fun updateLitter(userId: String, litterId: Int, request: Litter): Boolean {
        val response = client.patch("/rest/v1/litters") {
            parameter("id", "eq.$litterId")
            parameter("user_id", "eq.$userId")
            setBody(request)
        }
        return response.status == HttpStatusCode.NoContent
    }

    suspend fun deleteLitter(userId: String, litterId: Int): Boolean {
        val response = client.delete("/rest/v1/litters") {
            parameter("id", "eq.$litterId")
            parameter("user_id", "eq.$userId")
        }
        return response.status == HttpStatusCode.NoContent
    }
}

// Helper extension function to combine the request data with the authenticated userId
fun AnimalCreateRequest.toAnimal(userId: String): Animal {
    return Animal(
        id = 0, // ID will be set by Supabase
        userId = userId,
        name = this.name,
        type = this.type,
        dob = this.dob,
        color = this.color,
        gender = this.gender,
        description = this.description,
        imageUrl = this.imageUrl,
        isSpayedOrNeutered = this.isSpayedOrNeutered ?: false
    )
}
