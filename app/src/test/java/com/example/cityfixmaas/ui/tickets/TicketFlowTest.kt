package com.example.cityfixmaas.ui.tickets

import com.example.cityfixmaas.data.model.IncidentType
import com.example.cityfixmaas.data.model.LifecycleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketFlowTest {

    @Test
    fun lifecycleState_allowedNextTransitions() {
        // OPEN can move to INFORMATION_REQUEST or IN_RESOLUTION
        assertEquals(
            setOf(LifecycleState.INFORMATION_REQUEST, LifecycleState.IN_RESOLUTION),
            LifecycleState.OPEN.allowedNext().toSet(),
        )

        // INFORMATION_REQUEST can move to OPEN or IN_RESOLUTION
        assertEquals(
            setOf(LifecycleState.OPEN, LifecycleState.IN_RESOLUTION),
            LifecycleState.INFORMATION_REQUEST.allowedNext().toSet(),
        )

        // IN_RESOLUTION can move to RESOLVED
        assertEquals(
            setOf(LifecycleState.RESOLVED),
            LifecycleState.IN_RESOLUTION.allowedNext().toSet(),
        )

        // RESOLVED can move to CLOSED
        assertEquals(
            setOf(LifecycleState.CLOSED),
            LifecycleState.RESOLVED.allowedNext().toSet(),
        )

        // CLOSED is terminal
        assertTrue(LifecycleState.CLOSED.allowedNext().isEmpty())
    }

    @Test
    fun lifecycleState_isActiveProperty() {
        assertTrue(LifecycleState.OPEN.isActive)
        assertTrue(LifecycleState.INFORMATION_REQUEST.isActive)
        assertTrue(LifecycleState.IN_RESOLUTION.isActive)
        assertFalse(LifecycleState.RESOLVED.isActive)
        assertFalse(LifecycleState.CLOSED.isActive)
    }

    @Test
    fun enumLabels_returnNonZeroResourceIds() {
        LifecycleState.entries.forEach { state ->
            assertNotEquals(0, state.labelRes())
        }
        IncidentType.entries.forEach { type ->
            assertNotEquals(0, type.labelRes())
        }
    }

    @Test
    fun formatTime_formatsValidDate() {
        val now = 1732000000000L
        val formatted = formatTime(now)
        assertTrue(formatted.isNotBlank())
    }
}
