package com.example.cityfixmaas.data.firebase

import com.example.cityfixmaas.data.model.UserRole
import com.example.cityfixmaas.data.model.Ticket
import com.example.cityfixmaas.push.PushSender
import com.example.cityfixmaas.push.PushKind
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

/** Chat between the ticket's citizens and the operators, in tickets/{id}/messages. */
class FirebaseMessageRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val push: PushSender,
) : MessageRepository {

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
        ref.set(Message(ref.id, uid, sender.role, text.trim(), System.currentTimeMillis())) // not awaited: works offline

        // Push to the other side: operator -> the ticket's citizens, citizen -> the operators.
        val ticket = db.collection("tickets").document(ticketId).get().await().toObject<Ticket>() ?: return@runCatching
        if (sender.role == UserRole.OPERATOR) push.toCitizens(ticket, PushKind.MESSAGE) else push.toOperators(ticket, PushKind.MESSAGE)
    }
}
