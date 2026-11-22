package com.example.cityfixmaas.data

import com.example.cityfixmaas.data.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object AppConfig {
    /** Praça da República, Coimbra (approximate; confirm before the demo). */
    val ANONYMOUS_AREA_CENTER = LatLng(latitude = 40.2093, longitude = -8.4196)

    const val ANONYMOUS_RADIUS_METERS = 10_000.0

    /** An operator this close to a ticket in resolution notifies its citizens. */
    const val OPERATOR_GEOFENCE_METERS = 20.0

    /** Web app URL of the relay (server/relay): push messages, sign-in codes, videos in Google Drive. */
    const val RELAY_URL = "https://script.google.com/macros/s/AKfycbxE_AXzq5G8G4dy2nDGpvuFs1eQTGKRkC203Me_hGLdE077JeB4OhaNUDj6dDQS8v3X/exec"

    /** Category preselected when Impact Detection suggests a road defect report. */
    const val ROAD_CATEGORY_ID = "cat-roads"

    /** A new report this close to an active one of the same category and type joins it instead. */
    const val DUPLICATE_RADIUS_METERS = 10.0

    /** A resolved ticket can be reopened by its author for 5 days; after that it is closed. */
    const val REOPEN_WINDOW_MS = 5 * 24 * 3_600_000L

    /**
     * Demo accounts with made-up addresses that cannot receive email, so they skip the email
     * confirmation and the sign-in code. Every other account needs both.
     */
    val EMAIL_CONFIRMATION_EXEMPT = setOf(
        "operator@cm-coimbra.pt",
        "rui.demo@example.com",
        "joana.demo@example.com",
        "maria1795093478@example.com",
        "test1795090837@example.com",
    )

    /** Operators must register with an email in this domain. TODO: confirm the domain. */
    const val MUNICIPAL_EMAIL_DOMAIN = "cm-coimbra.pt"
}

/** Great-circle distance in metres (haversine). */
fun LatLng.distanceTo(other: LatLng): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(other.latitude - latitude)
    val dLng = Math.toRadians(other.longitude - longitude)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) *
        sin(dLng / 2) * sin(dLng / 2)
    return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** Location shown to anonymous users: rounded to 2 decimals (about 1 km), never the exact point. */
fun LatLng.roundedForPublic(): LatLng = LatLng(
    latitude = Math.round(latitude * 100) / 100.0,
    longitude = Math.round(longitude * 100) / 100.0,
)
