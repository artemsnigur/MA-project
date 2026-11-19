package com.example.cityfixmaas.ui.tickets

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.cityfixmaas.data.AppConfig
import com.example.cityfixmaas.data.model.IncidentType
import com.example.cityfixmaas.data.model.TicketDraft
import com.example.cityfixmaas.data.repository.CategoryRepository
import com.example.cityfixmaas.data.repository.LocationProvider
import com.example.cityfixmaas.data.repository.TicketRepository
import com.example.cityfixmaas.di.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportUiState(
    val categoryId: String = "",
    val incidentType: IncidentType = IncidentType.DAMAGE,
    val description: String = "",
    val publicScope: Boolean = true,
    val photos: List<Uri> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

class ReportIncidentViewModel(
    private val tickets: TicketRepository,
    categories: CategoryRepository,
    private val location: LocationProvider,
) : ViewModel() {

    val categories = categories.observeCategories(activeOnly = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(ReportUiState())
    val state = _state.asStateFlow()

    /** Every field change goes through here, e.g. `edit { it.copy(description = text) }`. */
    fun edit(change: (ReportUiState) -> ReportUiState) = _state.update { change(it).copy(error = null) }

    fun send() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val s = _state.value
            val here = location.currentLocation() ?: AppConfig.ANONYMOUS_AREA_CENTER
            val draft = TicketDraft(s.categoryId, s.incidentType, s.description, s.publicScope, here, s.photos)
            val result = tickets.createTicket(draft)
            _state.update { it.copy(isSaving = false, done = result.isSuccess, error = result.exceptionOrNull()?.message) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ReportIncidentViewModel(appContainer().tickets, appContainer().categories, appContainer().location) }
        }
    }
}

@Composable
fun ReportIncidentScreen(
    onDone: () -> Unit,
    viewModel: ReportIncidentViewModel = viewModel(factory = ReportIncidentViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        if (uris.isNotEmpty()) viewModel.edit { it.copy(photos = uris) }
    }
    LaunchedEffect(state.done) { if (state.done) onDone() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.report_category), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { category ->
                FilterChip(
                    selected = state.categoryId == category.id,
                    onClick = { viewModel.edit { it.copy(categoryId = category.id) } },
                    label = { Text(category.name) },
                )
            }
        }

        Text(stringResource(R.string.report_type), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IncidentType.entries.forEach { type ->
                FilterChip(
                    selected = state.incidentType == type,
                    onClick = { viewModel.edit { it.copy(incidentType = type) } },
                    label = { Text(stringResource(type.labelRes())) },
                )
            }
        }

        OutlinedTextField(
            value = state.description,
            onValueChange = { text -> viewModel.edit { it.copy(description = text) } },
            label = { Text(stringResource(R.string.report_description)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.report_public), Modifier.weight(1f))
            Switch(checked = state.publicScope, onCheckedChange = { checked -> viewModel.edit { it.copy(publicScope = checked) } })
        }

        OutlinedButton(onClick = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Text(stringResource(R.string.report_add_photos))
        }
        if (state.photos.isNotEmpty()) Text(stringResource(R.string.report_photos_selected, state.photos.size))

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(onClick = viewModel::send, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.report_send))
        }
    }
}
