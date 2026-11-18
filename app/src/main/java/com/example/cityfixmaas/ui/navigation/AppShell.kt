package com.example.cityfixmaas.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.cityfixmaas.R
import com.example.cityfixmaas.ui.components.LoadingScreen
import com.example.cityfixmaas.ui.components.MainTopAppBar
import com.example.cityfixmaas.ui.components.PlaceholderScreen

/**
 * Root of the UI: picks the graph from the session and hosts the shared Scaffold.
 * When the session changes (sign in, sign out, activation) the graph restarts at the new
 * mode's start screen. F1–F3 replace each PlaceholderScreen with the feature's XRoute.
 */
@Composable
fun AppShell(sessionViewModel: SessionViewModel = viewModel(factory = SessionViewModel.Factory)) {
    val mode by sessionViewModel.mode.collectAsStateWithLifecycle()
    val currentMode = mode ?: return LoadingScreen()

    key(currentMode) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val showFab = currentMode == AppMode.CITIZEN &&
            backStackEntry?.destination?.hasRoute<MyIncidentsRoute>() == true

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                MainTopAppBar(
                    navController = navController,
                    mode = currentMode,
                    onNavigate = { navController.navigate(it) { launchSingleTop = true } },
                    onSignOut = sessionViewModel::signOut,
                    onCycleDemoAccount = sessionViewModel::cycleDemoAccount,
                )
            },
            floatingActionButton = {
                if (showFab) {
                    FloatingActionButton(onClick = { navController.navigate(ReportIncidentRoute) }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.fab_new_incident))
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = startRouteFor(currentMode),
                modifier = Modifier.padding(innerPadding),
            ) {
                composable<DashboardRoute> { PlaceholderScreen(R.string.title_dashboard) }
                composable<IncidentListRoute> { PlaceholderScreen(R.string.title_incidents) }
                composable<IncidentDetailRoute> { entry ->
                    PlaceholderScreen(R.string.title_incident_detail, detail = entry.toRoute<IncidentDetailRoute>().incidentId)
                }
                composable<LoginRoute> { PlaceholderScreen(R.string.title_login) }
                composable<RegisterRoute> { PlaceholderScreen(R.string.title_register) }
                composable<MyIncidentsRoute> { PlaceholderScreen(R.string.title_my_incidents) }
                composable<ReportIncidentRoute> { PlaceholderScreen(R.string.title_report_incident) }
                composable<ProfileRoute> { PlaceholderScreen(R.string.title_profile) }
                composable<AccountPendingRoute> { PlaceholderScreen(R.string.title_account_pending) }
                composable<PanelRoute> { PlaceholderScreen(R.string.title_panel) }
                composable<PendingCitizensRoute> { PlaceholderScreen(R.string.title_pending) }
                composable<CategoriesRoute> { PlaceholderScreen(R.string.title_categories) }
            }
        }
    }
}
