package com.example.cityfixmaas.data.firebase

import com.example.cityfixmaas.data.model.Message
import com.example.cityfixmaas.data.model.User
import com.example.cityfixmaas.data.repository.MessageRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** Chat between the ticket's author and the operators, in tickets/{id}/messages. */
class FirebaseMessageRepository(private val auth: FirebaseAuth, private val db: FirebaseFirestore) : MessageRepository {

    private fun messages(ticketId: String) = db.collection("tickets").document(ticketId).collection("messages")

    override fun observeMessages(ticketId: String): Flow<List<Message>> = messages(ticketId)
        .snapshots()
        .map { result -> result.toObjects<Message>().sortedBy { it.sentAt } }
        .catch { emit(emptyList()) }

    override suspend fun sendMessage(ticketId: String, text: String): Result<Unit> = runCatching {
        require(text.isNotBlank()) { "Message is empty" }
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        // The role decides how the message is shown (operator messages are highlighted).
        val sender = db.collection("users").document(uid).get().await().toObject<User>() ?: error("Profile not found")
        val ref = messages(ticketId).document() // new random id
        val now = System.currentTimeMillis()
        val batch = db.batch() // not awaited: works offline
        batch.set(ref, Message(ref.id, uid, sender.role, text.trim(), now))
        // Lets the other side notice the new message from the ticket alone (see TicketWatcher).
        batch.update(db.collection("tickets").document(ticketId), mapOf("lastMessageAt" to now, "lastMessageRole" to sender.role.name))
        batch.commit()
        Unit
    }
}
