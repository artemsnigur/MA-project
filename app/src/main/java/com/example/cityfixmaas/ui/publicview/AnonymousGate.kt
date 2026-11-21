package com.example.cityfixmaas.ui.publicview

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cityfixmaas.R
import com.example.cityfixmaas.data.repository.LocationProvider
import com.example.cityfixmaas.di.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Null while checking, then true (inside 10 km of Praça da República) or false. */
class AnonymousGateViewModel(private val location: LocationProvider) : ViewModel() {

    val isInside = MutableStateFlow<Boolean?>(null)

    /** No location (permission denied or no GPS fix) also counts as outside. */
    fun check() {
        viewModelScope.launch {
            isInside.value = null
            val here = location.currentLocation()
            isInside.value = here != null && location.isWithinAnonymousArea(here)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { AnonymousGateViewModel(appContainer().location) }
        }
    }
}

/**
 * Anonymous users may only use the app within 10 km of Praça da República.
 * AppShell wraps every anonymous screen (dashboard, public list, public details) in this gate;
 * signed-in users skip it. Outside the area the user can still sign in.
 */
@Composable
fun AnonymousGate(
    onSignIn: () -> Unit,
    viewModel: AnonymousGateViewModel = viewModel(factory = AnonymousGateViewModel.Factory),
    content: @Composable () -> Unit,
) {
    val isInside by viewModel.isInside.collectAsStateWithLifecycle()
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.check() // granted or not, check() decides: no location means outside
    }
    val checkLocation = {
        askPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(Unit) { checkLocation() }

    when (isInside) {
        true -> content()
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.gate_checking_location))
            }
        }
        false -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.gate_blocked_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.gate_blocked_desc), textAlign = TextAlign.Center)
            Button(onClick = checkLocation) { Text(stringResource(R.string.gate_retry_location)) }
            OutlinedButton(onClick = onSignIn) { Text(stringResource(R.string.action_sign_in)) }
        }
    }
}
