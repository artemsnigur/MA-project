package com.example.cityfixmaas.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cityfixmaas.data.fake.FakeBackend
import com.example.cityfixmaas.data.model.SessionState
import com.example.cityfixmaas.data.repository.AuthRepository
import com.example.cityfixmaas.di.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide session: decides which navigation graph is shown. */
class SessionViewModel(private val auth: AuthRepository) : ViewModel() {

    /** Null while the session is loading (show the splash). */
    val mode: StateFlow<AppMode?> = auth.session
        .map { it.toAppMode() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), auth.session.value.toAppMode())

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    /**
     * TEMP (F0, debug only): signs in as the next demo account so every graph can be checked
     * before the login screen exists. Works with the fake repositories only. Remove in F1.
     * Order: anonymous -> citizen (rui) -> operator (ana) -> pending citizen (marta) -> anonymous.
     */
    fun cycleDemoAccount() {
        viewModelScope.launch {
            val current = (auth.session.value as? SessionState.SignedIn)?.user?.email
            val next = when (current) {
                null -> "rui@example.com"
                "rui@example.com" -> "ana@cm-coimbra.pt"
                "ana@cm-coimbra.pt" -> "marta@example.com"
                else -> null
            }
            if (next == null) auth.signOut() else auth.signIn(next, FakeBackend.DEMO_PASSWORD)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SessionViewModel(appContainer().auth) }
        }
    }
}
