package com.example.cityfixmaas.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Category(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isActive: Boolean = true
)
