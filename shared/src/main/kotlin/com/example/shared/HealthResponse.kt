package com.example.shared

import kotlinx.serialization.Serializable

/**
 * Response model for the /health endpoint.
 * Shared between android-app and server.
 */
@Serializable
data class HealthResponse(
    val status: String,
    val message: String = "OK",
)
