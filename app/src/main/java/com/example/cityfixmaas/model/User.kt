package com.example.cityfixmaas.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class UserRole {
    ANONYMOUS, CITIZEN, OPERATOR
}

@Serializable
data class User(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val email: String,
    val phone: String,
    val nif: String? = null,
    val profileType: UserRole = UserRole.CITIZEN,
    val photoPath: String? = null,
    val impactDetectionEnabled: Boolean = false,
    val isVerified: Boolean = false, // Pending until validated by municipal services via NIF
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)
