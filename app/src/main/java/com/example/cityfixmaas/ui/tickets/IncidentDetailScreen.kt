package com.example.cityfixmaas.ui.tickets

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cityfixmaas.R
import com.example.cityfixmaas.data.model.LifecycleState
import com.example.cityfixmaas.data.model.MediaPhase
import com.example.cityfixmaas.data.model.Message
import com.example.cityfixmaas.data.model.UploadState
import com.example.cityfixmaas.data.model.UserRole
import com.example.cityfixmaas.data.repository.CategoryRepository
import com.example.cityfixmaas.data.repository.MediaRepository
import com.example.cityfixmaas.data.repository.MessageRepository
import com.example.cityfixmaas.data.repository.TicketRepository
import com.example.cityfixmaas.di.appContainer
import com.example.cityfixmaas.ui.components.Base64Image
import com.example.cityfixmaas.ui.publicview.SingleIncidentMapView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One ticket with its photos, upload status with retry, history and messaging. */
class IncidentDetailViewModel(
    private val ticketId: String,
    private val tickets: TicketRepository,
    media: MediaRepository,
    categories: CategoryRepository,
    private val messagesRepo: MessageRepository,
) : ViewModel() {

    val ticket = tickets.observeTicket(ticketId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val history = tickets.observeHistory(ticketId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Full-size photos, available once PhotoUploadWorker has uploaded them. */
    val photos = media.observeMedia(ticketId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categoryNames = categories.observeCategories(activeOnly = false)
        .map { list -> list.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val messages = messagesRepo.observeMessages(ticketId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messageInput = MutableStateFlow("")
    val messageInput = _messageInput.asStateFlow()

    private val _isSendingMessage = MutableStateFlow(false)
    val isSendingMessage = _isSendingMessage.asStateFlow()

    private val _isRetryingUploads = MutableStateFlow(false)
    val isRetryingUploads = _isRetryingUploads.asStateFlow()

    val error = MutableStateFlow<String?>(null)

    fun onMessageInputChange(text: String) {
        _messageInput.value = text
    }

    fun sendMessage() {
        val text = _messageInput.value.trim()
        if (text.isBlank() || _isSendingMessage.value) return
        viewModelScope.launch {
            _isSendingMessage.value = true
            val res = messagesRepo.sendMessage(ticketId, text)
            _isSendingMessage.value = false
            if (res.isSuccess) {
                _messageInput.value = ""
            } else {
                error.value = res.exceptionOrNull()?.message
            }
        }
    }

    fun retryUploads() {
        if (_isRetryingUploads.value) return
        viewModelScope.launch {
            _isRetryingUploads.value = true
            tickets.retryUploads(ticketId)
            _isRetryingUploads.value = false
        }
    }

    fun changeState(to: LifecycleState, note: String? = null) {
        viewModelScope.launch {
            error.value = tickets.changeState(ticketId, to, note).exceptionOrNull()?.message
        }
    }

    fun addResolutionPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            error.value = tickets.addResolutionPhotos(ticketId, uris).exceptionOrNull()?.message
        }
    }

    companion object {
        fun factory(ticketId: String) = viewModelFactory {
            initializer {
                val container = appContainer()
                IncidentDetailViewModel(
                    ticketId,
                    container.tickets,
                    container.media,
                    container.categories,
                    container.messages,
                )
            }
        }
    }
}

@Composable
fun IncidentDetailScreen(
    ticketId: String,
    isOperator: Boolean,
    viewModel: IncidentDetailViewModel = viewModel(factory = IncidentDetailViewModel.factory(ticketId)),
) {
    val ticket by viewModel.ticket.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val categoryNames by viewModel.categoryNames.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val messageInput by viewModel.messageInput.collectAsStateWithLifecycle()
    val isSendingMessage by viewModel.isSendingMessage.collectAsStateWithLifecycle()
    val isRetryingUploads by viewModel.isRetryingUploads.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var targetStateName by rememberSaveable { mutableStateOf<String?>(null) }
    val targetStateForDialog = targetStateName?.let { runCatching { LifecycleState.valueOf(it) }.getOrNull() }
    var resolutionPhotoRequiredError by rememberSaveable { mutableStateOf(false) }

    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) {
        viewModel.addResolutionPhotos(it)
    }

    val t = ticket
    if (t == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val hasFailedUploads = t.media.any { it.uploadState == UploadState.FAILED }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        // Header info
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(categoryNames[t.categoryId] ?: t.categoryId, style = MaterialTheme.typography.headlineSmall)
            SuggestionChip(
                onClick = {},
                label = { Text(stringResource(t.lifecycleState.labelRes()), fontWeight = FontWeight.Bold) },
            )
        }

        // Metadata badges
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(t.incidentType.labelRes()) + " · " +
                    stringResource(if (t.publicScope) R.string.detail_public else R.string.detail_private) + " · " +
                    formatTime(t.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Description
        Text(t.description, style = MaterialTheme.typography.bodyLarge)

        // Location coordinates and Map
        Text(
            stringResource(R.string.report_location_captured, t.location.latitude, t.location.longitude),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            SingleIncidentMapView(
                latitude = t.location.latitude,
                longitude = t.location.longitude,
                title = categoryNames[t.categoryId] ?: t.categoryId,
                modifier = Modifier.fillMaxSize(),
            )
        }

        HorizontalDivider()

        // Photos gallery
        Text(stringResource(R.string.detail_photos), style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            t.media.forEach { ref ->
                val full = photos.find { it.id == ref.id }?.imageBase64
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Base64Image(
                        full ?: ref.thumbnailBase64,
                        Modifier.size(130.dp),
                        contentDescription = stringResource(R.string.cd_incident_thumbnail),
                    )
                    val status = when {
                        ref.uploadState == UploadState.PENDING -> stringResource(R.string.photo_uploading)
                        ref.uploadState == UploadState.FAILED -> stringResource(R.string.photo_failed)
                        ref.phase == MediaPhase.RESOLUTION -> stringResource(R.string.photo_resolution)
                        else -> ""
                    }
                    if (status.isNotBlank()) {
                        Text(
                            status,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ref.uploadState == UploadState.FAILED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
            }
        }

        // Retry Upload Action if any photo failed
        if (hasFailedUploads) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.photo_failed),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::retryUploads,
                        enabled = !isRetryingUploads,
                    ) {
                        if (isRetryingUploads) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.retry_upload))
                        }
                    }
                }
            }
        }

        // Operator lifecycle actions
        if (isOperator) {
            HorizontalDivider()
            Text(stringResource(R.string.detail_change_state), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                t.lifecycleState.allowedNext().forEach { next ->
                    Button(onClick = {
                        if (next == LifecycleState.RESOLVED && t.media.none { it.phase == MediaPhase.RESOLUTION }) {
                            resolutionPhotoRequiredError = true
                        } else {
                            resolutionPhotoRequiredError = false
                            targetStateName = next.name
                        }
                    }) {
                        Text(stringResource(next.labelRes()))
                    }
                }
            }
            if (resolutionPhotoRequiredError) {
                Text(
                    stringResource(R.string.dialog_resolution_photo_required),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (t.lifecycleState == LifecycleState.IN_RESOLUTION) {
                OutlinedButton(
                    onClick = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                ) {
                    Text(stringResource(R.string.detail_add_resolution_photos))
                }
            }
        }

        targetStateForDialog?.let { nextState ->
            StateChangeNoteDialog(
                targetState = nextState,
                onConfirm = { note ->
                    viewModel.changeState(nextState, note)
                    targetStateName = null
                },
                onDismiss = { targetStateName = null },
            )
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider()

        // History timeline
        Text(stringResource(R.string.detail_history), style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            history.forEach { change ->
                val from = change.from?.let { stringResource(it.labelRes()) + " → " } ?: ""
                Text(
                    "• " + from + stringResource(change.to.labelRes()) + " · " + formatTime(change.at) +
                        (change.note?.let { " ($it)" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        HorizontalDivider()

        // Messages thread
        Text(stringResource(R.string.detail_messages), style = MaterialTheme.typography.titleMedium)

        if (messages.isEmpty()) {
            Text(
                stringResource(R.string.messages_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                messages.forEach { msg ->
                    MessageBubble(msg)
                }
            }
        }

        // Message input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = messageInput,
                onValueChange = viewModel::onMessageInputChange,
                placeholder = { Text(stringResource(R.string.message_hint)) },
                modifier = Modifier.weight(1f),
                maxLines = 3,
            )
            IconButton(
                onClick = viewModel::sendMessage,
                enabled = messageInput.isNotBlank() && !isSendingMessage,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                if (isSendingMessage) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.message_send),
                        tint = if (messageInput.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val isOperator = message.senderRole == UserRole.OPERATOR
    val containerColor = if (isOperator) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isOperator) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val roleBadge = if (isOperator) {
        stringResource(R.string.message_operator_badge)
    } else {
        stringResource(R.string.message_citizen_badge)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    roleBadge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                Text(
                    formatTime(message.sentAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun StateChangeNoteDialog(
    targetState: LifecycleState,
    onConfirm: (note: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.dialog_state_change_title) + " → " + stringResource(targetState.labelRes()))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.dialog_state_note_label)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note.takeIf { it.isNotBlank() }) }) {
                Text(stringResource(R.string.dialog_state_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
