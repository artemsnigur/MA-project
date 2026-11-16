package com.example.cityfixmaas.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val ticketId: String,
    val senderId: String,
    val content: String,
    val timestampEpochMillis: Long = System.currentTimeMillis()
)
