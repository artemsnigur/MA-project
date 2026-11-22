package com.example.cityfixmaas.data.model

import com.example.cityfixmaas.data.AppConfig

import android.net.Uri

// Firestore maps documents to these classes with toObject(), which needs a no-arg constructor:
// every field has a default value. Booleans have no "is" prefix, because Firestore would store
// "isActive" as "active" and break the mapping.

data class LatLng(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)

/** Stored in `users/{uid}`. */
data class User(
    val id: String = "",
    val role: UserRole = UserRole.CITIZEN,
    val status: AccountStatus = AccountStatus.PENDING,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val nif: String = "",
    /** Citizen location captured at registration; null for operators. Read-only after registration. */
    val location: LatLng? = null,
    /** Profile photo as a Base64-encoded JPEG, or null. */
    val photoBase64: String? = null,
    val impactDetectionEnabled: Boolean = false,
    val createdAt: Long = 0,
)

/** Stored in `categories/{id}`. */
data class Category(
    val id: String = "",
    val name: String = "",
    val active: Boolean = true,
)

/**
 * Lightweight reference to one photo, kept inside the ticket document so lists can show
 * thumbnails without loading full images. The full image lives in [MediaContent].
 */
data class MediaRef(
    val id: String = "",
    /** ~200 px Base64 JPEG, null until the photo has been processed. */
    val thumbnailBase64: String? = null,
    val phase: MediaPhase = MediaPhase.REPORT,
    val uploadState: UploadState = UploadState.PENDING,
    val createdAt: Long = 0,
    val type: MediaType = MediaType.PHOTO,
    /** uid of the citizen or operator who added it; only their phone holds the file to upload. */
    val addedBy: String = "",
)

/** Full-size photo, stored in `tickets/{ticketId}/media/{id}`, one document per photo (1 MiB limit). */
data class MediaContent(
    val id: String = "",
    val ticketId: String = "",
    /** Compressed JPEG (max 1280 px) encoded as Base64; for a video, its first frame. */
    val imageBase64: String = "",
    /** Short compressed MP4 encoded as Base64 (see VideoSize), only for videos. */
    val videoBase64: String? = null,
    val phase: MediaPhase = MediaPhase.REPORT,
    val createdAt: Long = 0,
)

/** Stored in `tickets/{id}`. Readable by its authors and operators only. */
data class Ticket(
    val id: String = "",
    /** uid of the citizen who reported it. */
    val authorId: String = "",
    val categoryId: String = "",
    val incidentType: IncidentType = IncidentType.DAMAGE,
    val lifecycleState: LifecycleState = LifecycleState.OPEN,
    val publicScope: Boolean = true,
    val description: String = "",
    val location: LatLng = LatLng(),
    val media: List<MediaRef> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val resolvedAt: Long? = null,
    /** Other citizens who reported the same problem (see AppConfig.DUPLICATE_RADIUS_METERS); they follow the ticket too. */
    val followerIds: List<String> = emptyList(),
    /** Last time an operator was within 20 m while the ticket was in resolution (the citizens got a push). */
    val operatorNearbyAt: Long = 0,
) {
    /** The author may reopen a resolved ticket during the first 5 days. */
    fun canReopen(now: Long): Boolean =
        lifecycleState == LifecycleState.RESOLVED && resolvedAt != null && now - resolvedAt <= AppConfig.REOPEN_WINDOW_MS

    /** Resolved for more than 5 days without a contest: it must be closed. */
    fun isReopenExpired(now: Long): Boolean =
        lifecycleState == LifecycleState.RESOLVED && resolvedAt != null && now - resolvedAt > AppConfig.REOPEN_WINDOW_MS
}

/**
 * Stored in `publicTickets/{id}` for public tickets only. Safe for anonymous users:
 * no author data and the location rounded to about 1 km.
 */
data class PublicTicket(
    val id: String = "",
    val categoryId: String = "",
    val incidentType: IncidentType = IncidentType.DAMAGE,
    val lifecycleState: LifecycleState = LifecycleState.OPEN,
    val thumbnailBase64: String? = null,
    val approxLocation: LatLng = LatLng(),
    val createdAt: Long = 0,
    val resolvedAt: Long? = null,
)

/** Stored in `tickets/{id}/history/{id}`. `from` is null for the creation entry. */
data class StateChange(
    val id: String = "",
    val from: LifecycleState? = null,
    val to: LifecycleState = LifecycleState.OPEN,
    val byUid: String = "",
    val at: Long = 0,
    val note: String? = null,
)

/** Stored in `tickets/{id}/messages/{id}`. */
data class Message(
    val id: String = "",
    val senderUid: String = "",
    val senderRole: UserRole = UserRole.CITIZEN,
    val text: String = "",
    val sentAt: Long = 0,
)

data class DashboardStats(
    val totalTickets: Int = 0,
    /** 0.0–100.0 */
    val activePercent: Double = 0.0,
    /** Average of resolvedAt - createdAt over resolved tickets; null when none is resolved yet. */
    val averageRepairHours: Double? = null,
)

/** Result of reporting: the new ticket, or the existing one the report joined. */
data class CreatedTicket(val id: String, val joinedExisting: Boolean)

// ---- Inputs from the UI (not stored as-is) ----

data class TicketDraft(
    val categoryId: String,
    val incidentType: IncidentType,
    val description: String,
    val publicScope: Boolean,
    val location: LatLng,
    /** Content URIs from the photo picker or camera. At least one is required. */
    val photoUris: List<Uri>,
)

data class RegistrationForm(
    val name: String,
    val email: String,
    val password: String,
    val phone: String,
    val nif: String,
    /** Required for citizens, ignored for operators. */
    val location: LatLng? = null,
    val photoUri: Uri? = null,
    val impactDetectionEnabled: Boolean = false,
)

/** What the app knows about who is using it. */
sealed interface SessionState {
    /** Firebase is still restoring the cached session; show a splash/progress. */
    data object Loading : SessionState

    /** Nobody is signed in: anonymous mode (10 km rule applies). */
    data object Anonymous : SessionState

    /** Signed in. Check [User.status]: a PENDING citizen sees the "account pending" screen. */
    data class SignedIn(val user: User) : SessionState
}
