package com.example.cityfixmaas.ui.publicview

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.example.cityfixmaas.data.repository.CategoryRepository
import com.example.cityfixmaas.data.repository.MediaRepository
import com.example.cityfixmaas.data.repository.PublicRepository
import com.example.cityfixmaas.di.appContainer
import com.example.cityfixmaas.ui.components.MediaPreview
import com.example.cityfixmaas.ui.components.IncidentMap
import com.example.cityfixmaas.ui.components.formatTime
import com.example.cityfixmaas.ui.components.labelRes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Details of a public incident for anyone (also anonymous users). Reads only publicTickets,
 * so the author and the exact coordinates are never loaded; the map is zoomed out.
 */
class PublicIncidentDetailViewModel(
    ticketId: String,
    publicData: PublicRepository,
    media: MediaRepository,
    categories: CategoryRepository,
) : ViewModel() {

    val ticket = publicData.observePublicTicket(ticketId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val photos = media.observeMedia(ticketId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categoryNames = categories.observeCategories(activeOnly = false)
        .map { list -> list.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    companion object {
        fun factory(ticketId: String) = viewModelFactory {
            initializer {
                val c = appContainer()
                PublicIncidentDetailViewModel(ticketId, c.publicData, c.media, c.categories)
            }
        }
    }
}

@Composable
fun PublicIncidentDetailScreen(
    ticketId: String,
    viewModel: PublicIncidentDetailViewModel = viewModel(factory = PublicIncidentDetailViewModel.factory(ticketId)),
) {
    val ticket by viewModel.ticket.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val categoryNames by viewModel.categoryNames.collectAsStateWithLifecycle()

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
                stringResource(t.incidentType.labelRes()) + " · " + formatTime(t.createdAt),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.approx_location, t.approxLocation.latitude, t.approxLocation.longitude),
            style = MaterialTheme.typography.bodySmall,
        )
        IncidentMap(t.approxLocation, zoom = 13.0, modifier = Modifier.fillMaxWidth().height(200.dp))

        Text(stringResource(R.string.detail_photos), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            photos.forEach { photo ->
                MediaPreview(photo.imageBase64, photo.videoBase64, Modifier.size(140.dp), contentDescription = stringResource(R.string.cd_incident_thumbnail))
            }
        }
    }
}
