package com.example.cityfixmaas.ui.components



import androidx.annotation.StringRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.cityfixmaas.R
import com.example.cityfixmaas.model.Role
import com.example.cityfixmaas.ui.navigation.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopAppBar(
    navController: NavHostController,
    role: Role,
    onNavigate: (Any) -> Unit,
    onSwitchRole: () -> Unit // TEMP (F0)
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val isHome = remember(role, destination) {
        when (role) {
            Role.ANONYMOUS -> destination?.hasRoute<DashboardRoute>() == true
            Role.CITIZEN -> destination?.hasRoute<MyIncidentsRoute>() == true
            Role.OPERATOR -> destination?.hasRoute<PanelRoute>() == true
        }
    }
    val titleRes = remember(destination) { titleFor(destination) }

    TopAppBar(
        title = { Text(stringResource(titleRes)) },
        navigationIcon = {
            if (!isHome) {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
            }
        },
        actions = {
            if (isHome) {
                when (role) {
                    Role.ANONYMOUS -> {
                        ActionIcon(Icons.AutoMirrored.Filled.List, R.string.cd_incident_list) { onNavigate(IncidentListRoute) }
                        ActionIcon(Icons.AutoMirrored.Filled.Login, R.string.cd_login) { onNavigate(LoginRoute) }
                    }
                    Role.CITIZEN -> {
                        ActionIcon(Icons.Default.BarChart, R.string.cd_dashboard) { onNavigate(DashboardRoute) }
                        ActionIcon(Icons.AutoMirrored.Filled.List, R.string.cd_incident_list) { onNavigate(IncidentListRoute) }
                        ActionIcon(Icons.Default.Person, R.string.cd_profile) { onNavigate(ProfileRoute) }
                    }
                    Role.OPERATOR -> {
                        ActionIcon(Icons.Default.HowToReg, R.string.cd_pending) { onNavigate(PendingCitizensRoute) }
                        ActionIcon(Icons.Default.Category, R.string.cd_categories) { onNavigate(CategoriesRoute) }
                        ActionIcon(Icons.Default.Person, R.string.cd_profile) { onNavigate(ProfileRoute) }
                    }
                }
            }
            // TEMP (F0): remove once login works
            ActionIcon(Icons.Default.SwapHoriz, R.string.cd_switch_role, onClick = onSwitchRole)
        }
    )
}

@Composable
private fun RowScope.ActionIcon(
    icon: ImageVector,
    @StringRes description: Int,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = stringResource(description))
    }
}

@StringRes
private fun titleFor(destination: NavDestination?): Int = when {
    destination == null -> R.string.app_name
    destination.hasRoute<DashboardRoute>() -> R.string.title_dashboard
    destination.hasRoute<IncidentListRoute>() -> R.string.title_incidents
    destination.hasRoute<IncidentDetailRoute>() -> R.string.title_incident_detail
    destination.hasRoute<LoginRoute>() -> R.string.title_login
    destination.hasRoute<RegisterRoute>() -> R.string.title_register
    destination.hasRoute<MyIncidentsRoute>() -> R.string.title_my_incidents
    destination.hasRoute<ReportIncidentRoute>() -> R.string.title_report_incident
    destination.hasRoute<ProfileRoute>() -> R.string.title_profile
    destination.hasRoute<PanelRoute>() -> R.string.title_panel
    destination.hasRoute<PendingCitizensRoute>() -> R.string.title_pending
    destination.hasRoute<CategoriesRoute>() -> R.string.title_categories
    else -> R.string.app_name
}