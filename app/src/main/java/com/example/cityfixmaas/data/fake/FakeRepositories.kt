package com.example.cityfixmaas.data.fake

import android.net.Uri
import com.example.cityfixmaas.data.AppConfig
import com.example.cityfixmaas.data.distanceTo
import com.example.cityfixmaas.data.fake.FakeBackend.Companion.SAMPLE_IMAGE_BASE64
import com.example.cityfixmaas.data.fake.FakeBackend.Companion.newId
import com.example.cityfixmaas.data.fake.FakeBackend.Companion.now
import com.example.cityfixmaas.data.model.AccountStatus
import com.example.cityfixmaas.data.model.Category
import com.example.cityfixmaas.data.model.DashboardStats
import com.example.cityfixmaas.data.model.LatLng
import com.example.cityfixmaas.data.model.LifecycleState
import com.example.cityfixmaas.data.model.MediaContent
import com.example.cityfixmaas.data.model.MediaPhase
import com.example.cityfixmaas.data.model.MediaRef
import com.example.cityfixmaas.data.model.Message
import com.example.cityfixmaas.data.model.PublicTicket
import com.example.cityfixmaas.data.model.RegistrationForm
import com.example.cityfixmaas.data.model.SessionState
import com.example.cityfixmaas.data.model.StateChange
import com.example.cityfixmaas.data.model.Ticket
import com.example.cityfixmaas.data.model.TicketDraft
import com.example.cityfixmaas.data.model.UploadState
import com.example.cityfixmaas.data.model.User
import com.example.cityfixmaas.data.model.UserRole
import com.example.cityfixmaas.data.repository.AuthRepository
import com.example.cityfixmaas.data.repository.CategoryRepository
import com.example.cityfixmaas.data.repository.LocationProvider
import com.example.cityfixmaas.data.repository.MediaRepository
import com.example.cityfixmaas.data.repository.MessageRepository
import com.example.cityfixmaas.data.repository.PublicRepository
import com.example.cityfixmaas.data.repository.TicketRepository
import com.example.cityfixmaas.data.repository.UserAdminRepository
import com.example.cityfixmaas.data.roundedForPublic
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Simulated network latency, so loading states are visible in the UI. */
private suspend fun latency() = delay(400)

private inline fun <T> attempt(block: () -> T): Result<T> = runCatching(block)

class FakeAuthRepository(private val backend: FakeBackend) : AuthRepository {

    override val session: StateFlow<SessionState> =
        combine(backend.currentUid, backend.users) { uid, users ->
            val user = uid?.let { users[it] }
            if (user == null) SessionState.Anonymous else SessionState.SignedIn(user)
        }.stateIn(backend.scope, SharingStarted.Eagerly, SessionState.Anonymous)

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        latency()
        return attempt {
            val user = backend.users.value.values.firstOrNull { it.email.equals(email.trim(), ignoreCase = true) }
            require(user != null && backend.passwords[user.email] == password) { "Wrong email or password" }
            backend.currentUid.value = user.id
        }
    }

    override suspend fun registerCitizen(form: RegistrationForm): Result<Unit> {
        latency()
        return attempt { register(form, UserRole.CITIZEN, AccountStatus.PENDING) }
    }

    override suspend fun registerOperator(form: RegistrationForm): Result<Unit> {
        latency()
        return attempt { register(form.copy(location = null), UserRole.OPERATOR, AccountStatus.ACTIVE) }
    }

    private fun register(form: RegistrationForm, role: UserRole, status: AccountStatus) {
        val email = form.email.trim().lowercase()
        val users = backend.users.value.values
        require(users.none { it.email == email }) { "This email is already registered" }
        require(users.none { it.role == role && it.nif == form.nif }) { "This NIF is already registered" }
        val user = User(
            id = newId(), role = role, status = status, name = form.name.trim(), email = email,
            phone = form.phone.trim(), nif = form.nif.trim(), location = form.location,
            photoBase64 = form.photoUri?.let { SAMPLE_IMAGE_BASE64 },
            impactDetectionEnabled = form.impactDetectionEnabled, createdAt = now(),
        )
        backend.users.update { it + (user.id to user) }
        backend.passwords[email] = form.password
        backend.currentUid.value = user.id
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        latency()
        return Result.success(Unit)
    }

    override suspend fun updateProfile(phone: String, photoUri: Uri?): Result<Unit> {
        latency()
        return attempt {
            val user = backend.requireUser()
            val updated = user.copy(
                phone = phone.trim(),
                photoBase64 = if (photoUri != null) SAMPLE_IMAGE_BASE64 else user.photoBase64,
            )
            backend.users.update { it + (user.id to updated) }
        }
    }

    override suspend fun signOut() {
        backend.currentUid.value = null
    }
}

class FakeUserAdminRepository(private val backend: FakeBackend) : UserAdminRepository {

    override fun observePendingCitizens(): Flow<List<User>> = backend.users.map { users ->
        users.values
            .filter { it.role == UserRole.CITIZEN && it.status == AccountStatus.PENDING }
            .sortedBy { it.createdAt }
    }

    override fun observeUser(uid: String): Flow<User?> = backend.users.map { it[uid] }

    override suspend fun activateCitizen(uid: String): Result<Unit> {
        latency()
        return attempt {
            backend.requireOperator()
            val user = requireNotNull(backend.users.value[uid]) { "User not found" }
            backend.users.update { it + (uid to user.copy(status = AccountStatus.ACTIVE)) }
        }
    }
}

class FakeCategoryRepository(private val backend: FakeBackend) : CategoryRepository {

    override fun observeCategories(activeOnly: Boolean): Flow<List<Category>> =
        backend.categories.map { categories ->
            categories.values.filter { !activeOnly || it.active }.sortedBy { it.name }
        }

    override suspend fun saveCategory(category: Category): Result<Unit> {
        latency()
        return attempt {
            backend.requireOperator()
            require(category.name.isNotBlank()) { "Name is required" }
            val saved = if (category.id.isBlank()) category.copy(id = newId()) else category
            backend.categories.update { it + (saved.id to saved) }
        }
    }

    override suspend fun setActive(id: String, active: Boolean): Result<Unit> {
        latency()
        return attempt {
            backend.requireOperator()
            val category = requireNotNull(backend.categories.value[id]) { "Category not found" }
            backend.categories.update { it + (id to category.copy(active = active)) }
        }
    }
}

class FakeTicketRepository(private val backend: FakeBackend) : TicketRepository {

    override fun observeMyTickets(): Flow<List<Ticket>> =
        combine(backend.tickets, backend.currentUid) { tickets, uid ->
            tickets.values.filter { it.authorId == uid }.sortedByDescending { it.createdAt }
        }

    override fun observeAllTickets(): Flow<List<Ticket>> =
        backend.tickets.map { tickets -> tickets.values.sortedByDescending { it.createdAt } }

    override fun observeTicket(id: String): Flow<Ticket?> = backend.tickets.map { it[id] }

    override fun observeHistory(ticketId: String): Flow<List<StateChange>> =
        backend.history.map { it[ticketId].orEmpty().sortedBy { change -> change.at } }

    override suspend fun createTicket(draft: TicketDraft): Result<String> {
        latency()
        return attempt {
            val user = backend.requireUser()
            check(user.role == UserRole.CITIZEN && user.status == AccountStatus.ACTIVE) {
                "Only active citizens can create tickets"
            }
            require(draft.description.isNotBlank()) { "Description is required" }
            require(draft.photoUris.isNotEmpty()) { "Add at least one photo" }
            require(backend.categories.value[draft.categoryId]?.active == true) { "Choose an active category" }

            val id = newId()
            val at = now()
            val photos = draft.photoUris.map { newMedia(id, MediaPhase.REPORT, at) }
            val ticket = Ticket(
                id = id, authorId = user.id, categoryId = draft.categoryId,
                incidentType = draft.incidentType, lifecycleState = LifecycleState.OPEN,
                publicScope = draft.publicScope, description = draft.description.trim(),
                location = draft.location, media = photos.map { it.first },
                createdAt = at, updatedAt = at,
            )
            backend.tickets.update { it + (id to ticket) }
            backend.media.update { it + (id to photos.map { p -> p.second }) }
            appendHistory(id, StateChange(newId(), null, LifecycleState.OPEN, user.id, at))
            id
        }
    }

    override suspend fun changeState(ticketId: String, to: LifecycleState, note: String?): Result<Unit> {
        latency()
        return attempt {
            // P0: only operators drive the lifecycle. Citizen reopening (RESOLVED -> OPEN) is P1.
            val user = backend.requireOperator()
            val ticket = requireNotNull(backend.tickets.value[ticketId]) { "Ticket not found" }
            require(to in ticket.lifecycleState.allowedNext()) {
                "Cannot move from ${ticket.lifecycleState} to $to"
            }
            if (to == LifecycleState.RESOLVED) {
                require(ticket.media.any { it.phase == MediaPhase.RESOLUTION }) {
                    "Add photos of the completed work first"
                }
            }
            val at = now()
            val updated = ticket.copy(
                lifecycleState = to,
                updatedAt = at,
                resolvedAt = if (to == LifecycleState.RESOLVED) at else ticket.resolvedAt,
            )
            backend.tickets.update { it + (ticketId to updated) }
            appendHistory(ticketId, StateChange(newId(), ticket.lifecycleState, to, user.id, at, note?.takeIf { it.isNotBlank() }))
        }
    }

    override suspend fun addResolutionPhotos(ticketId: String, photoUris: List<Uri>): Result<Unit> {
        latency()
        return attempt {
            backend.requireOperator()
            require(photoUris.isNotEmpty()) { "Add at least one photo" }
            val ticket = requireNotNull(backend.tickets.value[ticketId]) { "Ticket not found" }
            val at = now()
            val photos = photoUris.map { newMedia(ticketId, MediaPhase.RESOLUTION, at) }
            backend.tickets.update { it + (ticketId to ticket.copy(media = ticket.media + photos.map { p -> p.first }, updatedAt = at)) }
            backend.media.update { it + (ticketId to it[ticketId].orEmpty() + photos.map { p -> p.second }) }
        }
    }

    private fun newMedia(ticketId: String, phase: MediaPhase, at: Long): Pair<MediaRef, MediaContent> {
        val mediaId = newId()
        return MediaRef(mediaId, SAMPLE_IMAGE_BASE64, phase, UploadState.DONE, at) to
            MediaContent(mediaId, ticketId, SAMPLE_IMAGE_BASE64, phase, at)
    }

    private fun appendHistory(ticketId: String, change: StateChange) {
        backend.history.update { it + (ticketId to it[ticketId].orEmpty() + change) }
    }
}

class FakeMediaRepository(private val backend: FakeBackend) : MediaRepository {
    override fun observeMedia(ticketId: String): Flow<List<MediaContent>> =
        backend.media.map { it[ticketId].orEmpty().sortedBy { media -> media.createdAt } }
}

class FakePublicRepository(private val backend: FakeBackend) : PublicRepository {

    private val publicTickets: Flow<List<PublicTicket>> = backend.tickets.map { tickets ->
        tickets.values.filter { it.publicScope }.map { it.toPublic() }.sortedByDescending { it.createdAt }
    }

    override fun observePublicTickets(): Flow<List<PublicTicket>> =
        publicTickets.map { list -> list.filter { it.lifecycleState.isActive } }

    override fun observePublicTicket(id: String): Flow<PublicTicket?> =
        publicTickets.map { list -> list.firstOrNull { it.id == id } }

    override fun observeDashboard(): Flow<DashboardStats> = publicTickets.map { list ->
        val repairHours = list.mapNotNull { t -> t.resolvedAt?.let { (it - t.createdAt) / 3_600_000.0 } }
        DashboardStats(
            totalTickets = list.size,
            activePercent = if (list.isEmpty()) 0.0 else 100.0 * list.count { it.lifecycleState.isActive } / list.size,
            averageRepairHours = repairHours.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    private fun Ticket.toPublic() = PublicTicket(
        id = id, categoryId = categoryId, incidentType = incidentType, lifecycleState = lifecycleState,
        thumbnailBase64 = media.firstOrNull { it.phase == MediaPhase.REPORT }?.thumbnailBase64,
        approxLocation = location.roundedForPublic(), createdAt = createdAt, resolvedAt = resolvedAt,
    )
}

class FakeMessageRepository(private val backend: FakeBackend) : MessageRepository {

    override fun observeMessages(ticketId: String): Flow<List<Message>> =
        backend.messages.map { it[ticketId].orEmpty().sortedBy { message -> message.sentAt } }

    override suspend fun sendMessage(ticketId: String, text: String): Result<Unit> {
        latency()
        return attempt {
            val user = backend.requireUser()
            require(text.isNotBlank()) { "Message is empty" }
            val message = Message(newId(), user.id, user.role, text.trim(), now())
            backend.messages.update { it + (ticketId to it[ticketId].orEmpty() + message) }
        }
    }
}

/** Always reports Praça da República, i.e. inside the anonymous area. */
class FakeLocationProvider : LocationProvider {
    override suspend fun currentLocation(): LatLng = AppConfig.ANONYMOUS_AREA_CENTER

    override fun isWithinAnonymousArea(location: LatLng): Boolean =
        location.distanceTo(AppConfig.ANONYMOUS_AREA_CENTER) <= AppConfig.ANONYMOUS_RADIUS_METERS
}
