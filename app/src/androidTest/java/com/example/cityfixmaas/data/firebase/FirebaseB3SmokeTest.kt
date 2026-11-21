package com.example.cityfixmaas.data.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cityfixmaas.data.model.LifecycleState
import com.example.cityfixmaas.data.model.PublicTicket
import com.example.cityfixmaas.data.model.User
import com.example.cityfixmaas.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks B3 against the real Firebase project (needs internet): ticket messages and the public
 * data used by anonymous users. Uses a throwaway account and deletes everything at the end.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseB3SmokeTest {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val messages = FirebaseMessageRepository(auth, db)
    private val publicData = FirebasePublicRepository(db)

    @Test
    fun messagesAndPublicData(): Unit = runBlocking {
        auth.createUserWithEmailAndPassword("b3-test-${System.currentTimeMillis()}@example.com", "test-pass-1").await()
        val uid = auth.currentUser!!.uid
        db.collection("users").document(uid).set(User(id = uid, role = UserRole.CITIZEN, name = "B3 Test")).await()
        val ticketId = "b3-test-$uid"

        // Messages: the sender's role comes from the profile.
        messages.sendMessage(ticketId, "Is anyone coming?").getOrThrow()
        val sent = waitFor(messages.observeMessages(ticketId)) { it.isNotEmpty() }.single()
        assertEquals(UserRole.CITIZEN, sent.senderRole)
        assertEquals("Is anyone coming?", sent.text)

        // Public data: a resolved ticket counts in the dashboard but is not in the active list.
        val now = System.currentTimeMillis()
        val resolved = PublicTicket(
            id = ticketId, categoryId = "cat-roads", lifecycleState = LifecycleState.RESOLVED,
            createdAt = now - 2 * 3_600_000, resolvedAt = now,
        )
        db.collection("publicTickets").document(ticketId).set(resolved).await()
        assertNotNull(waitFor(publicData.observePublicTicket(ticketId)) { it != null })
        val stats = waitFor(publicData.observeDashboard()) { it.totalTickets > 0 }
        assertNotNull(stats.averageRepairHours)
        assertTrue(publicData.observePublicTickets().first().none { it.id == ticketId })

        // Clean up.
        db.collection("publicTickets").document(ticketId).delete().await()
        db.collection("tickets").document(ticketId).collection("messages").document(sent.id).delete().await()
        db.collection("users").document(uid).delete().await()
        auth.currentUser!!.delete().await()
    }

    private suspend fun <T> waitFor(flow: Flow<T>, condition: (T) -> Boolean): T =
        withTimeout(30_000) { flow.first(condition) }
}
