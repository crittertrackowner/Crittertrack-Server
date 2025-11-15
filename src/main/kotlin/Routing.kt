package com.example.plugins

import com.example.models.* // Now imports from the new Models.kt file
import com.example.services.SUPABASE_ANON_KEY
import com.example.services.SUPABASE_URL
import com.example.services.SupabaseService
import com.example.security.generateToken
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.http.*
import java.io.File
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.plugins.contentnegotiation.* // Used here for ContentTransformationException

// Initialize the Supabase Service
private val supabaseService = SupabaseService(SUPABASE_URL, SUPABASE_ANON_KEY)

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
                
                // 1. MOCK: Simulate user creation and get a mock ID
                val mockUserId = "user-${request.email.substringBefore('@')}"
                val token = generateToken(mockUserId)
                
                // 2. Create or get the user's profile record in Supabase
                val profile = supabaseService.createOrGetProfile(mockUserId, request.email, null)
                
                if (profile != null) {
                    val response = LoginResponse(token = token, userId = mockUserId)
                    call.respond(HttpStatusCode.Created, response)
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create profile in Supabase."))
                }

            } catch (e: ContentTransformationException) {
                // Catches serialization errors when request body is malformed
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body format."))
            } catch (e: Exception) {
                call.application.log.error("Registration failed", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Registration failed: ${e.message}"))
            }
        }

        post("/api/login") {
            try {
                val request = call.receive<LoginRequest>()
                
                // 1. MOCK: Simulate login success and generate Ktor JWT
                val mockUserId = "user-${request.email.substringBefore('@')}"
                val token = generateToken(mockUserId)
                
                // 2. Ensure profile exists (fetch it from Supabase)
                val profile = supabaseService.getProfile(mockUserId)
                
                if (profile != null) {
                    val response = LoginResponse(token = token, userId = mockUserId)
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found or profile missing."))
                }
            } catch (e: ContentTransformationException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body format."))
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
                        val profile = supabaseService.getProfile(userId)
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
                        if (supabaseService.updateProfile(userId, request)) {
                            // Fetch the updated profile to send back (optional, sending success message here)
                            call.respond(HttpStatusCode.OK, MessageResponse("Profile updated successfully."))
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update profile in Supabase."))
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
                        val animals = supabaseService.getAllAnimals(userId)
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
                        val newAnimal = supabaseService.createAnimal(userId, request)
                        if (newAnimal != null) {
                            call.respond(HttpStatusCode.Created, newAnimal)
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Could not create animal in Supabase."))
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
                        val animal = supabaseService.getAnimalById(userId, animalId)
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
                        if (supabaseService.updateAnimal(userId, animalId, request)) {
                            call.respond(HttpStatusCode.OK, MessageResponse("Animal updated successfully."))
                        } else {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("Animal not found or update failed in Supabase."))
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
                        if (supabaseService.deleteAnimal(userId, animalId)) {
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
                        val litters = supabaseService.getAllLitters(userId)
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
                        val newLitter = supabaseService.createLitter(userId, request)
                        if (newLitter != null) {
                            call.respond(HttpStatusCode.Created, newLitter)
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Could not create litter in Supabase."))
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
                        val litter = supabaseService.getLitterById(userId, litterId)
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
                        if (supabaseService.updateLitter(userId, litterId, request)) {
                            call.respond(HttpStatusCode.OK, MessageResponse("Litter updated successfully."))
                        } else {
                            call.respond(HttpStatusCode.NotFound, ErrorResponse("Litter not found or update failed in Supabase."))
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
                        if (supabaseService.deleteLitter(userId, litterId)) {
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

                    // This is a local file upload. For a real app, you would use the Supabase Storage API here.
                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            val originalFileName = part.originalFileName ?: "upload_${System.currentTimeMillis()}"
                            val uniqueFileName = "${System.currentTimeMillis()}_$originalFileName"
                            val file = File("uploads/$uniqueFileName")

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
                        val publicUrl = call.url { path("uploads", fileName!!) }
                        call.respond(HttpStatusCode.OK, UploadResponse(publicUrl))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("No file uploaded."))
                    }
                }
            }
        }
    }
}
