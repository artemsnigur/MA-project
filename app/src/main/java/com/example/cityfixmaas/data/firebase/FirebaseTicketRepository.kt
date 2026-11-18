package com.example.cityfixmaas.data.firebase

import android.content.Context
import android.net.Uri
import com.example.cityfixmaas.data.model.LifecycleState
import com.example.cityfixmaas.data.model.MediaPhase
import com.example.cityfixmaas.data.model.MediaRef
import com.example.cityfixmaas.data.model.PublicTicket
import com.example.cityfixmaas.data.model.StateChange
import com.example.cityfixmaas.data.model.Ticket
import com.example.cityfixmaas.data.model.TicketDraft
import com.example.cityfixmaas.data.model.UploadState
import com.example.cityfixmaas.data.repository.TicketRepository
import com.example.cityfixmaas.data.roundedForPublic
import com.example.cityfixmaas.worker.PhotoUploadWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class FirebaseTicketRepository(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : TicketRepository {

    private val tickets = db.collection("tickets")
    private val publicTickets = db.collection("publicTickets")

    override fun observeMyTickets(): Flow<List<Ticket>> {
        val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
        return tickets.whereEqualTo("authorId", uid)
            .snapshots()
            .map { result -> result.toObjects<Ticket>().sortedByDescending { it.createdAt } }
            .catch { emit(emptyList()) }
    }

    override fun observeAllTickets(): Flow<List<Ticket>> = tickets
        .snapshots()
        .map { result -> result.toObjects<Ticket>().sortedByDescending { it.createdAt } }
        .catch { emit(emptyList()) }

    override fun observeTicket(id: String): Flow<Ticket?> = tickets.document(id)
        .snapshots()
        .map { it.toObject<Ticket>() }
        .catch { emit(null) }

    override fun observeHistory(ticketId: String): Flow<List<StateChange>> = tickets.document(ticketId)
        .collection("history")
        .snapshots()
        .map { result -> result.toObjects<StateChange>().sortedBy { it.at } }
        .catch { emit(emptyList()) }

    /**
     * Works offline: photos are copied into app storage, the ticket is written to the local
     * Firestore cache (synced when online) and the photo upload waits for a connection.
     */
    override suspend fun createTicket(draft: TicketDraft): Result<String> = runCatching {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        require(draft.categoryId.isNotBlank()) { "Choose a category" }
        require(draft.description.isNotBlank()) { "Description is required" }
        require(draft.photoUris.isNotEmpty()) { "Add at least one photo" }

        val now = System.currentTimeMillis()
        val ticketRef = tickets.document() // new random id
        val photos = savePhotos(draft.photoUris, MediaPhase.REPORT, now)
        val ticket = Ticket(
            id = ticketRef.id,
            authorId = uid,
            categoryId = draft.categoryId,
            incidentType = draft.incidentType,
            lifecycleState = LifecycleState.OPEN,
            publicScope = draft.publicScope,
            description = draft.description.trim(),
            location = draft.location,
            media = photos,
            createdAt = now,
            updatedAt = now,
        )
        val firstChange = StateChange(UUID.randomUUID().toString(), null, LifecycleState.OPEN, uid, now)

        // One batch: the ticket, its public copy and the first history entry are saved together.
        val batch = db.batch()
        batch.set(ticketRef, ticket)
        batch.set(ticketRef.collection("history").document(firstChange.id), firstChange)
        if (ticket.publicScope) batch.set(publicTickets.document(ticket.id), ticket.toPublic())
        batch.commit() // not awaited: offline it would wait until the connection is back

        PhotoUploadWorker.enqueue(context, ticket.id)
        ticket.id
    }

    override suspend fun changeState(ticketId: String, to: LifecycleState, note: String?): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        val ticketRef = tickets.document(ticketId)
        val ticket = ticketRef.get().await().toObject<Ticket>() ?: error("Ticket not found")
        require(to in ticket.lifecycleState.allowedNext()) { "Cannot move from ${ticket.lifecycleState} to $to" }
        if (to == LifecycleState.RESOLVED) {
            require(ticket.media.any { it.phase == MediaPhase.RESOLUTION }) { "Add photos of the completed work first" }
        }

        val now = System.currentTimeMillis()
        val changes = mutableMapOf<String, Any>("lifecycleState" to to.name, "updatedAt" to now)
        if (to == LifecycleState.RESOLVED) changes["resolvedAt"] = now
        val change = StateChange(UUID.randomUUID().toString(), ticket.lifecycleState, to, uid, now, note?.trim())

        val batch = db.batch()
        batch.update(ticketRef, changes)
        batch.set(ticketRef.collection("history").document(change.id), change)
        if (ticket.publicScope) batch.update(publicTickets.document(ticketId), changes - "updatedAt")
        batch.commit()
        Unit
    }

    override suspend fun addResolutionPhotos(ticketId: String, photoUris: List<Uri>): Result<Unit> = runCatching {
        require(photoUris.isNotEmpty()) { "Add at least one photo" }
        val photos = savePhotos(photoUris, MediaPhase.RESOLUTION, System.currentTimeMillis())
        tickets.document(ticketId).update(
            mapOf("media" to FieldValue.arrayUnion(*photos.toTypedArray()), "updatedAt" to System.currentTimeMillis()),
        )
        PhotoUploadWorker.enqueue(context, ticketId)
    }

    override suspend fun retryUploads(ticketId: String) {
        PhotoUploadWorker.enqueue(context, ticketId)
    }

    /** Copies the photos into app storage and returns their PENDING entries for the ticket. */
    private suspend fun savePhotos(uris: List<Uri>, phase: MediaPhase, now: Long): List<MediaRef> =
        withContext(Dispatchers.IO) {
            uris.map { uri ->
                val mediaId = UUID.randomUUID().toString()
                PhotoUploadWorker.saveLocally(context, uri, mediaId)
                MediaRef(id = mediaId, phase = phase, uploadState = UploadState.PENDING, createdAt = now)
            }
        }

    /** Public copy without the author and with an approximate location. */
    private fun Ticket.toPublic() = PublicTicket(
        id = id,
        categoryId = categoryId,
        incidentType = incidentType,
        lifecycleState = lifecycleState,
        thumbnailBase64 = null, // filled in by PhotoUploadWorker
        approxLocation = location.roundedForPublic(),
        createdAt = createdAt,
        resolvedAt = resolvedAt,
    )
}
