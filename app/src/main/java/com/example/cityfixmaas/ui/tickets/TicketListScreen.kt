package com.example.cityfixmaas.ui.tickets

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.cityfixmaas.data.model.Category
import com.example.cityfixmaas.data.model.LifecycleState
import com.example.cityfixmaas.data.model.Ticket
import com.example.cityfixmaas.data.model.User
import com.example.cityfixmaas.data.repository.CategoryRepository
import com.example.cityfixmaas.data.repository.TicketRepository
import com.example.cityfixmaas.data.repository.UserAdminRepository
import com.example.cityfixmaas.di.appContainer
import com.example.cityfixmaas.ui.components.Base64Image
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Tickets of the signed-in citizen ("My incidents"), or all tickets for the operator panel with filters. */
class TicketListViewModel(
    ticketsRepo: TicketRepository,
    categoriesRepo: CategoryRepository,
    val allTickets: Boolean,
) : ViewModel() {

    val allCategories = categoriesRepo.observeCategories(activeOnly = false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Category id → name, so the list shows names instead of ids. */
    val categoryNames = allCategories
        .map { list -> list.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _rawTickets = (if (allTickets) ticketsRepo.observeAllTickets() else ticketsRepo.observeMyTickets())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedState = MutableStateFlow<LifecycleState?>(null)
    val selectedState = _selectedState.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId = _selectedCategoryId.asStateFlow()

    val filteredTickets = combine(_rawTickets, _selectedState, _selectedCategoryId) { tickets, stateFilter, catFilter ->
        tickets.filter { t ->
            (stateFilter == null || t.lifecycleState == stateFilter) &&
                (catFilter == null || t.categoryId == catFilter)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectStateFilter(state: LifecycleState?) {
        _selectedState.value = state
    }

    fun selectCategoryFilter(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    companion object {
        fun factory(allTickets: Boolean) = viewModelFactory {
            initializer {
                val container = appContainer()
                TicketListViewModel(container.tickets, container.categories, allTickets)
            }
        }
    }
}

@Composable
fun TicketListScreen(
    allTickets: Boolean,
    onOpen: (ticketId: String) -> Unit,
    viewModel: TicketListViewModel = viewModel(factory = TicketListViewModel.factory(allTickets)),
) {
    val tickets by viewModel.filteredTickets.collectAsStateWithLifecycle()
    val categoryNames by viewModel.categoryNames.collectAsStateWithLifecycle()
    val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
    val selectedState by viewModel.selectedState.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        // Filters section (for Operator Panel or list filtering)
        if (allTickets) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // State filter row
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedState == null,
                        onClick = { viewModel.selectStateFilter(null) },
                        label = { Text(stringResource(R.string.filter_all_states)) },
                    )
                    LifecycleState.entries.forEach { state ->
                        FilterChip(
                            selected = selectedState == state,
                            onClick = { viewModel.selectStateFilter(if (selectedState == state) null else state) },
                            label = { Text(stringResource(state.labelRes())) },
                        )
                    }
                }

                // Category filter row
                if (allCategories.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { viewModel.selectCategoryFilter(null) },
                            label = { Text(stringResource(R.string.filter_all_categories)) },
                        )
                        allCategories.forEach { category ->
                            FilterChip(
                                selected = selectedCategoryId == category.id,
                                onClick = {
                                    viewModel.selectCategoryFilter(
                                        if (selectedCategoryId == category.id) null else category.id,
                                    )
                                },
                                label = { Text(category.name) },
                            )
                        }
                    }
                }
            }
        }

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
                    TicketCard(
                        ticket = ticket,
                        categoryName = categoryNames[ticket.categoryId] ?: ticket.categoryId,
                        showOperatorDetails = allTickets,
                        onClick = { onOpen(ticket.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TicketCard(
    ticket: Ticket,
    categoryName: String,
    showOperatorDetails: Boolean,
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
                    ticket.media.firstOrNull()?.thumbnailBase64,
                    Modifier.fillMaxSize(),
                    contentDescription = stringResource(R.string.cd_incident_thumbnail),
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
                    ticket.description,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    stringResource(ticket.incidentType.labelRes()) + " · " + formatTime(ticket.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )

                // Show exact coordinates for operators
                if (showOperatorDetails) {
                    Spacer(Modifier.height(2.dp))
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
