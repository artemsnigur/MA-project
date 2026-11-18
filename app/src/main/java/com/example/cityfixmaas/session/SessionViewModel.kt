package com.example.cityfixmaas.session

import androidx.lifecycle.ViewModel
import com.example.cityfixmaas.model.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SessionViewModel : ViewModel() {
    private val _role = MutableStateFlow(Role.ANONYMOUS)
    val role: StateFlow<Role> = _role.asStateFlow()

    // TEMP (F0): cycles Anonymous -> Citizen -> Operator
    fun switchRole() {
        _role.update { current ->
            Role.entries[(current.ordinal + 1) % Role.entries.size]
        }
    }
}
