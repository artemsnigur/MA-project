package com.example.cityfixmaas.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cityfixmaas.data.model.SessionState
import com.example.cityfixmaas.data.model.User
import com.example.cityfixmaas.data.repository.AuthRepository
import com.example.cityfixmaas.di.appContainer
import com.example.cityfixmaas.ui.auth.FieldError
import com.example.cityfixmaas.ui.auth.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: User? = null,
    val phone: String = "",
    val phoneError: FieldError? = null,
    val selectedPhotoUri: Uri? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null,
) {
    val hasChanges: Boolean
        get() = (user != null && phone != user.phone) || selectedPhotoUri != null
}

class ProfileViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            auth.session.collect { session ->
                if (session is SessionState.SignedIn) {
                    _uiState.update { current ->
                        current.copy(
                            user = session.user,
                            phone = if (current.user == null) session.user.phone else current.phone,
                        )
                    }
                }
            }
        }
    }

    fun onPhoneChange(value: String) {
        _uiState.update {
            it.copy(
                phone = value,
                phoneError = if (it.phoneError != null) Validation.phone(value) else null,
                saveSuccess = false,
                errorMessage = null,
            )
        }
    }

    fun onPhotoSelected(uri: Uri?) {
        _uiState.update {
            it.copy(
                selectedPhotoUri = uri,
                saveSuccess = false,
                errorMessage = null,
            )
        }
    }

    fun onSaveClick() {
        val s = _uiState.value
        val phoneErr = Validation.phone(s.phone)
        if (phoneErr != null) {
            _uiState.update { it.copy(phoneError = phoneErr) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, saveSuccess = false) }
            val result = auth.updateProfile(s.phone.trim(), s.selectedPhotoUri)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveSuccess = true,
                            selectedPhotoUri = null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "Failed to update profile",
                        )
                    }
                },
            )
        }
    }

    fun dismissSuccessMessage() {
        _uiState.update { it.copy(saveSuccess = false) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ProfileViewModel(appContainer().auth)
            }
        }
    }
}
