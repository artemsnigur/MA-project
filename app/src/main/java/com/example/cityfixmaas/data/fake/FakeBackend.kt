package com.example.cityfixmaas.data.fake

import com.example.cityfixmaas.data.AppConfig
import com.example.cityfixmaas.data.model.AccountStatus
import com.example.cityfixmaas.data.model.Category
import com.example.cityfixmaas.data.model.IncidentType
import com.example.cityfixmaas.data.model.LatLng
import com.example.cityfixmaas.data.model.LifecycleState
import com.example.cityfixmaas.data.model.MediaContent
import com.example.cityfixmaas.data.model.MediaPhase
import com.example.cityfixmaas.data.model.MediaRef
import com.example.cityfixmaas.data.model.Message
import com.example.cityfixmaas.data.model.StateChange
import com.example.cityfixmaas.data.model.Ticket
import com.example.cityfixmaas.data.model.UploadState
import com.example.cityfixmaas.data.model.User
import com.example.cityfixmaas.data.model.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

/**
 * In-memory stand-in for Firestore, shared by all fake repositories so that a change made
 * through one (e.g. a new ticket) shows up in the others (e.g. the public list).
 *
 * Demo accounts (password for all: "password"):
 * - ana@cm-coimbra.pt   operator
 * - rui@example.com     active citizen, author of the sample tickets
 * - marta@example.com   pending citizen
 */
class FakeBackend {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val users = MutableStateFlow(seedUsers().associateBy { it.id })
    val passwords = mutableMapOf(
        "ana@cm-coimbra.pt" to DEMO_PASSWORD,
        "rui@example.com" to DEMO_PASSWORD,
        "marta@example.com" to DEMO_PASSWORD,
    )
    val currentUid = MutableStateFlow<String?>(null)

    val categories = MutableStateFlow(seedCategories().associateBy { it.id })
    val tickets = MutableStateFlow(seedTickets().associateBy { it.id })
    val history = MutableStateFlow(seedHistory())
    val messages = MutableStateFlow(seedMessages())
    val media = MutableStateFlow(seedMedia())

    fun currentUser(): User? = currentUid.value?.let { users.value[it] }

    fun requireUser(): User = currentUser() ?: throw IllegalStateException("Not signed in")

    fun requireOperator(): User = requireUser().also {
        check(it.role == UserRole.OPERATOR) { "Only operators can do this" }
    }

    companion object {
        const val DEMO_PASSWORD = "password"

        /** Valid 1×1 PNG, so image decoding code paths work with fake data. */
        const val SAMPLE_IMAGE_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="

        fun newId(): String = UUID.randomUUID().toString()

        fun now(): Long = System.currentTimeMillis()

        private const val HOUR = 3_600_000L
        private const val DAY = 24 * HOUR

        private fun near(dLat: Double, dLng: Double) = LatLng(
            AppConfig.ANONYMOUS_AREA_CENTER.latitude + dLat,
            AppConfig.ANONYMOUS_AREA_CENTER.longitude + dLng,
        )

        private fun seedUsers() = listOf(
            User(
                id = "op-ana", role = UserRole.OPERATOR, status = AccountStatus.ACTIVE,
                name = "Ana Operator", email = "ana@cm-coimbra.pt", phone = "912345678",
                nif = "123456789", createdAt = now() - 30 * DAY,
            ),
            User(
                id = "cit-rui", role = UserRole.CITIZEN, status = AccountStatus.ACTIVE,
                name = "Rui Citizen", email = "rui@example.com", phone = "913456789",
                nif = "234567890", location = near(0.004, 0.003), createdAt = now() - 20 * DAY,
            ),
            User(
                id = "cit-marta", role = UserRole.CITIZEN, status = AccountStatus.PENDING,
                name = "Marta Pending", email = "marta@example.com", phone = "914567890",
                nif = "345678901", location = near(-0.006, 0.002), createdAt = now() - 1 * DAY,
            ),
        )

        private fun seedCategories() = listOf(
            Category("cat-roads", "Public Roads", active = true),
            Category("cat-water", "Water", active = true),
            Category("cat-lighting", "Lighting", active = true),
            Category("cat-sanitation", "Sanitation", active = true),
            Category("cat-signs", "Road Signs", active = false),
        )

        private fun ref(id: String, phase: MediaPhase = MediaPhase.REPORT, at: Long) = MediaRef(
            id = id, thumbnailBase64 = SAMPLE_IMAGE_BASE64, phase = phase,
            uploadState = UploadState.DONE, createdAt = at,
        )

        private fun seedTickets(): List<Ticket> {
            val t = now()
            return listOf(
                Ticket(
                    id = "t-pothole", authorId = "cit-rui", categoryId = "cat-roads",
                    incidentType = IncidentType.DAMAGE, lifecycleState = LifecycleState.OPEN,
                    publicScope = true, description = "Deep pothole in the right lane near the bus stop.",
                    location = near(0.002, -0.001), media = listOf(ref("m-pothole", at = t - 2 * HOUR)),
                    createdAt = t - 2 * HOUR, updatedAt = t - 2 * HOUR,
                ),
                Ticket(
                    id = "t-lamp", authorId = "cit-rui", categoryId = "cat-lighting",
                    incidentType = IncidentType.BREAKDOWN, lifecycleState = LifecycleState.IN_RESOLUTION,
                    publicScope = true, description = "Street lamp has been off for a week.",
                    location = near(-0.003, 0.004), media = listOf(ref("m-lamp", at = t - 3 * DAY)),
                    createdAt = t - 3 * DAY, updatedAt = t - 1 * DAY,
                ),
                Ticket(
                    id = "t-bins", authorId = "cit-rui", categoryId = "cat-sanitation",
                    incidentType = IncidentType.THEFT, lifecycleState = LifecycleState.INFORMATION_REQUEST,
                    publicScope = true, description = "Two recycling bins were taken from the corner.",
                    location = near(0.006, 0.005), media = listOf(ref("m-bins", at = t - 2 * DAY)),
                    createdAt = t - 2 * DAY, updatedAt = t - 20 * HOUR,
                ),
                Ticket(
                    id = "t-leak", authorId = "cit-rui", categoryId = "cat-water",
                    incidentType = IncidentType.DESTRUCTION, lifecycleState = LifecycleState.RESOLVED,
                    publicScope = false, description = "Broken water pipe flooding the pavement.",
                    location = near(-0.001, -0.005),
                    media = listOf(
                        ref("m-leak", at = t - 6 * DAY),
                        ref("m-leak-fixed", MediaPhase.RESOLUTION, at = t - 2 * DAY),
                    ),
                    createdAt = t - 6 * DAY, updatedAt = t - 2 * DAY, resolvedAt = t - 2 * DAY,
                ),
            )
        }

        private fun seedHistory(): Map<String, List<StateChange>> {
            val t = now()
            fun open(at: Long) = StateChange(newId(), null, LifecycleState.OPEN, "cit-rui", at)
            return mapOf(
                "t-pothole" to listOf(open(t - 2 * HOUR)),
                "t-lamp" to listOf(
                    open(t - 3 * DAY),
                    StateChange(newId(), LifecycleState.OPEN, LifecycleState.IN_RESOLUTION, "op-ana", t - 1 * DAY, "Team dispatched"),
                ),
                "t-bins" to listOf(
                    open(t - 2 * DAY),
                    StateChange(newId(), LifecycleState.OPEN, LifecycleState.INFORMATION_REQUEST, "op-ana", t - 20 * HOUR, "Which bin colours were taken?"),
                ),
                "t-leak" to listOf(
                    open(t - 6 * DAY),
                    StateChange(newId(), LifecycleState.OPEN, LifecycleState.IN_RESOLUTION, "op-ana", t - 5 * DAY),
                    StateChange(newId(), LifecycleState.IN_RESOLUTION, LifecycleState.RESOLVED, "op-ana", t - 2 * DAY, "Pipe replaced"),
                ),
            )
        }

        private fun seedMessages(): Map<String, List<Message>> {
            val t = now()
            return mapOf(
                "t-bins" to listOf(
                    Message(newId(), "op-ana", UserRole.OPERATOR, "Hello, which bin colours were taken?", t - 20 * HOUR),
                    Message(newId(), "cit-rui", UserRole.CITIZEN, "The yellow and the blue ones.", t - 18 * HOUR),
                ),
            )
        }

        private fun seedMedia(): Map<String, List<MediaContent>> {
            val t = now()
            fun content(id: String, ticketId: String, phase: MediaPhase = MediaPhase.REPORT) =
                MediaContent(id, ticketId, SAMPLE_IMAGE_BASE64, phase, t)
            return mapOf(
                "t-pothole" to listOf(content("m-pothole", "t-pothole")),
                "t-lamp" to listOf(content("m-lamp", "t-lamp")),
                "t-bins" to listOf(content("m-bins", "t-bins")),
                "t-leak" to listOf(
                    content("m-leak", "t-leak"),
                    content("m-leak-fixed", "t-leak", MediaPhase.RESOLUTION),
                ),
            )
        }
    }
}
