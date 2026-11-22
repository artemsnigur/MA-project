package com.example.cityfixmaas.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.cityfixmaas.R

data class SignInCodeState(
    val email: String,
    val code: String = "",
    val isChecking: Boolean = false,
    val errorMessage: String? = null,
)

/** Second sign-in step: the 6-digit code emailed by the relay. */
@Composable
fun SignInCodeDialog(
    state: SignInCodeState,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {}, // only the buttons close it
        title = { Text(stringResource(R.string.sign_in_code_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sign_in_code_desc, state.email), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.code,
                    onValueChange = { onCodeChange(it.filter(Char::isDigit).take(6)) },
                    label = { Text(stringResource(R.string.label_sign_in_code)) },
                    isError = state.errorMessage != null,
                    supportingText = state.errorMessage?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    enabled = !state.isChecking,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.isChecking && state.code.length == 6) {
                if (state.isChecking) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.action_sign_in))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isChecking) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
