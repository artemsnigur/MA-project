package com.example.cityfixmaas.ui.auth

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cityfixmaas.data.AppConfig
import com.example.cityfixmaas.data.model.LatLng
import com.example.cityfixmaas.data.model.RegistrationForm
import com.example.cityfixmaas.data.model.UserRole
import com.example.cityfixmaas.data.repository.AuthRepository
import com.example.cityfixmaas.data.repository.LocationProvider
import com.example.cityfixmaas.di.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val role: UserRole = UserRole.CITIZEN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val phone: String = "",
    val nif: String = "",
    val location: LatLng? = null,
    val isCapturingLocation: Boolean = false,
    val photoUri: Uri? = null,
    val impactDetectionEnabled: Boolean = false,
    val nameError: FieldError? = null,
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    val confirmPasswordError: FieldError? = null,
    val phoneError: FieldError? = null,
    val nifError: FieldError? = null,
    val locationError: FieldError? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Registered: the confirmation link was sent to this address. */
    val confirmationSentTo: String? = null,
)

class RegisterViewModel(
    private val auth: AuthRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    init {
        // Try getting initial location for citizen
        captureLocation()
    }

    // Typing only stores the value and clears that field's error; everything is checked on "Register".
    fun onRoleChange(role: UserRole) = _uiState.update { it.copy(role = role, emailError = null, locationError = null, errorMessage = null) }
    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, nameError = null) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, emailError = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, passwordError = null) }
    fun onConfirmPasswordChange(value: String) = _uiState.update { it.copy(confirmPassword = value, confirmPasswordError = null) }
    fun onPhoneChange(value: String) = _uiState.update { it.copy(phone = value, phoneError = null) }
    fun onNifChange(value: String) = _uiState.update { it.copy(nif = value, nifError = null) }
    fun onPhotoSelected(uri: Uri?) = _uiState.update { it.copy(photoUri = uri) }
    fun onImpactDetectionToggled(enabled: Boolean) = _uiState.update { it.copy(impactDetectionEnabled = enabled) }

    fun captureLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCapturingLocation = true) }
            val loc = locationProvider.currentLocation() ?: AppConfig.ANONYMOUS_AREA_CENTER
            _uiState.update {
                it.copy(
                    location = loc,
                    isCapturingLocation = false,
                    locationError = null,
                )
            }
        }
    }

    fun onRegisterClick() {
        val s = _uiState.value
        val nameErr = Validation.name(s.name)
        val emailErr = Validation.email(s.email, municipalOnly = s.role == UserRole.OPERATOR)
        val passErr = Validation.password(s.password)
        val confErr = Validation.passwordMatch(s.password, s.confirmPassword)
        val phoneErr = Validation.phone(s.phone)
        val nifErr = Validation.nif(s.nif)
        val locErr = if (s.role == UserRole.CITIZEN && s.location == null) {
            FieldError.LOCATION_REQUIRED
        } else null

        if (nameErr != null || emailErr != null || passErr != null ||
            confErr != null || phoneErr != null || nifErr != null || locErr != null
        ) {
            _uiState.update {
                it.copy(
                    nameError = nameErr,
                    emailError = emailErr,
                    passwordError = passErr,
                    confirmPasswordError = confErr,
                    phoneError = phoneErr,
                    nifError = nifErr,
                    locationError = locErr,
                )
            }
            return
        }

        val form = RegistrationForm(
            name = s.name.trim(),
            email = s.email.trim(),
            password = s.password,
            phone = s.phone.trim(),
            nif = s.nif.trim(),
            location = if (s.role == UserRole.CITIZEN) s.location else null,
            photoUri = s.photoUri,
            impactDetectionEnabled = s.impactDetectionEnabled,
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = if (s.role == UserRole.CITIZEN) {
                auth.registerCitizen(form)
            } else {
                auth.registerOperator(form)
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    confirmationSentTo = if (result.isSuccess) form.email.trim() else null,
                )
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                RegisterViewModel(container.auth, container.location)
            }
        }
    }
}
