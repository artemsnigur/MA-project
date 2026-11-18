package com.example.cityfixmaas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
import com.example.cityfixmaas.model.Role
import com.example.cityfixmaas.session.SessionViewModel
import com.example.cityfixmaas.ui.components.MainTopAppBar
import com.example.cityfixmaas.ui.navigation.*
import com.example.cityfixmaas.ui.screens.PlaceholderScreen
import com.example.cityfixmaas.ui.theme.CityFixMAASTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CityFixMAASTheme {
                val sessionViewModel: SessionViewModel = viewModel()
                val role by sessionViewModel.role.collectAsStateWithLifecycle()

                key(role) {
                    val navController = rememberNavController()
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val showFab = remember(role, backStackEntry?.destination) {
                        role == Role.CITIZEN &&
                                backStackEntry?.destination?.hasRoute<MyIncidentsRoute>() == true
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            MainTopAppBar(
                                navController = navController,
                                role = role,
                                onNavigate = { navController.navigate(it) { launchSingleTop = true } },
                                onSwitchRole = sessionViewModel::switchRole
                            )
                        },
                        floatingActionButton = {
                            if (showFab) {
                                FloatingActionButton(onClick = { navController.navigate(
                                    ReportIncidentRoute
                                ) }) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = stringResource(R.string.fab_new_incident)
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = startRouteFor(role),
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable<DashboardRoute> { PlaceholderScreen("Dashboard") }
                            composable<IncidentListRoute> { PlaceholderScreen("Incident list") }
                            composable<IncidentDetailRoute> { backStack ->
                                val route = backStack.toRoute<IncidentDetailRoute>()
                                PlaceholderScreen("Incident ${route.incidentId}")
                            }
                            composable<LoginRoute> { PlaceholderScreen("Login") }
                            composable<RegisterRoute> { PlaceholderScreen("Register") }
                            composable<MyIncidentsRoute> { PlaceholderScreen("My incidents") }
                            composable<ReportIncidentRoute> { PlaceholderScreen("Report incident") }
                            composable<ProfileRoute> { PlaceholderScreen("Profile") }
                            composable<PanelRoute> { PlaceholderScreen("Operations panel") }
                            composable<PendingCitizensRoute> { PlaceholderScreen("Pending citizens") }
                            composable<CategoriesRoute> { PlaceholderScreen("Categories") }
                        }
                    }
                }
            }
        }
    }
}
