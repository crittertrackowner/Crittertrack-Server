package com.example.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.time.LocalDate

/**
 * Custom KSerializer for Kotlinx Serialization to handle non-nullable java.time.LocalDate.
 * Serializes LocalDate objects to ISO 8601 format (e.g., "YYYY-MM-DD") strings.
 */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDate {
        return LocalDate.parse(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString()) // Serializes to "YYYY-MM-DD"
    }
}
// NullableLocalDateSerializer has been removed.

// 🚨 THIS IS THE ADDED EXTENSION FUNCTION THAT RESOLVES THE COMPILATION ERROR 🚨
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            Json {
                // Register the custom LocalDateSerializer using a SerializersModule.
                // This ensures all LocalDate objects in your application are serialized correctly.
                serializersModule = SerializersModule {
                    contextual(LocalDate::class, LocalDateSerializer)
                }

                // Recommended JSON settings for development/robustness:
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            }
        )
    }
}