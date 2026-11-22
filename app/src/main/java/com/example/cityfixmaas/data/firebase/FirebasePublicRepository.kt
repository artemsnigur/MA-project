package com.example.cityfixmaas.data.firebase

import com.example.cityfixmaas.data.model.DashboardStats
import com.example.cityfixmaas.data.model.PublicTicket
import com.example.cityfixmaas.data.repository.PublicRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * What anonymous users may see: the publicTickets copies (no author, rounded location).
 * The dashboard is computed on the device from the same documents.
 */
class FirebasePublicRepository(db: FirebaseFirestore) : PublicRepository {

    private val publicTickets = db.collection("publicTickets")

    private val all: Flow<List<PublicTicket>> = publicTickets
        .snapshots()
        .map { result -> result.toObjects<PublicTicket>().sortedByDescending { it.createdAt } }
        .catch { emit(emptyList()) }

    override fun observePublicTickets(): Flow<List<PublicTicket>> =
        all.map { list -> list.filter { it.lifecycleState.isActive } }

    override fun observePublicTicket(id: String): Flow<PublicTicket?> = publicTickets.document(id)
        .snapshots()
        .map { it.toObject<PublicTicket>() }
        .catch { emit(null) }

    override fun observeDashboard(): Flow<DashboardStats> = all.map { list ->
        val repairHours = list.mapNotNull { t -> t.resolvedAt?.let { (it - t.createdAt) / 3_600_000.0 } }
        DashboardStats(
            totalTickets = list.size,
            activePercent = if (list.isEmpty()) 0.0 else 100.0 * list.count { it.lifecycleState.isActive } / list.size,
            averageRepairHours = repairHours.takeIf { it.isNotEmpty() }?.average(),
        )
    }
}
