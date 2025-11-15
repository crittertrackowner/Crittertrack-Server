package com.example.plugins

import com.example.Animals
import com.example.Users
import com.example.dbQuery
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import java.util.UUID
import org.jetbrains.exposed.dao.id.EntityID
import org.mindrot.jbcrypt.BCrypt
import io.ktor.serialization.*

// Explicitly import necessary Exposed DSL members
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.stringLiteral // FIX: Import stringLiteral as a top-level function

import java.time.LocalDate
import org.jetbrains.exposed.sql.CustomFunction
import org.jetbrains.exposed.sql.VarCharColumnType
import kotlinx.serialization.Contextual

// PLACEHOLDER: Assuming these are defined elsewhere for JWT/LocalDate
// If these are defined in your 'com.example.plugins' package, remove these placeholder definitions
data class JwtPrincipal(val userId: String) : Principal
fun generateToken(userId: String): String = "dummy-token-for-$userId"


// Helper function to apply SQL LOWER
fun Column<String?>.lowerCase() = CustomFunction<String?>("LOWER", VarCharColumnType(), this)

// Helper function to handle NULL names gracefully for searching
fun Column<String?>.coalesceToEmpty() = CustomFunction<String>("COALESCE", VarCharColumnType(), this, stringLiteral(""))


// --- DATA CLASSES ---

@Serializable
data class AnimalListResponse(
    val id: String,
    val userId: String,
    val name: String?,
    val species: String,
    val showOnProfile: Boolean
)

@Serializable
data class FullAnimalResponse(
    val sequentialId: Int? = null,
    val id: String,
    val userId: String,
    val name: String?,
    val species: String,
    val breeder: String?,
    @Contextual val birthDate: LocalDate?,
    val gender: String?,
    val colorVariety: String?,
    val coatVariety: String?,
    val registryCode: String?,
    val owner: String?,
    val remarks: String?,
    val fatherId: String?,
    val motherId: String?,
    val showOnProfile: Boolean,
    val showRegistryCode: Boolean,
    val showOwner: Boolean,
    val showRemarks: Boolean,
    val showParents: Boolean,
    val geneticsCode: String?
)

@Serializable
data class CreateAnimalRequest(
    val name: String?,
    val species: String,
    val breeder: String?,
    @Contextual val birthDate: LocalDate?,
    val gender: String?,
    val colorVariety: String?,
    val coatVariety: String?,
    val registryCode: String?,
    val owner: String?,
    val remarks: String?,
    val fatherId: String?,
    val motherId: String?,
    val showOnProfile: Boolean,
    val showRegistryCode: Boolean,
    val showOwner: Boolean,
    val showRemarks: Boolean,
    val showParents: Boolean,
    val geneticsCode: String?
)

@Serializable
data class UpdateAnimalRequest(
    val name: String? = null,
    val species: String? = null,
    val breeder: String? = null,
    @Contextual val birthDate: LocalDate? = null,
    val gender: String? = null,
    val colorVariety: String? = null, 
    val coatVariety: String? = null, 
    val registryCode: String? = null,
    val owner: String? = null,
    val remarks: String? = null,
    val fatherId: String? = null,
    val motherId: String? = null,
    val showOnProfile: Boolean? = null,
    val showRegistryCode: Boolean? = null,
    val showOwner: Boolean? = null,
    val showRemarks: Boolean? = null,
    val showParents: Boolean? = null,
    val geneticsCode: String? = null
)

@Serializable
data class UserRegistrationRequest(
    val email: String,
    val password: String,
    val personalName: String?,
    val breederName: String?,
    // FIX: Client MUST send this field explicitly to avoid Ktor serialization failure
    val isBreederProfile: Boolean = false 
)

@Serializable
data class UserLoginRequest(
    val email: String,
    val password: String
)

// 🛑 NEW: Data class for updating the user's profile 🛑
@Serializable
data class UpdateProfileRequest(
    val personalName: String? = null,
    val breederName: String? = null,
    val profilePictureUrl: String? = null,
    val isBreederProfile: Boolean? = null // Allow setting this field
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val personalName: String?,
    val breederName: String?,
    val profilePictureUrl: String?,
    val isBreederProfile: Boolean,
    val sequentialId: Int
)

// --- MAPPER FUNCTIONS ---

fun ResultRow.toUserResponse() = UserResponse(
    id = this[Users.id].value,
    email = this[Users.email],
    personalName = this[Users.personalName],
    breederName = this[Users.breederName],
    profilePictureUrl = this[Users.profilePictureUrl],
    isBreederProfile = this[Users.isBreederProfile],
    sequentialId = this[Users.sequentialId]
)

fun ResultRow.toAnimalListResponse() = AnimalListResponse(
    id = this[Animals.id].value,
    userId = this[Animals.userId].value,
    name = this[Animals.name],
    species = this[Animals.species],
    showOnProfile = this[Animals.showOnProfile]
)

fun ResultRow.toFullAnimalResponse() = FullAnimalResponse(
    sequentialId = this[Animals.sequentialId],
    id = this[Animals.id].value,
    userId = this[Animals.userId].value,
    name = this[Animals.name],
    species = this[Animals.species],
    breeder = this[Animals.breeder],
    birthDate = this[Animals.birthDate],
    gender = this[Animals.gender],
    colorVariety = this [Animals.colorVariety],
    coatVariety = this [Animals.coatVariety],
    registryCode = this[Animals.registryCode],
    owner = this[Animals.owner],
    remarks = this[Animals.remarks],
    fatherId = this[Animals.fatherId],
    motherId = this[Animals.motherId],
    showOnProfile = this[Animals.showOnProfile],
    showRegistryCode = this[Animals.showRegistryCode],
    showOwner = this[Animals.showOwner],
    showRemarks = this[Animals.showRemarks],
    showParents = this[Animals.showParents],
    geneticsCode = this[Animals.geneticsCode]
)


// --- ROUTING CONFIGURATION ---

fun Application.configureRouting() {
    routing {

        // --- AUTHENTICATION ROUTES ---

        post("/api/register") {
            val request = try {
                call.receive<UserRegistrationRequest>()
            } catch (e: ContentTransformationException) {
                // Return 400 if JSON format is wrong, which includes missing non-nullable fields
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid request format. Ensure all required fields (email, password, isBreederProfile, etc.) are present.")
            }

            // --- INPUT VALIDATION ---
            if (request.email.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Email is required."))
            }
            if (request.password.length < 12) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Password must be at least 12 characters long."))
            }

            val hashedPassword = BCrypt.hashpw(request.password, BCrypt.gensalt())
            val newUserId = UUID.randomUUID().toString()
            
            // FIX: Create EntityID outside the insert block to ensure typing is unambiguous
            val userIdEntity = EntityID(newUserId, Users)
            
            // Using dbQuery (suspending)
            val success = dbQuery {
                val existingUser = Users.select { Users.email eq request.email }.singleOrNull()
                if (existingUser != null) {
                    return@dbQuery false // Indicate email conflict
                }

                // FIX: Removed NESTED Users.insert {} block
                Users.insert {
                    // FIX: Insert the raw String value (.value) to bypass the compiler's generic issue 
                    // with the exposed set(Column<EntityID<S>>, EntityID<S>) overload.
                    it[Users.id] = userIdEntity.value 
                    it[Users.email] = request.email
                    it[Users.passwordHash] = hashedPassword
                    it[Users.personalName] = request.personalName
                    it[Users.breederName] = request.breederName
                    it[Users.isBreederProfile] = request.isBreederProfile
                }
                return@dbQuery true // Indicate success
            }
            
            if (!success) {
                return@post call.respond(HttpStatusCode.Conflict, "Email already registered.")
            }

            // Respond with a success message and a token for immediate login
            val token = generateToken(newUserId)
            call.respond(HttpStatusCode.Created, mapOf("token" to token, "userId" to newUserId))
        }

        post("/api/login") {
            val request = call.receive<UserLoginRequest>()

            // Using dbQuery (suspending)
            val userRow = dbQuery {
                Users.select { Users.email eq request.email }.singleOrNull()
            }

            if (userRow != null && BCrypt.checkpw(request.password, userRow[Users.passwordHash])) {
                val userId = userRow[Users.id].value
                val token = generateToken(userId)
                call.respond(HttpStatusCode.OK, mapOf("token" to token, "userId" to userId))
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
            }
        }

        // --- PUBLIC ROUTES (No Auth Required) ---

        // Public Animal Search Endpoint (for public profiles)
        get("/api/public/animals/list/{ownerId}") {
            val ownerId = call.parameters.getOrFail("ownerId")
            
            // Using dbQuery (suspending)
            val animals = dbQuery {
                // ownerId (String) must be wrapped in EntityID for comparison with Animals.userId
                Animals.select { (Animals.userId eq EntityID(ownerId, Users)) and (Animals.showOnProfile eq true) }
                    .map { it.toAnimalListResponse() } // FIX: Added map here to convert ResultRow to Response
            }
            call.respond(HttpStatusCode.OK, animals)
        }

        // Public Animal Detail Endpoint
        get("/api/public/animals/{ownerId}/{animalId}") {
            val ownerId = call.parameters.getOrFail("ownerId")
            val animalId = call.parameters.getOrFail("animalId")

            // Using dbQuery (suspending)
            val animal = dbQuery {
                // Both IDs must be wrapped
                Animals.select {
                        (Animals.id eq EntityID(animalId, Animals)) and
                        (Animals.userId eq EntityID(ownerId, Users)) and
                        (Animals.showOnProfile eq true)
                    }
                    .singleOrNull()
                    ?.toFullAnimalResponse()
            }

            if (animal != null) {
                call.respond(HttpStatusCode.OK, animal)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
        
        // Public User Profile Info Endpoint
        get("/api/public/user/{ownerId}") {
            val ownerId = call.parameters.getOrFail("ownerId")

            // Using dbQuery (suspending)
            val user = dbQuery {
                // ownerId (String) must be wrapped in EntityID for comparison with Users.id
                Users.select { Users.id eq EntityID(ownerId, Users) }
                    .singleOrNull()
                    ?.toUserResponse()
            }

            if (user != null) {
                call.respond(HttpStatusCode.OK, user)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        // Public User Search Endpoint (using COALESCE)
        get("/api/public/users/search") {
            val searchTerm = call.request.queryParameters["q"]
            
            if (searchTerm.isNullOrBlank()) {
                return@get call.respond(HttpStatusCode.BadRequest, "Missing search query parameter 'q'.")
            }

            val searchResults = dbQuery {
                // Prepare for name/breeder search (case-insensitive partial match)
                val lowerTerm = "%${searchTerm.lowercase()}%"

                Users.select {
                    // Use coalesceToEmpty() to treat NULL names as empty strings for robust searching
                    (Users.personalName.coalesceToEmpty().lowerCase() like lowerTerm).or(
                        Users.breederName.coalesceToEmpty().lowerCase() like lowerTerm
                    )
                }
                .map { it.toUserResponse() }
            }

            call.respond(HttpStatusCode.OK, searchResults)
        }


        // --- PROTECTED ROUTES (Requires Auth) ---

        authenticate("auth-jwt") {
            
            // --- USER DETAILS ENDPOINT ---
            get("/api/user") {
                val principal = call.principal<JwtPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val ownerUid = principal.userId

                // Using dbQuery (suspending)
                val user = dbQuery {
                    // ownerUid (String) must be wrapped in EntityID for comparison
                    Users.select { Users.id eq EntityID(ownerUid, Users) }
                        .singleOrNull()
                        ?.toUserResponse()
                }

                if (user != null) {
                    call.respond(HttpStatusCode.OK, user)
                } else {
                    call.respond(HttpStatusCode.NotFound, "User not found")
                }
            }

            // 🛑 FIX: USER PROFILE UPDATE ENDPOINT (POST /api/profile) 🛑
            post("/api/profile") {
                val principal = call.principal<JwtPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val ownerUid = principal.userId
                
                // 1. Receive the update request
                val request = try {
                    call.receive<UpdateProfileRequest>()
                } catch (e: ContentTransformationException) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid request format or data structure.")
                }

                // 2. Perform the update in the database
                val updateCount = dbQuery {
                    Users.update({ Users.id eq EntityID(ownerUid, Users) }) { stmt ->
                        // Only update fields that are explicitly provided (not null) in the request
                        request.personalName?.let { stmt[Users.personalName] = it }
                        request.breederName?.let { stmt[Users.breederName] = it }
                        request.profilePictureUrl?.let { stmt[Users.profilePictureUrl] = it }
                        request.isBreederProfile?.let { stmt[Users.isBreederProfile] = it }
                    }
                }

                if (updateCount > 0) {
                    // 3. Fetch the updated user data to return in the response
                    val updatedUser = dbQuery {
                        Users.select { Users.id eq EntityID(ownerUid, Users) }
                            .singleOrNull()
                            ?.toUserResponse()
                    }
                    if (updatedUser != null) {
                        call.respond(HttpStatusCode.OK, updatedUser)
                    } else {
                        // This case should theoretically not happen if updateCount > 0
                        call.respond(HttpStatusCode.InternalServerError, "Profile updated but failed to retrieve new details.")
                    }
                } else {
                    // If updateCount is 0, the user ID was either not found or no data was actually changed.
                    call.respond(HttpStatusCode.NotFound, "User not found or no new information provided to update.")
                }
            }

            // --- USER'S ANIMAL LIST ENDPOINT ---

            get("/api/animals") {
                val principal = call.principal<JwtPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val ownerUid = principal.userId
                
                val speciesFilter = call.request.queryParameters["species"]

                // Using dbQuery (suspending)
                val animals = dbQuery {
                    // ownerUid (String) must be wrapped in EntityID for comparison
                    var query = Animals.select { Animals.userId eq EntityID(ownerUid, Users) }

                    if (!speciesFilter.isNullOrBlank()) {
                        query = query.andWhere { Animals.species.lowerCase() like "%${speciesFilter.lowercase()}%" }
                    }

                    query.map { it.toAnimalListResponse() }
                }
                call.respond(HttpStatusCode.OK, animals)
            }
            
            // --- ANIMAL CREATION ENDPOINT ---

            post("/api/animals") {
                val principal = call.principal<JwtPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val ownerUid = principal.userId
                
                val request = try {
                    call.receive<CreateAnimalRequest>()
                } catch (e: ContentTransformationException) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid request format or date serialization issue.")
                }

                val newAnimalId = UUID.randomUUID().toString()
                
                // FIX: Create EntityID objects outside the insert block
                val animalIdEntity = EntityID(newAnimalId, Animals)
                val userIdEntity = EntityID(ownerUid, Users)

                // Using dbQuery (suspending)
                dbQuery {
                    Animals.insert { stmt ->
                        // All EntityID columns (id, userId) must be wrapped
                        stmt[Animals.id] = animalIdEntity // Animal ID (PK)
                        stmt[Animals.userId] = userIdEntity // Foreign Key to User
                        
                        stmt[Animals.name] = request.name
                        stmt[Animals.species] = request.species
                        stmt[Animals.breeder] = request.breeder
                        stmt[Animals.birthDate] = request.birthDate
                        stmt[Animals.gender] = request.gender
                        stmt[Animals.colorVariety] = request.colorVariety
                        stmt[Animals.coatVariety] = request.coatVariety
                        stmt[Animals.registryCode] = request.registryCode
                        stmt[Animals.owner] = request.owner
                        stmt[Animals.remarks] = request.remarks
                        stmt[Animals.fatherId] = request.fatherId
                        stmt[Animals.motherId] = request.motherId
                        stmt[Animals.showOnProfile] = request.showOnProfile
                        stmt[Animals.showRegistryCode] = request.showRegistryCode
                        stmt[Animals.showOwner] = request.showOwner
                        stmt[Animals.showRemarks] = request.showRemarks
                        stmt[Animals.showParents] = request.showParents
                        stmt[Animals.geneticsCode] = request.geneticsCode
                    }
                }

                call.respond(HttpStatusCode.Created, mapOf("id" to newAnimalId))
            }

            // --- ANIMAL DETAIL ENDPOINT ---

            get("/api/animals/{id}") {
                val principal = call.principal<JwtPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val ownerUid = principal.userId
                val animalId = call.parameters.getOrFail("id")

                // Using dbQuery (suspending)
                val animal = dbQuery {
                    Animals.select {
                        // Both IDs must be wrapped for comparison
                        (Animals.id eq EntityID(animalId, Animals)) and
                        (Animals.userId eq EntityID(ownerUid, Users))
                    }
                        .singleOrNull()
                        ?.toFullAnimalResponse()
                }

                if (animal != null) {
                    call.respond(HttpStatusCode.OK, animal)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            // --- ANIMAL UPDATE ENDPOINT ---

            put("/api/animals/{id}") {
                val principal = call.principal<JwtPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                val ownerUid = principal.userId
                val animalId = call.parameters.getOrFail("id")

                val request = try {
                    call.receive<UpdateAnimalRequest>()
                } catch (e: ContentTransformationException) {
                    return@put call.respond(HttpStatusCode.BadRequest, "Invalid request format or date serialization issue.")
                }

                // Using dbQuery (suspending)
                val updateCount = dbQuery {
                    // Both IDs must be wrapped for comparison in the update filter
                    Animals.update({
                        (Animals.id eq EntityID(animalId, Animals)) and
                        (Animals.userId eq EntityID(ownerUid, Users))
                    }) { stmt ->
                        request.name?.let { stmt[Animals.name] = it }
                        request.species?.let { stmt[Animals.species] = it }
                        request.breeder?.let { stmt[Animals.breeder] = it }
                        request.birthDate?.let { stmt[Animals.birthDate] = it }
                        request.gender?.let { stmt[Animals.gender] = it }
                        request.colorVariety?.let { stmt[Animals.colorVariety] = it }
                        request.coatVariety?.let { stmt[Animals.coatVariety] = it }
                        request.registryCode?.let { stmt[Animals.registryCode] = it }
                        request.owner?.let { stmt[Animals.owner] = it }
                        request.remarks?.let { stmt[Animals.remarks] = it }
                        request.fatherId?.let { stmt[Animals.fatherId] = it }
                        request.motherId?.let { stmt[Animals.motherId] = it }
                        request.showOnProfile?.let { stmt[Animals.showOnProfile] = it }
                        request.showRegistryCode?.let { stmt[Animals.showRegistryCode] = it }
                        request.showOwner?.let { stmt[Animals.showOwner] = it }
                        request.showRemarks?.let { stmt[Animals.showRemarks] = it }
                        request.showParents?.let { stmt[Animals.showParents] = it }
                        request.geneticsCode?.let { stmt[Animals.geneticsCode] = it }
                    }
                }

                if (updateCount > 0) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Animal updated successfully."))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Animal not found or you don't have permission to edit it.")
                }
            }

            // --- ANIMAL DELETION ENDPOINT ---

            delete("/api/animals/{id}") {
                val principal = call.principal<JwtPrincipal>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                val ownerUid = principal.userId
                val animalId = call.parameters.getOrFail("id")

                // Using dbQuery (suspending)
                val deleteCount = dbQuery {
                    // Both IDs must be wrapped for comparison in the delete filter
                    Animals.deleteWhere {
                        (Animals.id eq EntityID(animalId, Animals)) and
                        (Animals.userId eq EntityID(ownerUid, Users))
                    }
                }

                if (deleteCount > 0) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Animal not found or you don't have permission to delete it.")
                }
            }

            // 🐾 STUBBED ENDPOINT 🐾
            get("/api/litters") {
                call.principal<JwtPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(HttpStatusCode.OK, emptyList<String>())
            }

        } // CLOSES authenticate("auth-jwt")
    } // CLOSES routing
} // CLOSES configureRouting
