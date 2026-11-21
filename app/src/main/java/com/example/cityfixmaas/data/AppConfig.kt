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

    /** Category preselected when Impact Detection suggests a road defect report. */
    const val ROAD_CATEGORY_ID = "cat-roads"

    /** A new report this close to an active one of the same category and type joins it instead. */
    const val DUPLICATE_RADIUS_METERS = 10.0

    /** A resolved ticket can be reopened by its author for 5 days; after that it is closed. */
    const val REOPEN_WINDOW_MS = 5 * 24 * 3_600_000L

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
