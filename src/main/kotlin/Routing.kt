package com.example.plugins

import com.example.models.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.server.sessions.*
import io.ktor.http.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.time.LocalDate

// Define database operations interface or object (placeholder for actual implementation)
object Database {
    // --- Profile Operations ---
    fun getProfile(userId: String): Profile? = Profile(userId, "Critter Tracker", LocalDate.now(), "Admin", "example@test.com", null, null, null, null, null, null, true, true)
    fun updateProfile(userId: String, request: UpdateProfileRequest): Boolean = true // Mock success

    // --- Animal Operations ---
    fun getAllAnimals(userId: String): List<Animal> = listOf(Animal(1, userId, "Bella", "Dog", LocalDate.now(), null, "F", null, null, false))
    fun createAnimal(userId: String, request: AnimalCreateRequest): Animal? = Animal(100, userId, request.name, request.type, request.dob, request.color, request.gender, request.description, request.imageUrl, request.isSpayedOrNeutered)
    fun getAnimalById(userId: String, animalId: Int): Animal? = Animal(animalId, userId, "Bella", "Dog", LocalDate.now(), null, "F", null, null, false)
    fun updateAnimal(userId: String, animalId: Int, request: AnimalCreateRequest): Boolean = true
    fun deleteAnimal(userId: String, animalId: Int): Boolean = true

    // --- Litter Operations ---
    fun getAllLitters(userId: String): List<Litter> = listOf(Litter(1, userId, "First Litter", LocalDate.now(), 5, null))
    fun createLitter(userId: String, litter: Litter): Litter? = litter.copy(id = 200)
    fun getLitterById(userId: String, litterId: Int): Litter? = Litter(litterId, userId, "First Litter", LocalDate.now(), 5, null)
    fun updateLitter(userId: String, litterId: Int, litter: Litter): Boolean = true
    fun deleteLitter(userId: String, litterId: Int): Boolean = true
}

fun Application.configureRouting() {
    routing {
        // Serve static files (like uploaded images) from the 'uploads' directory
        static("/uploads") {
            files("uploads")
        }

        // --- Authentication Routes (No authentication required) ---
        post("/api/register") {
            try {
                val request = call.receive<RegisterRequest>()
                // Assuming success and JWT generation for simplicity
                val response = LoginResponse(
                    token = "mock-jwt-token-for-${request.email}",
                    userId = "user-${request.email.substringBefore('@')}"
                )
                call.respond(HttpStatusCode.Created, response)
            } catch (e: Exception) {
                call.application.log.error("Registration failed", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Registration failed: ${e.message}"))
            }
        }

        post("/api/login") {
            try {
                val request = call.receive<LoginRequest>()
                // Assuming success and JWT generation for simplicity
                val response = LoginResponse(
                    token = "mock-jwt-token-for-${request.email}",
                    userId = "user-${request.email.substringBefore('@')}"
                )
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                call.application.log.error("Login failed", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Login failed: ${e.message}"))
            }
        }


        // --- Authenticated Routes ---
        authenticate("auth-jwt") {
            route("/api") {
                // Helper to get authenticated user ID
                val getUserId: ApplicationCall.() -> String? = {
                    principal<UserIdPrincipal>()?.name
                }
                
                // --- Profile Management ---

                get("/profile") {
                    val userId = call.getUserId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    
                    try {
                        // In a real application, you'd fetch this from the DB using a transaction
                        val profile = Database.getProfile(userId)
                        if (profile != null) {
                            call.respond(HttpStatusCode.OK, profile)
                        } else {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("Profile not found for user $userId"))
                        }
                    } catch (e: Exception) {
                        call.application.log.error("Error fetching profile", e)
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to fetch profile."))
                    }
                }

                put("/profile") {
                    val userId = call.getUserId() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    val request = try {
                        call.receive<UpdateProfileRequest>()
                    } catch (e: ContentTransformationException) {
                        return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body."))
                    }

                    try {
                        // In a real application, update the DB
                        if (Database.updateProfile(userId, request)) {
                            call.respond(HttpStatusCode.OK, MessageResponse("Profile updated successfully."))
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update profile."))
                        }
                    } catch (e: Exception) {
                        call.application.log.error("Error updating profile", e)
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update profile."))
                    }
                }

                // --- Animal Management ---
                
                get("/animals") {
                    val userId = call.getUserId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    
                    try {
                        val animals = Database.getAllAnimals(userId)
                        call.respond(HttpStatusCode.OK, animals)
                    } catch (e: Exception) {
                        call.application.log.error("Error fetching animals", e)
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to fetch animals."))
                    }
                }

                post("/animals") {
                    val userId = call.getUserId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    val request = try {
                        call.receive<AnimalCreateRequest>()
                    } catch (e: ContentTransformationException) {
                        return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid animal data."))
                    }
                    
                    try {
                        val newAnimal = Database.createAnimal(userId, request)
                        if (newAnimal != null) {
                            call.respond(HttpStatusCode.Created, newAnimal)
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Could not create animal."))
                        }
                    } catch (e: Exception) {
                        call.application.log.error("Error creating animal", e)
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create animal."))
                    }
                }

                get("/animals/{id}") {
                    val userId = call.getUserId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val animalId = call.parameters.getOrFail<Int>("id")

                    try {
                        val animal = Database.getAnimalById(userId, animalId)
                        if (animal != null) {
                            call.respond(HttpStatusCode.OK, animal)
                        } else {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("Animal not found."))
                        }
                    } catch (e: Exception) {
                        call.application.log.error("Error fetching animal $animalId", e)
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to fetch animal."))
                    }
                }

                put("/animals/{id}") {
                    val userId = call.getUserId() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    val animalId = call.parameters.getOrFail<Int>("id")
                    val request = try {
                        call.receive<AnimalCreateRequest>()
                    } catch (e: ContentTransformationException) {
                        return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid animal data."))
                    }

                    try {
                        if (Database.updateAnimal(userId, animalId, request)) {
                            call.respond(HttpStatusCode.OK, MessageResponse("Animal updated successfully."))
                        } else {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("Animal not found or update failed."))
                        }
                    } catch (e: Exception) {
                        call.application.log.error("Error updating animal $animalId", e)
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update animal."))
                    }
                }

                delete("/animals/{id}") {
                    val userId = call.getUserId() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                    val animalId = call.parameters.getOrFail<Int>("id")

                    try {
                        if (Database.deleteAnimal(userId, animalId)) {
                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("Animal not found."))
                        }
                    } catch (e: Exception) {
                        call.application.log.error("Error deleting animal $animalId", e)
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to delete animal."))
                    }
                }

                // --- Litter Management ---

                get("/litters") {
                    val userId = call.getUserId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    
                    try {
                        val litters = Database.getAllLitters(userId)
                        call.respond(HttpStatusCode.OK, litters)
                    } catch (e: Exception) {
                        call.application.log.error("Error fetching litters", e)
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to fetch litters."))
                    }
                }

                post("/litters") {
                    val userId = call.getUserId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    val request = try {
                        call.receive<Litter>()
                    } catch (e: ContentTransformationException) {
                        return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid litter data."))
                    }
                    
                    try {
                        val newLitter = Database.createLitter(userId, request)
                        if (newLitter != null) {
                            call.respond(HttpStatusCode.Created, newLitter)
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Could not create litter."))
                        }
                    } catch (e: Exception) {
                        call.application.log.error("Error creating litter", e)
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create litter."))
                    }
                }

                get("/litters/{id}") {
                    val userId = call.getUserId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val litterId = call.parameters.getOrFail<Int>("id")

                    try {
                        val litter = Database.getLitterById(userId, litterId)
                        if (litter != null) {
                            call.respond(HttpStatusCode.OK, litter)
                        } else {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("Litter not found."))
                        }
                    } catch (e: Exception) {
                        call.application.log.error("Error fetching litter $litterId", e)
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to fetch litter."))
                    }
                }

                put("/litters/{id}") {
                    val userId = call.getUserId() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    val litterId = call.parameters.getOrFail<Int>("id")
                    val request = try {
                        call.receive<Litter>()
                    } catch (e: ContentTransformationException) {
                        return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid litter data."))
                    }

                    try {
                        if (Database.updateLitter(userId, litterId, request)) {
                            call.respond(HttpStatusCode.OK, MessageResponse("Litter updated successfully."))
                        } else {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("Litter not found or update failed."))
                        }
                    } catch (e: Exception) {
                        call.application.log.error("Error updating litter $litterId", e)
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update litter."))
                    }
                }

                delete("/litters/{id}") {
                    val userId = call.getUserId() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                    val litterId = call.parameters.getOrFail<Int>("id")

                    try {
                        if (Database.deleteLitter(userId, litterId)) {
                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("Litter not found."))
                        }
                    } catch (e: Exception) {
                        call.application.log.error("Error deleting litter $litterId", e)
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to delete litter."))
                    }
                }

                // --- File Upload ---

                post("/upload") {
                    val multipart = call.receiveMultipart()
                    var fileName: String? = null

                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            val originalFileName = part.originalFileName ?: "upload_${System.currentTimeMillis()}"
                            val fileExtension = originalFileName.substringAfterLast('.', "jpg")
                            
                            // Generate a unique, safe filename
                            val uniqueFileName = "${System.currentTimeMillis()}_$originalFileName"
                            val file = File("uploads/$uniqueFileName")

                            // Ensure the uploads directory exists
                            file.parentFile.mkdirs() 

                            part.streamProvider().use { inputStream ->
                                file.outputStream().use { fileOutputStream ->
                                    inputStream.copyTo(fileOutputStream)
                                }
                            }
                            fileName = uniqueFileName
                        }
                        part.dispose()
                    }

                    if (fileName != null) {
                        // Return the public URL or path to the uploaded file
                        val publicUrl = call.url { path("uploads", fileName!!) }
                        call.respond(HttpStatusCode.OK, UploadResponse(publicUrl))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("No file uploaded."))
                    }
                }
            } // Closes route("/api")
        } // Closes authenticate("auth-jwt")
    } // Closes routing { ... }
}

// Data models used in the API (you should have these defined elsewhere)
// For compilation completeness, I'll include minimal definitions here.
object Tables {
    // Placeholder to satisfy Exposed/Database calls if they existed in the original file
}

// Assuming these models exist in com.example.models
data class Animal(
    val id: Int,
    val userId: String,
    val name: String,
    val type: String,
    val dob: LocalDate?,
    val color: String?,
    val gender: String?,
    val description: String?,
    val imageUrl: String?,
    val isSpayedOrNeutered: Boolean
)

data class AnimalCreateRequest(
    val name: String,
    val type: String,
    val dob: LocalDate?,
    val color: String?,
    val gender: String?,
    val description: String?,
    val imageUrl: String?,
    val isSpayedOrNeutered: Boolean?
)

data class Litter(
    val id: Int,
    val userId: String,
    val name: String,
    val dob: LocalDate,
    val count: Int,
    val parentIds: List<Int>?
)

data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val email: String, val password: String)
data class LoginResponse(val token: String?, val userId: String?)
data class ErrorResponse(val error: String)
data class MessageResponse(val message: String)
data class Profile(
    val userId: String,
    val name: String,
    val dateJoined: LocalDate,
    val role: String?,
    val email: String,
    val location: String?,
    val phone: String?,
    val website: String?,
    val facebook: String?,
    val instagram: String?,
    val profilePictureUrl: String?,
    val acceptsDonations: Boolean,
    val isVerified: Boolean
)
data class UpdateProfileRequest(
    val name: String,
    val location: String?,
    val phone: String?,
    val website: String?,
    val facebook: String?,
    val instagram: String?,
    val acceptsDonations: Boolean,
    val isVerified: Boolean // Should probably not be user-updatable, but included for completeness
)
data class UploadResponse(val url: String)
