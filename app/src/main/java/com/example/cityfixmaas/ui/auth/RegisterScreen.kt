package com.example.cityfixmaas.ui.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cityfixmaas.R
import com.example.cityfixmaas.data.model.UserRole

@Composable
fun RegisterScreen(
    state: RegisterUiState,
    onRoleChange: (UserRole) -> Unit,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onNifChange: (String) -> Unit,
    onCaptureLocation: () -> Unit,
    onPhotoSelected: (Uri?) -> Unit,
    onImpactDetectionToggled: (Boolean) -> Unit,
    onRegisterClick: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        onPhotoSelected(uri)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Text(
            text = stringResource(R.string.title_register),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Role Segmented Button
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.role == UserRole.CITIZEN,
                onClick = { onRoleChange(UserRole.CITIZEN) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text(stringResource(R.string.role_citizen))
            }
            SegmentedButton(
                selected = state.role == UserRole.OPERATOR,
                onClick = { onRoleChange(UserRole.OPERATOR) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text(stringResource(R.string.role_operator))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        state.errorMessage?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.label_name)) },
            isError = state.nameError != null,
            supportingText = state.nameError?.let { { Text(stringResource(it.messageRes())) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.label_email)) },
            isError = state.emailError != null,
            supportingText = state.emailError?.let { { Text(stringResource(it.messageRes())) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.label_password)) },
            isError = state.passwordError != null,
            supportingText = state.passwordError?.let { { Text(stringResource(it.messageRes())) } },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = stringResource(
                            if (passwordVisible) R.string.cd_hide_password else R.string.cd_show_password,
                        ),
                    )
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = state.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text(stringResource(R.string.label_confirm_password)) },
            isError = state.confirmPasswordError != null,
            supportingText = state.confirmPasswordError?.let { { Text(stringResource(it.messageRes())) } },
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                    Icon(
                        imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = stringResource(
                            if (confirmPasswordVisible) R.string.cd_hide_password else R.string.cd_show_password,
                        ),
                    )
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = state.phone,
            onValueChange = onPhoneChange,
            label = { Text(stringResource(R.string.label_phone)) },
            isError = state.phoneError != null,
            supportingText = state.phoneError?.let { { Text(stringResource(it.messageRes())) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = state.nif,
            onValueChange = onNifChange,
            label = { Text(stringResource(R.string.label_nif)) },
            isError = state.nifError != null,
            supportingText = state.nifError?.let { { Text(stringResource(it.messageRes())) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        // Citizen-only extras
        if (state.role == UserRole.CITIZEN) {
            Spacer(modifier = Modifier.height(16.dp))

            // Location
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.padding(4.dp))
                        val locText = state.location?.let {
                            stringResource(R.string.location_captured, it.latitude, it.longitude)
                        } ?: stringResource(R.string.location_not_set)
                        Text(
                            text = locText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(
                        onClick = onCaptureLocation,
                        enabled = !state.isCapturingLocation,
                    ) {
                        if (state.isCapturingLocation) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.capture_location))
                        }
                    }
                }
            }

            state.locationError?.let {
                Text(
                    text = stringResource(it.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(start = 16.dp, top = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Profile photo option
            OutlinedButton(
                onClick = { photoPickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = if (state.photoUri != null) {
                        stringResource(R.string.action_change_photo)
                    } else {
                        stringResource(R.string.action_change_photo)
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Impact detection toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.label_impact_detection),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = state.impactDetectionEnabled,
                    onCheckedChange = onImpactDetectionToggled,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRegisterClick,
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.action_register))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = onNavigateToLogin,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.prompt_has_account))
        }
    }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterScreenPreview() {
    MaterialTheme {
        RegisterScreen(
            state = RegisterUiState(),
            onRoleChange = {},
            onNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onPhoneChange = {},
            onNifChange = {},
            onCaptureLocation = {},
            onPhotoSelected = {},
            onImpactDetectionToggled = {},
            onRegisterClick = {},
            onNavigateToLogin = {},
        )
    }
}
