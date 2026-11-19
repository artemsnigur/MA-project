package com.example.cityfixmaas.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LoginRoute(
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LoginScreen(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSignInClick = viewModel::onSignInClick,
        onForgotPasswordClick = viewModel::onForgotPasswordClick,
        onNavigateToRegister = onNavigateToRegister,
        modifier = modifier,
    )

    state.forgotPassword?.let { dialogState ->
        ForgotPasswordDialog(
            state = dialogState,
            onEmailChange = viewModel::onForgotPasswordEmailChange,
            onSend = viewModel::onForgotPasswordSend,
            onDismiss = viewModel::onForgotPasswordDismiss,
        )
    }
}
