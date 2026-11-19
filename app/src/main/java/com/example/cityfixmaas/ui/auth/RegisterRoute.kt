package com.example.cityfixmaas.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun RegisterRoute(
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    RegisterScreen(
        state = state,
        onRoleChange = viewModel::onRoleChange,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onPhoneChange = viewModel::onPhoneChange,
        onNifChange = viewModel::onNifChange,
        onCaptureLocation = viewModel::captureLocation,
        onPhotoSelected = viewModel::onPhotoSelected,
        onImpactDetectionToggled = viewModel::onImpactDetectionToggled,
        onRegisterClick = viewModel::onRegisterClick,
        onNavigateToLogin = onNavigateToLogin,
        modifier = modifier,
    )
}
