package com.example.cityfixmaas.data.model

/** Profile of a signed-in user. Anonymous users have no stored profile. */
enum class UserRole { CITIZEN, OPERATOR }

/** Citizens start as PENDING until an operator activates them; operators start as ACTIVE. */
enum class AccountStatus { PENDING, ACTIVE }

/** Incident classification, called "Status" in the assignment. Not the lifecycle state. */
enum class IncidentType { DAMAGE, BREAKDOWN, DESTRUCTION, THEFT }

/** Ticket lifecycle stage. */
enum class LifecycleState {
    OPEN, INFORMATION_REQUEST, IN_RESOLUTION, RESOLVED, CLOSED;

    /** Active tickets are the ones still being handled by the municipality. */
    val isActive: Boolean
        get() = this == OPEN || this == INFORMATION_REQUEST || this == IN_RESOLUTION

    /** States this ticket may move to next. The UI offers only these; repositories enforce them. */
    fun allowedNext(): Set<LifecycleState> = when (this) {
        OPEN -> setOf(INFORMATION_REQUEST, IN_RESOLUTION)
        INFORMATION_REQUEST -> setOf(OPEN, IN_RESOLUTION)
        IN_RESOLUTION -> setOf(RESOLVED)
        RESOLVED -> setOf(CLOSED)
        CLOSED -> emptySet()
    }
}

/** Whether a photo has been compressed and written to Firestore yet. */
enum class UploadState { PENDING, DONE, FAILED }

/** REPORT = attached by the citizen; RESOLUTION = evidence of completed work from an operator. */
enum class MediaPhase { REPORT, RESOLUTION }
