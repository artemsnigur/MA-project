package com.example.cityfixmaas.data.repository

import android.net.Uri
import com.example.cityfixmaas.data.model.Category
import com.example.cityfixmaas.data.model.DashboardStats
import com.example.cityfixmaas.data.model.LatLng
import com.example.cityfixmaas.data.model.CreatedTicket
import com.example.cityfixmaas.data.model.LifecycleState
import com.example.cityfixmaas.data.model.MediaContent
import com.example.cityfixmaas.data.model.Message
import com.example.cityfixmaas.data.model.PublicTicket
import com.example.cityfixmaas.data.model.RegistrationForm
import com.example.cityfixmaas.data.model.SessionState
import com.example.cityfixmaas.data.model.StateChange
import com.example.cityfixmaas.data.model.Ticket
import com.example.cityfixmaas.data.model.TicketDraft
import com.example.cityfixmaas.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// The contract between UI and backend. ViewModels depend on these interfaces only, never on
// Firebase. Change a signature only after both of us agree (PROJECT_PLAN.md, section 6).
//
// Conventions:
// - observeX(): a Flow that emits the current value and every later change (Firestore listeners).
// - suspend actions return Result; on failure, show exception.message to the user.

interface AuthRepository {
    val session: StateFlow<SessionState>

    suspend fun signIn(email: String, password: String): Result<Unit>

    /** Creates a PENDING citizen. Fails if the NIF is already registered for citizens. */
    suspend fun registerCitizen(form: RegistrationForm): Result<Unit>

    /** Creates an ACTIVE operator. Fails unless the email is in [AppConfig.MUNICIPAL_EMAIL_DOMAIN]. */
    suspend fun registerOperator(form: RegistrationForm): Result<Unit>

    suspend fun sendPasswordReset(email: String): Result<Unit>

    /** Name, NIF and location cannot be changed. Pass photoUri = null to keep the current photo. */
    suspend fun updateProfile(phone: String, photoUri: Uri?, impactDetectionEnabled: Boolean): Result<Unit>

    suspend fun signOut()
}

/** Operator-only user management. */
interface UserAdminRepository {
    fun observePendingCitizens(): Flow<List<User>>

    /** Any user's profile, e.g. the ticket author's contact data in the operator panel. */
    fun observeUser(uid: String): Flow<User?>

    suspend fun activateCitizen(uid: String): Result<Unit>
}

interface CategoryRepository {
    /** Citizens creating a ticket pass activeOnly = true; the operator's category screen passes false. */
    fun observeCategories(activeOnly: Boolean): Flow<List<Category>>

    /** Creates the category when id is blank, otherwise renames it. Operator only. */
    suspend fun saveCategory(category: Category): Result<Unit>

    suspend fun setActive(id: String, active: Boolean): Result<Unit>
}

interface TicketRepository {
    /** Tickets the signed-in citizen authored or follows, newest first. */
    fun observeMyTickets(): Flow<List<Ticket>>

    /** Every ticket, newest first. Operator only. */
    fun observeAllTickets(): Flow<List<Ticket>>

    fun observeTicket(id: String): Flow<Ticket?>

    /** Lifecycle history, oldest first. */
    fun observeHistory(ticketId: String): Flow<List<StateChange>>

    /**
     * Saves the ticket immediately (works offline) and queues photo processing.
     * Photos show UploadState.PENDING until they are written.
     * If an active ticket with the same category and type is within 10 m, the citizen joins it
     * as a follower instead and no new ticket is created.
     */
    suspend fun createTicket(draft: TicketDraft): Result<CreatedTicket>

    /**
     * Operator only. Fails if [to] is not in the current state's allowedNext(),
     * or on RESOLVED without RESOLUTION photos.
     */
    suspend fun changeState(ticketId: String, to: LifecycleState, note: String?): Result<Unit>

    /** Author or follower: RESOLVED -> OPEN within 5 days of the resolution, with a mandatory justification. */
    suspend fun reopenTicket(ticketId: String, justification: String): Result<Unit>

    /** Author or follower: OPEN -> INFORMATION_REQUEST, asking the municipality a question (kept in the history). */
    suspend fun requestInformation(ticketId: String, question: String): Result<Unit>

    /**
     * Closes the given tickets that were resolved more than 5 days ago. There is no server code
     * (Spark plan), so the apps of the author and of the operators do it when they see such a ticket.
     */
    suspend fun closeExpiredTickets(tickets: List<Ticket>)

    /** Operator evidence of completed work. Required before moving to RESOLVED. */
    suspend fun addResolutionPhotos(ticketId: String, photoUris: List<Uri>): Result<Unit>
}

/** Full-size photos. Used by ticket details for authors, operators and (public tickets) anonymous users. */
interface MediaRepository {
    fun observeMedia(ticketId: String): Flow<List<MediaContent>>
}

/** Data that anonymous users may see. */
interface PublicRepository {
    /** Active public tickets (OPEN, INFORMATION_REQUEST, IN_RESOLUTION), newest first. */
    fun observePublicTickets(): Flow<List<PublicTicket>>

    fun observePublicTicket(id: String): Flow<PublicTicket?>

    fun observeDashboard(): Flow<DashboardStats>
}

interface MessageRepository {
    /** Oldest first. */
    fun observeMessages(ticketId: String): Flow<List<Message>>

    suspend fun sendMessage(ticketId: String, text: String): Result<Unit>
}

interface LocationProvider {
    /** Null when permission is missing or no fix is available. The UI requests the permission. */
    suspend fun currentLocation(): LatLng?

    /** True within [AppConfig.ANONYMOUS_RADIUS_METERS] of [AppConfig.ANONYMOUS_AREA_CENTER]. */
    fun isWithinAnonymousArea(location: LatLng): Boolean
}
