package com.example.cityfixmaas.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
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
import com.example.cityfixmaas.ui.auth.AccountPendingScreen
import com.example.cityfixmaas.ui.auth.LoginRoute
import com.example.cityfixmaas.ui.auth.RegisterRoute
import com.example.cityfixmaas.ui.components.MainTopAppBar
import com.example.cityfixmaas.ui.operator.CategoriesScreen
import com.example.cityfixmaas.ui.operator.PendingCitizensScreen
import com.example.cityfixmaas.ui.profile.ProfileRoute
import com.example.cityfixmaas.ui.publicview.DashboardScreen
import com.example.cityfixmaas.ui.publicview.PublicIncidentDetailScreen
import com.example.cityfixmaas.ui.publicview.AnonymousGate
import com.example.cityfixmaas.ui.publicview.PublicIncidentsScreen
import com.example.cityfixmaas.ui.tickets.IncidentDetailScreen
import com.example.cityfixmaas.ui.tickets.ReportIncidentScreen
import com.example.cityfixmaas.ui.tickets.TicketListScreen

/**
 * Root of the UI: picks the graph from the session and hosts the shared Scaffold.
 * When the session changes (sign in, sign out, activation) the graph restarts at the new
 * mode's start screen.
 */
@Composable
fun AppShell(sessionViewModel: SessionViewModel = viewModel(factory = SessionViewModel.Factory)) {
    val mode by sessionViewModel.mode.collectAsStateWithLifecycle()
    val currentMode = mode
    if (currentMode == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    key(currentMode) {
        val anonymous = currentMode == AppMode.ANONYMOUS
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
                composable<DashboardRoute> {
                    GateIfAnonymous(anonymous, onSignIn = { navController.navigate(LoginRoute) }) {
                        DashboardScreen(onNavigateToPublicIncidents = { navController.navigate(IncidentListRoute) })
                    }
                }
                composable<IncidentListRoute> {
                    GateIfAnonymous(anonymous, onSignIn = { navController.navigate(LoginRoute) }) {
                        PublicIncidentsScreen(onOpen = { navController.navigate(PublicIncidentDetailRoute(it)) })
                    }
                }
                composable<PublicIncidentDetailRoute> { entry ->
                    GateIfAnonymous(anonymous, onSignIn = { navController.navigate(LoginRoute) }) {
                        PublicIncidentDetailScreen(entry.toRoute<PublicIncidentDetailRoute>().incidentId)
                    }
                }
                composable<IncidentDetailRoute> { entry ->
                    IncidentDetailScreen(
                        ticketId = entry.toRoute<IncidentDetailRoute>().incidentId,
                        isOperator = currentMode == AppMode.OPERATOR,
                    )
                }
                composable<LoginRoute> {
                    LoginRoute(onNavigateToRegister = { navController.navigate(RegisterRoute) })
                }
                composable<RegisterRoute> {
                    RegisterRoute(onNavigateToLogin = { navController.navigateUp() })
                }
                composable<MyIncidentsRoute> {
                    TicketListScreen(allTickets = false, onOpen = { navController.navigate(IncidentDetailRoute(it)) })
                }
                composable<ReportIncidentRoute> { ReportIncidentScreen(onDone = { navController.popBackStack() }) }
                composable<ProfileRoute> { ProfileRoute() }
                composable<AccountPendingRoute> {
                    AccountPendingScreen(onSignOut = sessionViewModel::signOut)
                }
                composable<PanelRoute> {
                    TicketListScreen(allTickets = true, onOpen = { navController.navigate(IncidentDetailRoute(it)) })
                }
                composable<PendingCitizensRoute> { PendingCitizensScreen() }
                composable<CategoriesRoute> { CategoriesScreen() }
            }
        }
    }
}

/** Anonymous users must be within 10 km of Praça da República; signed-in users skip the check. */
@Composable
private fun GateIfAnonymous(anonymous: Boolean, onSignIn: () -> Unit, content: @Composable () -> Unit) {
    if (anonymous) AnonymousGate(onSignIn = onSignIn, content = content) else content()
}
