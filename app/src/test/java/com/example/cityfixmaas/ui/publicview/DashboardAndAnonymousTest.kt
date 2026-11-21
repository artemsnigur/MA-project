package com.example.cityfixmaas.ui.publicview

import com.example.cityfixmaas.data.AppConfig
import com.example.cityfixmaas.data.distanceTo
import com.example.cityfixmaas.data.model.Category
import com.example.cityfixmaas.data.model.LatLng
import com.example.cityfixmaas.data.roundedForPublic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardAndAnonymousTest {

    @Test
    fun anonymousGeofence_distanceCheck() {
        val center = AppConfig.ANONYMOUS_AREA_CENTER

        // Center point is 0 metres away
        assertEquals(0.0, center.distanceTo(center), 0.1)

        // Point nearby in Coimbra (approx 1.5 km away)
        val coimbraNorth = LatLng(center.latitude + 0.015, center.longitude)
        val distance = center.distanceTo(coimbraNorth)
        assertTrue(distance < AppConfig.ANONYMOUS_RADIUS_METERS)

        // Far away point (e.g. Lisbon ~38.72, -9.14) is well beyond 10 km (approx 175 km)
        val lisbon = LatLng(38.7223, -9.1393)
        val lisbonDistance = center.distanceTo(lisbon)
        assertTrue(lisbonDistance > AppConfig.ANONYMOUS_RADIUS_METERS)
        assertTrue(lisbonDistance > 150_000.0)
    }

    @Test
    fun publicLocation_isRoundedToTwoDecimals() {
        val exactLocation = LatLng(40.209385, -8.419612)
        val rounded = exactLocation.roundedForPublic()

        assertEquals(40.21, rounded.latitude, 0.001)
        assertEquals(-8.42, rounded.longitude, 0.001)
    }

    @Test
    fun categoryModel_defaultValues() {
        val cat = Category(id = "roads", name = "Public Roads")
        assertTrue(cat.active)
    }
}
