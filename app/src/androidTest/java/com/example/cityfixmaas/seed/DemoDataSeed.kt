package com.example.cityfixmaas.seed

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cityfixmaas.data.firebase.FirebaseAuthRepository
import com.example.cityfixmaas.data.firebase.FirebaseMessageRepository
import com.example.cityfixmaas.data.firebase.FirebaseTicketRepository
import com.example.cityfixmaas.data.model.IncidentType
import com.example.cityfixmaas.data.model.LatLng
import com.example.cityfixmaas.data.model.LifecycleState
import com.example.cityfixmaas.data.model.RegistrationForm
import com.example.cityfixmaas.data.model.StateChange
import com.example.cityfixmaas.data.model.TicketDraft
import com.example.cityfixmaas.data.model.UploadState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * NOT a test: a one-off tool that fills the live Firebase project with demo data through the
 * real repositories. It is skipped unless started on purpose with seed=true:
 *   ./gradlew connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.example.cityfixmaas.seed.DemoDataSeed \
 *     -Pandroid.testInstrumentationRunnerArguments.seed=true \
 *     -Pandroid.testInstrumentationRunnerArguments.password=<demo accounts password>
 *
 * Needs the demo accounts operator@cm-coimbra.pt and rui.demo@example.com (same password, passed
 * as an argument so it is not stored in the repository).
 * Creates six tickets of Rui in every lifecycle state, a short chat and one pending citizen.
 */
@RunWith(AndroidJUnit4::class)
class DemoDataSeed {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val tickets = FirebaseTicketRepository(context, auth, db)
    private val messages = FirebaseMessageRepository(auth, db)
    private val day = 24 * 3_600_000L

    private class Demo(
        val category: String, val type: IncidentType, val text: String, val public: Boolean,
        val where: LatLng, val color: Int, val daysAgo: Int, val repairHours: Int? = null,
    )

    @Test
    fun seed(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("seed") == "true")
        val password = InstrumentationRegistry.getArguments().getString("password") ?: error("Pass password=...")
        val demos = listOf(
            Demo("cat-roads", IncidentType.DAMAGE, "Deep pothole on Rua da Sofia, next to the bus stop.", true, LatLng(40.2114, -8.4290), Color.DKGRAY, 1),
            Demo("cat-lighting", IncidentType.BREAKDOWN, "Street lamp not working on Avenida Sá da Bandeira.", true, LatLng(40.2095, -8.4220), Color.rgb(40, 40, 90), 3),
            Demo("cat-water", IncidentType.BREAKDOWN, "Water leaking from a broken pipe on Rua Ferreira Borges.", true, LatLng(40.2085, -8.4290), Color.rgb(30, 90, 160), 2),
            Demo("cat-sanitation", IncidentType.DAMAGE, "Overflowing recycling bins at Largo da Portagem.", true, LatLng(40.2075, -8.4305), Color.rgb(30, 110, 60), 6, repairHours = 30),
            Demo("cat-roads", IncidentType.THEFT, "Stop sign stolen at the Celas roundabout.", true, LatLng(40.2165, -8.4130), Color.rgb(160, 40, 40), 10, repairHours = 20),
            Demo("cat-sanitation", IncidentType.DESTRUCTION, "Graffiti on the entrance wall of my building.", false, LatLng(40.2050, -8.4180), Color.rgb(120, 60, 140), 1),
        )

        // 1. The citizen reports everything.
        auth.signInWithEmailAndPassword("rui.demo@example.com", password).await()
        val ids = demos.mapIndexed { i, d ->
            tickets.createTicket(TicketDraft(d.category, d.type, d.text, d.public, d.where, listOf(photo("report$i", d.color)))).getOrThrow()
        }
        ids.forEach { waitUploaded(it) }

        // 2. The operator works on them.
        db.waitForPendingWrites().await() // writes are not awaited in the repositories
        auth.signOut()
        auth.signInWithEmailAndPassword("operator@cm-coimbra.pt", password).await()
        tickets.changeState(ids[1], LifecycleState.INFORMATION_REQUEST, "Which lamp post?").getOrThrow()
        messages.sendMessage(ids[1], "Hello! Could you tell us the number written on the lamp post?").getOrThrow()
        tickets.changeState(ids[2], LifecycleState.IN_RESOLUTION, "Repair team dispatched").getOrThrow()
        for (i in listOf(3, 4)) {
            tickets.changeState(ids[i], LifecycleState.IN_RESOLUTION, "Team on site").getOrThrow()
            tickets.addResolutionPhotos(ids[i], listOf(photo("fixed$i", Color.rgb(90, 160, 90)))).getOrThrow()
            waitUploaded(ids[i])
            tickets.changeState(ids[i], LifecycleState.RESOLVED, "Work completed").getOrThrow()
        }
        tickets.changeState(ids[4], LifecycleState.CLOSED, null).getOrThrow()

        // 3. The citizen answers the question.
        db.waitForPendingWrites().await() // writes are not awaited in the repositories
        auth.signOut()
        auth.signInWithEmailAndPassword("rui.demo@example.com", password).await()
        messages.sendMessage(ids[1], "It is number 27, in front of the pharmacy.").getOrThrow()

        // 4. Spread the dates over the last days, so the dashboard shows a real average repair time.
        db.waitForPendingWrites().await()
        demos.forEachIndexed { i, d -> backdate(ids[i], System.currentTimeMillis() - d.daysAgo * day, d.repairHours) }

        // 5. One citizen waiting for activation.
        db.waitForPendingWrites().await() // writes are not awaited in the repositories
        auth.signOut()
        FirebaseAuthRepository(context, auth, db, CoroutineScope(Dispatchers.Default)).registerCitizen(
            RegistrationForm(
                name = "Joana Pereira", email = "joana.demo@example.com", password = password,
                phone = "914567890", nif = "678901236", location = LatLng(40.2120, -8.4250),
                photoUri = null, impactDetectionEnabled = false,
            ),
        ).getOrThrow()
        db.waitForPendingWrites().await() // writes are not awaited in the repositories
        auth.signOut()
    }

    /** Moves createdAt (and resolvedAt) back in time and spreads the history entries between them. */
    private suspend fun backdate(id: String, createdAt: Long, repairHours: Int?) {
        val resolvedAt = repairHours?.let { createdAt + it * 3_600_000L }
        val ticketRef = db.collection("tickets").document(id)
        val changes = mutableMapOf<String, Any>("createdAt" to createdAt)
        if (resolvedAt != null) changes["resolvedAt"] = resolvedAt
        ticketRef.update(changes).await()
        val publicRef = db.collection("publicTickets").document(id)
        if (publicRef.get().await().exists()) publicRef.update(changes).await()

        val end = resolvedAt ?: (createdAt + 6 * 3_600_000L)
        val history = ticketRef.collection("history").get().await().toObjects<StateChange>().sortedBy { it.at }
        history.forEachIndexed { i, change ->
            val at = if (history.size == 1) createdAt else createdAt + (end - createdAt) * i / (history.size - 1)
            ticketRef.collection("history").document(change.id).update("at", at).await()
        }
    }

    private suspend fun waitUploaded(id: String) = withTimeout(120_000) {
        tickets.observeTicket(id).first { t -> t != null && t.media.all { it.uploadState == UploadState.DONE } }
    }

    /** A plain picture with a coloured background, standing in for a camera photo. */
    private fun photo(name: String, background: Int): Uri {
        val bitmap = Bitmap.createBitmap(1280, 960, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        val paint = Paint().apply { color = Color.argb(60, 255, 255, 255) }
        for (x in 0 until 1280 step 160) canvas.drawRect(x.toFloat(), 700f, x + 90f, 720f, paint) // road marks
        val file = File(context.cacheDir, "$name.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return Uri.fromFile(file)
    }
}
