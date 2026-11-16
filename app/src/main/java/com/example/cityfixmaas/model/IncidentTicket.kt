package com.example.cityfixmaas.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class TicketLifecycleStatus {
    OPEN, INFORMATION_REQUEST, IN_RESOLUTION, RESOLVED, CLOSED
}

@Serializable
enum class AnomalyType {
    DAMAGE, BREAKDOWN, DESTRUCTION, THEFT
}

@Serializable
data class IncidentTicket(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val categoryId: String,
    val anomalyType: AnomalyType,
    val status: TicketLifecycleStatus = TicketLifecycleStatus.OPEN,
    val scopePublic: Boolean = true, // Public or Private
    val authorId: String,
    val latitude: Double,
    val longitude: Double,
    val mediaPaths: List<String> = emptyList(), // Local paths or URLs for images/videos
    val timestampEpochMillis: Long = System.currentTimeMillis()
)
