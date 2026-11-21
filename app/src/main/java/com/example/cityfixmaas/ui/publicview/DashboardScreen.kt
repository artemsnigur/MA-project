package com.example.cityfixmaas.ui.publicview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.cityfixmaas.data.model.DashboardStats
import com.example.cityfixmaas.data.repository.PublicRepository
import com.example.cityfixmaas.di.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

/** Municipality numbers for everyone: total incidents, % still active, average repair time. */
class DashboardViewModel(publicData: PublicRepository) : ViewModel() {

    val stats = publicData.observeDashboard()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardStats())

    companion object {
        val Factory = viewModelFactory {
            initializer { DashboardViewModel(appContainer().publicData) }
        }
    }
}

@Composable
fun DashboardScreen(
    onNavigateToPublicIncidents: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val averageRepair = stats.averageRepairHours
        ?.let { String.format(Locale.US, "%.1f %s", it, stringResource(R.string.dashboard_hours_unit)) }
        ?: stringResource(R.string.dashboard_no_repairs_yet)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(stringResource(R.string.dashboard_total_tickets), stats.totalTickets.toString())
        StatCard(stringResource(R.string.dashboard_active_percent), String.format(Locale.US, "%.1f %%", stats.activePercent))
        StatCard(stringResource(R.string.dashboard_avg_repair_hours), averageRepair)
        Button(onClick = onNavigateToPublicIncidents, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.dashboard_view_public_list))
        }
    }
}

@Composable
private fun StatCard(title: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}
