package com.example.cityfixmaas.data

import com.example.cityfixmaas.data.model.LatLng
import com.example.cityfixmaas.data.model.LifecycleState
import com.example.cityfixmaas.data.model.Ticket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Business rules that live in the data layer: lifecycle moves, reopening, the 10 km area, public rounding. */
class ModelTest {

    @Test
    fun reopen_onlyWithinFiveDaysOfResolution() {
        val day = 24 * 3_600_000L
        val now = 100 * day
        val resolved = Ticket(lifecycleState = LifecycleState.RESOLVED, resolvedAt = now - 4 * day)
        assertTrue(resolved.canReopen(now))
        assertFalse(resolved.isReopenExpired(now))

        val old = resolved.copy(resolvedAt = now - 6 * day)
        assertFalse(old.canReopen(now))
        assertTrue(old.isReopenExpired(now)) // closed automatically

        val closed = old.copy(lifecycleState = LifecycleState.CLOSED)
        assertFalse(closed.canReopen(now))
        assertFalse(closed.isReopenExpired(now))
    }

    @Test
    fun lifecycle_allowedMoves() {
        assertEquals(setOf(LifecycleState.INFORMATION_REQUEST, LifecycleState.IN_RESOLUTION), LifecycleState.OPEN.allowedNext())
        assertEquals(setOf(LifecycleState.OPEN, LifecycleState.IN_RESOLUTION), LifecycleState.INFORMATION_REQUEST.allowedNext())
        assertEquals(setOf(LifecycleState.RESOLVED), LifecycleState.IN_RESOLUTION.allowedNext())
        assertEquals(setOf(LifecycleState.CLOSED), LifecycleState.RESOLVED.allowedNext())
        assertTrue(LifecycleState.CLOSED.allowedNext().isEmpty())
    }

    @Test
    fun lifecycle_activeStates() {
        assertTrue(LifecycleState.OPEN.isActive)
        assertTrue(LifecycleState.IN_RESOLUTION.isActive)
        assertFalse(LifecycleState.RESOLVED.isActive)
        assertFalse(LifecycleState.CLOSED.isActive)
    }

    @Test
    fun anonymousArea_10km() {
        val center = AppConfig.ANONYMOUS_AREA_CENTER
        val nearby = LatLng(center.latitude + 0.015, center.longitude) // about 1.7 km north
        val lisbon = LatLng(38.7223, -9.1393)
        assertTrue(center.distanceTo(nearby) < AppConfig.ANONYMOUS_RADIUS_METERS)
        assertTrue(center.distanceTo(lisbon) > AppConfig.ANONYMOUS_RADIUS_METERS)
    }

    @Test
    fun publicLocation_roundedToTwoDecimals() {
        assertEquals(LatLng(40.21, -8.42), LatLng(40.21234, -8.41987).roundedForPublic())
    }
}
