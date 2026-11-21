package com.example.cityfixmaas.ui.publicview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.cityfixmaas.data.model.PublicTicket
import com.example.cityfixmaas.data.repository.CategoryRepository
import com.example.cityfixmaas.data.repository.PublicRepository
import com.example.cityfixmaas.di.appContainer
import com.example.cityfixmaas.ui.components.Base64Image
import com.example.cityfixmaas.ui.tickets.formatTime
import com.example.cityfixmaas.ui.tickets.labelRes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PublicIncidentsViewModel(
    publicRepo: PublicRepository,
    categoriesRepo: CategoryRepository,
) : ViewModel() {

    val tickets = publicRepo.observePublicTickets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categoryNames = categoriesRepo.observeCategories(activeOnly = false)
        .map { list -> list.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                PublicIncidentsViewModel(container.publicData, container.categories)
            }
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
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = List, 1 = Map

    Column(Modifier.fillMaxSize()) {
        // Toggle view: List vs Map
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SegmentedButton(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { Icon(Icons.Default.List, contentDescription = null, Modifier.size(18.dp)) },
            ) {
                Text(stringResource(R.string.list_view))
            }
            SegmentedButton(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(Icons.Default.Map, contentDescription = null, Modifier.size(18.dp)) },
            ) {
                Text(stringResource(R.string.map_view))
            }
        }

        if (selectedTab == 1) {
            // Map view with osmdroid
            OsmMapView(
                tickets = tickets,
                categoryNames = categoryNames,
                onMarkerClick = onOpen,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // List view
            if (tickets.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.tickets_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(tickets, key = { it.id }) { ticket ->
                        PublicTicketCard(
                            ticket = ticket,
                            categoryName = categoryNames[ticket.categoryId] ?: ticket.categoryId,
                            onClick = { onOpen(ticket.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PublicTicketCard(
    ticket: PublicTicket,
    categoryName: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                Base64Image(
                    ticket.thumbnailBase64,
                    Modifier.fillMaxSize(),
                )
            }
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    SuggestionChip(
                        onClick = onClick,
                        label = {
                            Text(
                                stringResource(ticket.lifecycleState.labelRes()),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        border = null,
                    )
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    stringResource(
                        R.string.approx_location,
                        ticket.approxLocation.latitude,
                        ticket.approxLocation.longitude,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    stringResource(ticket.incidentType.labelRes()) + " · " + formatTime(ticket.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
