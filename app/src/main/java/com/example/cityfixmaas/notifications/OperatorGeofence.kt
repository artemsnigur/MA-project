package com.example.cityfixmaas.notifications

import com.example.cityfixmaas.data.AppConfig
import com.example.cityfixmaas.data.distanceTo
import com.example.cityfixmaas.data.model.LifecycleState
import com.example.cityfixmaas.data.repository.LocationProvider
import com.example.cityfixmaas.data.repository.TicketRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * The 20 m geofence of tickets in resolution. Runs on the operator's phone while the app is on
 * screen: every 30 s it takes the GPS position and marks the tickets in resolution within 20 m.
 * The citizens' apps see the mark and show a notification (TicketWatcher).
 */
class OperatorGeofence(private val tickets: TicketRepository, private val location: LocationProvider) {

    suspend fun run() {
        while (true) {
            val here = location.currentLocation()
            if (here != null) {
                val now = System.currentTimeMillis()
                tickets.observeAllTickets().first()
                    .filter { it.lifecycleState == LifecycleState.IN_RESOLUTION }
                    .filter { it.location.distanceTo(here) <= AppConfig.OPERATOR_GEOFENCE_METERS }
                    .filter { now - it.operatorNearbyAt > REPEAT_AFTER_MS } // do not notify again while the team stays there
                    .forEach { tickets.markOperatorNearby(it.id) }
            }
            delay(CHECK_EVERY_MS)
        }
    }

    private companion object {
        const val CHECK_EVERY_MS = 30_000L
        const val REPEAT_AFTER_MS = 60 * 60_000L
    }
}
