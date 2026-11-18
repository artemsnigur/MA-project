package com.example.cityfixmaas.data.firebase

import com.example.cityfixmaas.data.model.MediaContent
import com.example.cityfixmaas.data.repository.MediaRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Full-size photos of a ticket, from tickets/{id}/media. */
class FirebaseMediaRepository(private val db: FirebaseFirestore) : MediaRepository {

    override fun observeMedia(ticketId: String): Flow<List<MediaContent>> = db.collection("tickets")
        .document(ticketId)
        .collection("media")
        .snapshots()
        .map { result -> result.toObjects<MediaContent>().sortedBy { it.createdAt } }
        .catch { emit(emptyList()) }
}
