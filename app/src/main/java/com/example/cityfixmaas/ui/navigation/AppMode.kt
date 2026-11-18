package com.example.cityfixmaas.ui.navigation

import com.example.cityfixmaas.data.model.AccountStatus
import com.example.cityfixmaas.data.model.SessionState
import com.example.cityfixmaas.data.model.UserRole

/** Which navigation graph the app shows. Derived from [SessionState]; never stored separately. */
enum class AppMode { ANONYMOUS, PENDING_CITIZEN, CITIZEN, OPERATOR }

/** Null while the session is still loading. */
fun SessionState.toAppMode(): AppMode? = when (this) {
    SessionState.Loading -> null
    SessionState.Anonymous -> AppMode.ANONYMOUS
    is SessionState.SignedIn -> when {
        user.role == UserRole.OPERATOR -> AppMode.OPERATOR
        user.status == AccountStatus.PENDING -> AppMode.PENDING_CITIZEN
        else -> AppMode.CITIZEN
    }
}
