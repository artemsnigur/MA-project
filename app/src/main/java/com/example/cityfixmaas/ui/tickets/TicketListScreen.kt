package com.example.cityfixmaas.ui.tickets

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cityfixmaas.R
import com.example.cityfixmaas.data.model.Ticket
import com.example.cityfixmaas.data.repository.CategoryRepository
import com.example.cityfixmaas.data.repository.TicketRepository
import com.example.cityfixmaas.di.appContainer
import com.example.cityfixmaas.ui.components.Base64Image
import com.example.cityfixmaas.ui.components.formatTime
import com.example.cityfixmaas.ui.components.labelRes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Tickets of the signed-in citizen ("My incidents"), or all tickets for the operator panel. */
class TicketListViewModel(tickets: TicketRepository, categories: CategoryRepository, allTickets: Boolean) : ViewModel() {

    val tickets = (if (allTickets) tickets.observeAllTickets() else tickets.observeMyTickets())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Category id → name, so the list shows names instead of ids. */
    val categoryNames = categories.observeCategories(activeOnly = false)
        .map { list -> list.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    companion object {
        fun factory(allTickets: Boolean) = viewModelFactory {
            initializer { TicketListViewModel(appContainer().tickets, appContainer().categories, allTickets) }
        }
    }
}

/** [allTickets] = true is the operator panel: every ticket, with its exact coordinates. */
@Composable
fun TicketListScreen(
    allTickets: Boolean,
    onOpen: (ticketId: String) -> Unit,
    viewModel: TicketListViewModel = viewModel(factory = TicketListViewModel.factory(allTickets)),
) {
    val tickets by viewModel.tickets.collectAsStateWithLifecycle()
    val categoryNames by viewModel.categoryNames.collectAsStateWithLifecycle()

    if (tickets.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.tickets_empty)) }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(tickets, key = { it.id }) { ticket ->
            TicketCard(
                ticket = ticket,
                categoryName = categoryNames[ticket.categoryId] ?: ticket.categoryId,
                showCoordinates = allTickets,
                onClick = { onOpen(ticket.id) },
            )
        }
    }
}

@Composable
private fun TicketCard(ticket: Ticket, categoryName: String, showCoordinates: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Base64Image(
                ticket.media.firstOrNull()?.thumbnailBase64,
                Modifier.size(72.dp),
                contentDescription = stringResource(R.string.cd_incident_thumbnail),
            )
            Column(Modifier.weight(1f)) {
                Text(categoryName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(ticket.description, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(ticket.lifecycleState.labelRes()) + " · " +
                        stringResource(ticket.incidentType.labelRes()) + " · " + formatTime(ticket.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (showCoordinates) {
                    Text(
                        stringResource(R.string.exact_coordinates, ticket.location.latitude, ticket.location.longitude),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}
