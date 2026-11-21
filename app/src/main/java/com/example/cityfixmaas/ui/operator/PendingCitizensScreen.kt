package com.example.cityfixmaas.ui.operator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.example.cityfixmaas.data.repository.UserAdminRepository
import com.example.cityfixmaas.di.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Citizens who registered and wait for an operator to activate their account. */
class PendingCitizensViewModel(private val admin: UserAdminRepository) : ViewModel() {

    val citizens = admin.observePendingCitizens()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun activate(uid: String) {
        viewModelScope.launch { admin.activateCitizen(uid) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PendingCitizensViewModel(appContainer().userAdmin) }
        }
    }
}

@Composable
fun PendingCitizensScreen(viewModel: PendingCitizensViewModel = viewModel(factory = PendingCitizensViewModel.Factory)) {
    val citizens by viewModel.citizens.collectAsStateWithLifecycle()

    if (citizens.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.pending_empty)) }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(citizens, key = { it.id }) { user ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(user.name, style = MaterialTheme.typography.titleMedium)
                        Text(user.email, style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.pending_details, user.phone, user.nif), style = MaterialTheme.typography.bodySmall)
                        user.location?.let { loc ->
                            Text(stringResource(R.string.pending_location, loc.latitude, loc.longitude), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(onClick = { viewModel.activate(user.id) }) { Text(stringResource(R.string.pending_activate)) }
                }
            }
        }
    }
}
