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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cityfixmaas.R
import com.example.cityfixmaas.data.AppConfig
import com.example.cityfixmaas.data.model.LifecycleState
import com.example.cityfixmaas.data.model.MediaPhase
import com.example.cityfixmaas.data.model.Message
import com.example.cityfixmaas.data.model.UploadState
import com.example.cityfixmaas.data.model.UserRole
import com.example.cityfixmaas.data.repository.CategoryRepository
import com.example.cityfixmaas.data.repository.MediaRepository
import com.example.cityfixmaas.data.repository.MessageRepository
import com.example.cityfixmaas.data.repository.TicketRepository
import com.example.cityfixmaas.data.repository.UserAdminRepository
import com.example.cityfixmaas.di.appContainer
import com.example.cityfixmaas.ui.components.MediaPreview
import com.example.cityfixmaas.ui.components.IncidentMap
import com.example.cityfixmaas.ui.components.formatTime
import com.example.cityfixmaas.ui.components.labelRes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One ticket for its author or an operator: photos, map, history and messages.
 * The operator also sees the author's contact data and can change the state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IncidentDetailViewModel(
    private val ticketId: String,
    private val tickets: TicketRepository,
    media: MediaRepository,
    categories: CategoryRepository,
    private val messages: MessageRepository,
    userAdmin: UserAdminRepository,
) : ViewModel() {

    val ticket = tickets.observeTicket(ticketId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val history = tickets.observeHistory(ticketId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val messageList = messages.observeMessages(ticketId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Full-size photos, available once PhotoUploadWorker has uploaded them. */
    val photos = media.observeMedia(ticketId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categoryNames = categories.observeCategories(activeOnly = false)
        .map { list -> list.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The author's profile (name, email, phone), loaded once the ticket is known. */
    val author = tickets.observeTicket(ticketId)
        .map { it?.authorId }
        .distinctUntilChanged()
        .flatMapLatest { uid -> if (uid == null) flowOf(null) else userAdmin.observeUser(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val error = MutableStateFlow<String?>(null)

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { error.value = messages.sendMessage(ticketId, text.trim()).exceptionOrNull()?.message }
    }

    /** The repository refuses invalid moves, e.g. RESOLVED without resolution photos. */
    fun changeState(to: LifecycleState, note: String?) {
        viewModelScope.launch { error.value = tickets.changeState(ticketId, to, note).exceptionOrNull()?.message }
    }

    /** Author only, within 5 days of the resolution; the justification is mandatory. */
    fun reopen(justification: String) {
        viewModelScope.launch { error.value = tickets.reopenTicket(ticketId, justification).exceptionOrNull()?.message }
    }

    /** Author or follower, while the ticket is open: ask the municipality a question. */
    fun requestInformation(question: String) {
        viewModelScope.launch { error.value = tickets.requestInformation(ticketId, question).exceptionOrNull()?.message }
    }

    fun addResolutionPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch { error.value = tickets.addResolutionPhotos(ticketId, uris).exceptionOrNull()?.message }
    }

    companion object {
        fun factory(ticketId: String) = viewModelFactory {
            initializer {
                val c = appContainer()
                IncidentDetailViewModel(ticketId, c.tickets, c.media, c.categories, c.messages, c.userAdmin)
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
    val messages by viewModel.messageList.collectAsStateWithLifecycle()
    val categoryNames by viewModel.categoryNames.collectAsStateWithLifecycle()
    val author by viewModel.author.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var messageText by rememberSaveable { mutableStateOf("") }
    var stateForDialog by rememberSaveable { mutableStateOf<LifecycleState?>(null) } // enums survive rotation
    var showReopenDialog by rememberSaveable { mutableStateOf(false) }
    var showQuestionDialog by rememberSaveable { mutableStateOf(false) }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) {
        viewModel.addResolutionPhotos(it)
    }

    val t = ticket
    if (t == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(categoryNames[t.categoryId] ?: t.categoryId, style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(t.lifecycleState.labelRes()) + " · " +
                stringResource(t.incidentType.labelRes()) + " · " +
                stringResource(if (t.publicScope) R.string.detail_public else R.string.detail_private) + " · " +
                formatTime(t.createdAt),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(t.description, style = MaterialTheme.typography.bodyLarge)
        if (isOperator) {
            author?.let { Text(stringResource(R.string.author_contact, it.name, it.email, it.phone)) }
        }

        // Exact place: only the author and operators open this screen.
        Text(
            stringResource(R.string.exact_coordinates, t.location.latitude, t.location.longitude),
            style = MaterialTheme.typography.bodySmall,
        )
        IncidentMap(t.location, zoom = 16.0, modifier = Modifier.fillMaxWidth().height(180.dp))

        Text(stringResource(R.string.detail_photos), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            t.media.forEach { ref ->
                // Full photo (or playable video) once uploaded; until then the thumbnail (or an empty box).
                val full = photos.find { it.id == ref.id }
                Column {
                    MediaPreview(full?.imageBase64 ?: ref.thumbnailBase64, full?.videoBase64, Modifier.size(130.dp), stringResource(R.string.cd_incident_thumbnail))
                    val status = when {
                        ref.uploadState == UploadState.PENDING -> stringResource(R.string.photo_uploading)
                        ref.uploadState == UploadState.FAILED -> stringResource(R.string.photo_failed)
                        ref.phase == MediaPhase.RESOLUTION -> stringResource(R.string.photo_resolution)
                        else -> ""
                    }
                    Text(status, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (t.followerIds.isNotEmpty()) {
            Text(stringResource(R.string.detail_followers, t.followerIds.size), style = MaterialTheme.typography.bodySmall)
        }

        if (isOperator) {
            HorizontalDivider()
            Text(stringResource(R.string.detail_change_state), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                t.lifecycleState.allowedNext().forEach { next ->
                    Button(onClick = { stateForDialog = next }) { Text(stringResource(next.labelRes())) }
                }
            }
            if (t.lifecycleState == LifecycleState.IN_RESOLUTION) {
                OutlinedButton(onClick = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) {
                    Text(stringResource(R.string.detail_add_resolution_photos))
                }
            }
        }
        if (!isOperator && t.lifecycleState == LifecycleState.OPEN) {
            OutlinedButton(onClick = { showQuestionDialog = true }) { Text(stringResource(R.string.detail_request_info)) }
        }
        if (!isOperator && t.canReopen(System.currentTimeMillis())) {
            HorizontalDivider()
            Text(stringResource(R.string.detail_reopen_until, formatTime(t.resolvedAt!! + AppConfig.REOPEN_WINDOW_MS)))
            OutlinedButton(onClick = { showReopenDialog = true }) { Text(stringResource(R.string.detail_reopen)) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        HorizontalDivider()
        Text(stringResource(R.string.detail_history), style = MaterialTheme.typography.titleMedium)
        history.forEach { change ->
            val from = change.from?.let { stringResource(it.labelRes()) + " → " } ?: ""
            val note = change.note?.let { " ($it)" } ?: ""
            Text(from + stringResource(change.to.labelRes()) + " · " + formatTime(change.at) + note, style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider()
        Text(stringResource(R.string.detail_messages), style = MaterialTheme.typography.titleMedium)
        if (messages.isEmpty()) Text(stringResource(R.string.messages_empty), style = MaterialTheme.typography.bodySmall)
        messages.forEach { MessageCard(it) }
        // A closed ticket is archived and locked, so no new messages.
        if (t.lifecycleState == LifecycleState.CLOSED) {
            Text(stringResource(R.string.detail_closed_locked), style = MaterialTheme.typography.bodySmall)
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                placeholder = { Text(stringResource(R.string.message_hint)) },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.sendMessage(messageText); messageText = "" }, enabled = messageText.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.message_send))
            }
        }
    }

    stateForDialog?.let { next ->
        StateChangeDialog(
            title = stringResource(R.string.dialog_state_change_title) + " → " + stringResource(next.labelRes()),
            noteLabel = stringResource(R.string.dialog_state_note_label),
            noteRequired = false,
            onConfirm = { note -> viewModel.changeState(next, note); stateForDialog = null },
            onDismiss = { stateForDialog = null },
        )
    }
    if (showQuestionDialog) {
        StateChangeDialog(
            title = stringResource(R.string.dialog_request_info_title),
            noteLabel = stringResource(R.string.dialog_request_info_label),
            noteRequired = true,
            onConfirm = { question -> viewModel.requestInformation(question.orEmpty()); showQuestionDialog = false },
            onDismiss = { showQuestionDialog = false },
        )
    }
    if (showReopenDialog) {
        StateChangeDialog(
            title = stringResource(R.string.dialog_reopen_title),
            noteLabel = stringResource(R.string.dialog_reopen_label),
            noteRequired = true,
            onConfirm = { note -> viewModel.reopen(note.orEmpty()); showReopenDialog = false },
            onDismiss = { showReopenDialog = false },
        )
    }
}

/** Operator messages are highlighted. */
@Composable
private fun MessageCard(message: Message) {
    val fromOperator = message.senderRole == UserRole.OPERATOR
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (fromOperator) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                stringResource(if (fromOperator) R.string.message_operator_badge else R.string.message_citizen_badge) +
                    " · " + formatTime(message.sentAt),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(message.text)
        }
    }
}

/** Asks for a note (optional for operators, required to reopen), saved with the state change in the history. */
@Composable
private fun StateChangeDialog(
    title: String,
    noteLabel: String,
    noteRequired: Boolean,
    onConfirm: (note: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(noteLabel) },
                minLines = 2,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(note.ifBlank { null }) }, enabled = !noteRequired || note.isNotBlank()) { Text(stringResource(R.string.dialog_state_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
