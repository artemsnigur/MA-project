package com.example.cityfixmaas.ui.auth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityfixmaas.R

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

    // Second step of the sign-in: the email must be confirmed before the first sign-in.
    state.confirmationSentTo?.let { email ->
        AlertDialog(
            onDismissRequest = onNavigateToLogin,
            title = { Text(stringResource(R.string.register_confirm_email_title)) },
            text = { Text(stringResource(R.string.register_confirm_email_text, email)) },
            confirmButton = { TextButton(onClick = onNavigateToLogin) { Text(stringResource(R.string.register_confirm_email_ok)) } },
        )
    }
}
