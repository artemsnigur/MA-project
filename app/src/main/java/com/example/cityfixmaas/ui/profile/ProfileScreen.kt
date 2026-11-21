package com.example.cityfixmaas.ui.profile

import androidx.compose.material3.Switch
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.cityfixmaas.R
import com.example.cityfixmaas.data.media.base64ToBitmap
import com.example.cityfixmaas.data.model.AccountStatus
import com.example.cityfixmaas.data.model.User
import com.example.cityfixmaas.data.model.UserRole
import com.example.cityfixmaas.ui.auth.messageRes

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onPhoneChange: (String) -> Unit,
    onPhotoSelected: (Uri?) -> Unit,
    onImpactDetectionToggled: (Boolean) -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        onPhotoSelected(uri)
    }

    val user = state.user
    val avatarBitmap = remember(user?.photoBase64) {
        base64ToBitmap(user?.photoBase64)
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
        // Avatar section
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.selectedPhotoUri != null -> {
                    AsyncImage(
                        model = state.selectedPhotoUri,
                        contentDescription = stringResource(R.string.cd_profile_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                avatarBitmap != null -> {
                    Image(
                        bitmap = avatarBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_profile_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { photoPickerLauncher.launch("image/*") },
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(R.string.action_change_photo))
        }

        // Success message
        if (state.saveSuccess) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.profile_updated_success),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Error message
        state.errorMessage?.let { error ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
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

        // Read-only fields
        user?.let { u ->
            ReadOnlyField(label = stringResource(R.string.label_name), value = u.name)
            Spacer(modifier = Modifier.height(8.dp))
            ReadOnlyField(label = stringResource(R.string.label_email), value = u.email)
            Spacer(modifier = Modifier.height(8.dp))
            ReadOnlyField(
                label = stringResource(R.string.label_role),
                value = if (u.role == UserRole.CITIZEN) stringResource(R.string.role_citizen) else stringResource(R.string.role_operator),
            )
            Spacer(modifier = Modifier.height(8.dp))
            ReadOnlyField(label = stringResource(R.string.label_nif), value = u.nif)
            Spacer(modifier = Modifier.height(8.dp))
            ReadOnlyField(
                label = stringResource(R.string.label_status),
                value = if (u.status == AccountStatus.ACTIVE) stringResource(R.string.status_active) else stringResource(R.string.status_pending),
            )

            if (u.role == UserRole.CITIZEN && u.location != null) {
                Spacer(modifier = Modifier.height(8.dp))
                ReadOnlyField(
                    label = stringResource(R.string.label_location),
                    value = stringResource(R.string.location_captured, u.location.latitude, u.location.longitude),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Editable Phone
        OutlinedTextField(
            value = state.phone,
            onValueChange = onPhoneChange,
            label = { Text(stringResource(R.string.label_phone)) },
            isError = state.phoneError != null,
            supportingText = state.phoneError?.let { { Text(stringResource(it.messageRes())) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (user?.role == UserRole.CITIZEN) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.label_impact_detection), modifier = Modifier.weight(1f))
                Switch(checked = state.impactDetectionEnabled, onCheckedChange = onImpactDetectionToggled)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Save button
        Button(
            onClick = onSaveClick,
            enabled = !state.isSaving && state.hasChanges,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.action_save))
            }
        }
    }
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    MaterialTheme {
        ProfileScreen(
            state = ProfileUiState(
                user = User(
                    name = "Rui Silva",
                    email = "rui@example.com",
                    phone = "912345678",
                    nif = "123456789",
                    role = UserRole.CITIZEN,
                    status = AccountStatus.ACTIVE,
                ),
                phone = "912345678",
            ),
            onPhoneChange = {},
            onPhotoSelected = {},
            onImpactDetectionToggled = {},
            onSaveClick = {},
        )
    }
}
