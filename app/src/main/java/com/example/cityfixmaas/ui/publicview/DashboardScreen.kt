package com.example.cityfixmaas.ui.publicview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cityfixmaas.R
import com.example.cityfixmaas.data.model.DashboardStats
import com.example.cityfixmaas.data.repository.LocationProvider
import com.example.cityfixmaas.data.repository.PublicRepository
import com.example.cityfixmaas.di.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class DashboardViewModel(
    private val locationProvider: LocationProvider,
    publicRepo: PublicRepository,
    private val isAnonymous: Boolean,
) : ViewModel() {

    val stats = publicRepo.observeDashboard()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardStats())

    private val _isCheckingLocation = MutableStateFlow(isAnonymous)
    val isCheckingLocation = _isCheckingLocation.asStateFlow()

    private val _isWithinArea = MutableStateFlow(true)
    val isWithinArea = _isWithinArea.asStateFlow()

    init {
        if (isAnonymous) {
            checkLocation()
        }
    }

    fun checkLocation() {
        viewModelScope.launch {
            _isCheckingLocation.value = true
            val loc = locationProvider.currentLocation()
            _isWithinArea.value = loc == null || locationProvider.isWithinAnonymousArea(loc)
            _isCheckingLocation.value = false
        }
    }

    companion object {
        fun factory(isAnonymous: Boolean) = viewModelFactory {
            initializer {
                val container = appContainer()
                DashboardViewModel(container.location, container.publicData, isAnonymous)
            }
        }
    }
}

@Composable
fun DashboardScreen(
    isAnonymous: Boolean,
    onNavigateToPublicIncidents: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(isAnonymous)),
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val isCheckingLocation by viewModel.isCheckingLocation.collectAsStateWithLifecycle()
    val isWithinArea by viewModel.isWithinArea.collectAsStateWithLifecycle()

    if (isAnonymous && isCheckingLocation) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    stringResource(R.string.gate_checking_location),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        return
    }

    // 10 km Gate Blocked Screen
    if (isAnonymous && !isWithinArea) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.LocationOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.gate_blocked_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.gate_blocked_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = viewModel::checkLocation,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.gate_retry_location))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onNavigateToLogin,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_sign_in))
                }
            }
        }
        return
    }

    // Dashboard Screen Content
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.title_dashboard),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        // Stat Card 1: Total Tickets
        StatCard(
            title = stringResource(R.string.dashboard_total_tickets),
            value = stats.totalTickets.toString(),
            icon = Icons.Default.ReportProblem,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )

        // Stat Card 2: Active Tickets %
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            stringResource(R.string.dashboard_active_percent),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            String.format(Locale.US, "%.1f%%", stats.activePercent),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Icon(
                        Icons.Default.PendingActions,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (stats.activePercent / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Stat Card 3: Average Repair Hours
        val avgHoursText = stats.averageRepairHours?.let {
            String.format(Locale.US, "%.1f %s", it, stringResource(R.string.dashboard_hours_unit))
        } ?: stringResource(R.string.dashboard_no_repairs_yet)

        StatCard(
            title = stringResource(R.string.dashboard_avg_repair_hours),
            value = avgHoursText,
            icon = Icons.Default.HourglassEmpty,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )

        Spacer(Modifier.height(8.dp))

        // Action button to view public incidents and map
        Button(
            onClick = onNavigateToPublicIncidents,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(stringResource(R.string.dashboard_view_public_list))
            Spacer(Modifier.size(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = contentColor)
                Spacer(Modifier.height(4.dp))
                Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = contentColor)
            }
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = contentColor)
        }
    }
}
