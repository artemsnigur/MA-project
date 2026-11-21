package com.example.cityfixmaas.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cityfixmaas.data.repository.AuthRepository
import com.example.cityfixmaas.di.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordState(
    val email: String = "",
    val emailError: FieldError? = null,
    val isSending: Boolean = false,
    val feedbackMessage: String? = null,
    val isSuccess: Boolean = false,
)

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val forgotPassword: ForgotPasswordState? = null,
) {
    val isValid: Boolean
        get() = emailError == null && passwordError == null &&
            email.isNotBlank() && password.isNotEmpty()
}

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, emailError = null, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, passwordError = null, errorMessage = null) }

    fun onSignInClick() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        val emailErr = Validation.email(email)
        val passErr = Validation.password(password, checkLength = false)

        if (emailErr != null || passErr != null) {
            _uiState.update { it.copy(emailError = emailErr, passwordError = passErr) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = auth.signIn(email, password)
            // On success the session changes and AppShell shows the next screen by itself.
            _uiState.update { it.copy(isLoading = false, errorMessage = result.exceptionOrNull()?.message) }
        }
    }

    fun onForgotPasswordClick() {
        _uiState.update { it.copy(forgotPassword = ForgotPasswordState(email = it.email.trim())) }
    }

    fun onForgotPasswordEmailChange(value: String) =
        _uiState.update { it.copy(forgotPassword = it.forgotPassword?.copy(email = value, emailError = null, feedbackMessage = null)) }

    fun onForgotPasswordSend() {
        val dialog = _uiState.value.forgotPassword ?: return
        val error = Validation.email(dialog.email)
        if (error != null) {
            _uiState.update { it.copy(forgotPassword = dialog.copy(emailError = error)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(forgotPassword = dialog.copy(isSending = true, feedbackMessage = null)) }
            val result = auth.sendPasswordReset(dialog.email.trim())
            val message = result.exceptionOrNull()?.message ?: "Password reset email sent"
            _uiState.update {
                it.copy(forgotPassword = it.forgotPassword?.copy(isSending = false, feedbackMessage = message, isSuccess = result.isSuccess))
            }
        }
    }

    fun onForgotPasswordDismiss() {
        _uiState.update { it.copy(forgotPassword = null) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                LoginViewModel(appContainer().auth)
            }
        }
    }
}
