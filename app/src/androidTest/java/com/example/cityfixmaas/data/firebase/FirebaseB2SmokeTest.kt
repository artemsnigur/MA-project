package com.example.cityfixmaas.data.firebase

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cityfixmaas.data.model.IncidentType
import com.example.cityfixmaas.data.model.LatLng
import com.example.cityfixmaas.data.model.LifecycleState
import com.example.cityfixmaas.data.model.MediaPhase
import com.example.cityfixmaas.data.model.PublicTicket
import com.example.cityfixmaas.data.model.TicketDraft
import com.example.cityfixmaas.data.model.UploadState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Checks B2 against the real Firebase project (needs internet): create a ticket with a photo,
 * let PhotoUploadWorker upload it, then move it to RESOLVED with a resolution photo.
 * Uses a throwaway account and deletes everything at the end.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseB2SmokeTest {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val tickets = FirebaseTicketRepository(context, auth, db)
    private val media = FirebaseMediaRepository(db)

    @Test
    fun createUploadAndResolve(): Unit = runBlocking {
        val email = "b2-test-${System.currentTimeMillis()}@example.com"
        auth.createUserWithEmailAndPassword(email, "test-pass-1").await()
        val uid = auth.currentUser!!.uid

        val draft = TicketDraft(
            categoryId = "cat-roads", incidentType = IncidentType.DAMAGE, description = "Test pothole",
            publicScope = true, location = LatLng(40.21234, -8.41987), photoUris = listOf(testPhoto(Color.RED)),
        )
        val created = tickets.createTicket(draft).getOrThrow()
        assertFalse(created.joinedExisting)
        val id = created.id

        // The ticket is saved at once with a PENDING photo; the worker then uploads it.
        val uploaded = waitFor(tickets.observeTicket(id)) { t -> t?.media?.all { it.uploadState == UploadState.DONE } == true }!!
        assertEquals(uid, uploaded.authorId)
        assertNotNull(uploaded.media.single().thumbnailBase64)
        assertEquals(1, waitFor(media.observeMedia(id)) { it.isNotEmpty() }.size)
        assertEquals(1, waitFor(tickets.observeMyTickets()) { list -> list.any { it.id == id } }.count { it.id == id })

        // The public copy has the thumbnail and a rounded location.
        val public = waitFor(publicTicket(id)) { it?.thumbnailBase64 != null }!!
        assertEquals(LatLng(40.21, -8.42), public.approxLocation)

        // A second citizen reporting the same problem 5 m away joins this ticket as a follower.
        auth.signOut()
        auth.createUserWithEmailAndPassword("b2-follower-${System.currentTimeMillis()}@example.com", "test-pass-1").await()
        val followerUid = auth.currentUser!!.uid
        val joined = tickets.createTicket(draft.copy(location = LatLng(40.21238, -8.41987))).getOrThrow()
        assertTrue(joined.joinedExisting)
        assertEquals(id, joined.id)
        assertTrue(waitFor(tickets.observeMyTickets()) { list -> list.any { it.id == id } }.isNotEmpty())
        auth.currentUser!!.delete().await()
        auth.signInWithEmailAndPassword(email, "test-pass-1").await()
        assertEquals(listOf(followerUid), waitFor(tickets.observeTicket(id)) { it?.followerIds?.isNotEmpty() == true }!!.followerIds)

        // Operator flow: in resolution → add resolution photo → resolved.
        tickets.changeState(id, LifecycleState.IN_RESOLUTION, "Team sent").getOrThrow()
        tickets.addResolutionPhotos(id, listOf(testPhoto(Color.GREEN))).getOrThrow()
        waitFor(tickets.observeTicket(id)) { t ->
            t?.media?.any { it.phase == MediaPhase.RESOLUTION && it.uploadState == UploadState.DONE } == true
        }
        tickets.changeState(id, LifecycleState.RESOLVED, null).getOrThrow()
        val resolved = waitFor(tickets.observeTicket(id)) { it?.lifecycleState == LifecycleState.RESOLVED }!!
        assertNotNull(resolved.resolvedAt)
        assertEquals(3, waitFor(tickets.observeHistory(id)) { it.size == 3 }.size)
        assertEquals(LifecycleState.RESOLVED, waitFor(publicTicket(id)) { it?.lifecycleState == LifecycleState.RESOLVED }!!.lifecycleState)

        // Clean up.
        val ticketRef = db.collection("tickets").document(id)
        for (sub in listOf("media", "history")) {
            ticketRef.collection(sub).get().await().documents.forEach { it.reference.delete().await() }
        }
        ticketRef.delete().await()
        db.collection("publicTickets").document(id).delete().await()
        auth.currentUser!!.delete().await()
    }

    private fun publicTicket(id: String): Flow<PublicTicket?> =
        db.collection("publicTickets").document(id).snapshots().map { it.toObject<PublicTicket>() }

    /** A small solid-colour JPEG in the cache folder, standing in for a camera photo. */
    private fun testPhoto(color: Int): Uri {
        val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val file = File(context.cacheDir, "test-$color.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(file)
    }

    private suspend fun <T> waitFor(flow: Flow<T>, condition: (T) -> Boolean): T =
        withTimeout(60_000) { flow.first(condition) }
}
