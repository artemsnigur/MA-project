package com.example.cityfixmaas.ui.publicview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.cityfixmaas.data.repository.CategoryRepository
import com.example.cityfixmaas.data.repository.PublicRepository
import com.example.cityfixmaas.di.appContainer
import com.example.cityfixmaas.ui.components.Base64Image
import com.example.cityfixmaas.ui.components.formatTime
import com.example.cityfixmaas.ui.components.labelRes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Active public incidents: category, state and thumbnail, without author or exact location. */
class PublicIncidentsViewModel(publicData: PublicRepository, categories: CategoryRepository) : ViewModel() {

    val tickets = publicData.observePublicTickets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categoryNames = categories.observeCategories(activeOnly = false)
        .map { list -> list.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    companion object {
        val Factory = viewModelFactory {
            initializer { PublicIncidentsViewModel(appContainer().publicData, appContainer().categories) }
        }
    }
}

@Composable
fun PublicIncidentsScreen(
    onOpen: (ticketId: String) -> Unit,
    viewModel: PublicIncidentsViewModel = viewModel(factory = PublicIncidentsViewModel.Factory),
) {
    val tickets by viewModel.tickets.collectAsStateWithLifecycle()
    val categoryNames by viewModel.categoryNames.collectAsStateWithLifecycle()

    if (tickets.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.tickets_empty)) }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(tickets, key = { it.id }) { ticket ->
            Card(onClick = { onOpen(ticket.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Base64Image(
                        ticket.thumbnailBase64,
                        Modifier.size(72.dp),
                        contentDescription = stringResource(R.string.cd_incident_thumbnail),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            categoryNames[ticket.categoryId] ?: ticket.categoryId,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(ticket.lifecycleState.labelRes()) + " · " +
                                stringResource(ticket.incidentType.labelRes()) + " · " + formatTime(ticket.createdAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
