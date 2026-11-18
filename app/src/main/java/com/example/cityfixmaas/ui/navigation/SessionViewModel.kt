package com.example.cityfixmaas.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cityfixmaas.data.fake.FakeBackend
import com.example.cityfixmaas.data.model.AccountStatus
import com.example.cityfixmaas.data.model.SessionState
import com.example.cityfixmaas.data.model.UserRole
import com.example.cityfixmaas.data.repository.AuthRepository
import com.example.cityfixmaas.di.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which set of screens the app shows. */
enum class AppMode { ANONYMOUS, PENDING_CITIZEN, CITIZEN, OPERATOR }

/** Watches who is signed in and turns it into an [AppMode] for [AppShell]. */
class SessionViewModel(private val auth: AuthRepository) : ViewModel() {

    /** Null while Firebase is still restoring the session (show a spinner). */
    val mode: StateFlow<AppMode?> = auth.session
        .map { toMode(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, toMode(auth.session.value))

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    /**
     * TEMP (F0, fakes only): signs in as the next demo account so every set of screens can be
     * checked before the login screen exists. Remove in F1.
     */
    fun cycleDemoAccount() {
        viewModelScope.launch {
            val email = (auth.session.value as? SessionState.SignedIn)?.user?.email
            when (email) {
                null -> auth.signIn("rui@example.com", FakeBackend.DEMO_PASSWORD)
                "rui@example.com" -> auth.signIn("ana@cm-coimbra.pt", FakeBackend.DEMO_PASSWORD)
                "ana@cm-coimbra.pt" -> auth.signIn("marta@example.com", FakeBackend.DEMO_PASSWORD)
                else -> auth.signOut()
            }
        }
    }

    private fun toMode(session: SessionState): AppMode? = when (session) {
        SessionState.Loading -> null
        SessionState.Anonymous -> AppMode.ANONYMOUS
        is SessionState.SignedIn -> when {
            session.user.role == UserRole.OPERATOR -> AppMode.OPERATOR
            session.user.status == AccountStatus.PENDING -> AppMode.PENDING_CITIZEN
            else -> AppMode.CITIZEN
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SessionViewModel(appContainer().auth) }
        }
    }
}
