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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.cityfixmaas.data.model.LifecycleState
import com.example.cityfixmaas.data.model.MediaPhase
import com.example.cityfixmaas.data.model.UploadState
import com.example.cityfixmaas.data.repository.CategoryRepository
import com.example.cityfixmaas.data.repository.MediaRepository
import com.example.cityfixmaas.data.repository.TicketRepository
import com.example.cityfixmaas.di.appContainer
import com.example.cityfixmaas.ui.components.Base64Image
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One ticket with its photos and history. The operator can also change its state. */
class IncidentDetailViewModel(
    private val ticketId: String,
    private val tickets: TicketRepository,
    media: MediaRepository,
    categories: CategoryRepository,
) : ViewModel() {

    val ticket = tickets.observeTicket(ticketId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val history = tickets.observeHistory(ticketId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Full-size photos, available once PhotoUploadWorker has uploaded them. */
    val photos = media.observeMedia(ticketId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categoryNames = categories.observeCategories(activeOnly = false)
        .map { list -> list.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val error = MutableStateFlow<String?>(null)

    fun changeState(to: LifecycleState) {
        viewModelScope.launch {
            error.value = tickets.changeState(ticketId, to, null).exceptionOrNull()?.message
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
                IncidentDetailViewModel(ticketId, container.tickets, container.media, container.categories)
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
    val error by viewModel.error.collectAsStateWithLifecycle()
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(categoryNames[t.categoryId] ?: t.categoryId, style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(t.lifecycleState.labelRes()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(t.incidentType.labelRes()) + " · " +
                stringResource(if (t.publicScope) R.string.detail_public else R.string.detail_private) + " · " +
                formatTime(t.createdAt),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(t.description)

        Text(stringResource(R.string.detail_photos), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            t.media.forEach { ref ->
                // Full photo once uploaded; until then the thumbnail (or an empty box).
                val full = photos.find { it.id == ref.id }?.imageBase64
                Column {
                    Base64Image(full ?: ref.thumbnailBase64, Modifier.size(140.dp))
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

        if (isOperator) {
            HorizontalDivider()
            Text(stringResource(R.string.detail_change_state), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                t.lifecycleState.allowedNext().forEach { next ->
                    Button(onClick = { viewModel.changeState(next) }) { Text(stringResource(next.labelRes())) }
                }
            }
            if (t.lifecycleState == LifecycleState.IN_RESOLUTION) {
                OutlinedButton(onClick = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(stringResource(R.string.detail_add_resolution_photos))
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        HorizontalDivider()
        Text(stringResource(R.string.detail_history), style = MaterialTheme.typography.titleSmall)
        history.forEach { change ->
            val from = change.from?.let { stringResource(it.labelRes()) + " → " } ?: ""
            Text(from + stringResource(change.to.labelRes()) + " · " + formatTime(change.at), style = MaterialTheme.typography.bodySmall)
        }
    }
}
