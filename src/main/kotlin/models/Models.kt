package com.example.models

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * All data classes used for serialization (API requests and responses)
 * are centralized here.
 */

@Serializable
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

@Serializable
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

@Serializable
data class Litter(
    val id: Int,
    val userId: String,
    val name: String,
    val dob: LocalDate,
    val count: Int,
    val parentIds: List<Int>? // IDs of parent animals
)

// --- Auth/General Responses ---

@Serializable
data class LoginRequest(val email: String, val password: String)
@Serializable
data class RegisterRequest(val email: String, val password: String)
@Serializable
data class LoginResponse(val token: String?, val userId: String?)
@Serializable
data class ErrorResponse(val error: String)
@Serializable
data class MessageResponse(val message: String)
@Serializable
data class UploadResponse(val url: String)


// --- Profile ---

@Serializable
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

@Serializable
data class UpdateProfileRequest(
    val name: String,
    val location: String?,
    val phone: String?,
    val website: String?,
    val facebook: String?,
    val instagram: String?,
    val acceptsDonations: Boolean,
    val isVerified: Boolean 
)
