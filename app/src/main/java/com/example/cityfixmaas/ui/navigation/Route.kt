package com.example.cityfixmaas.ui.navigation


import com.example.cityfixmaas.model.Role
import kotlinx.serialization.Serializable


// public / shared
@Serializable data object DashboardRoute
@Serializable data object IncidentListRoute
@Serializable data class IncidentDetailRoute(val incidentId: String)
@Serializable data object LoginRoute
@Serializable data object RegisterRoute

// citizen
@Serializable data object MyIncidentsRoute
@Serializable data object ReportIncidentRoute
@Serializable data object ProfileRoute

// operator
@Serializable data object PanelRoute
@Serializable data object PendingCitizensRoute
@Serializable data object CategoriesRoute

fun startRouteFor(role: Role): Any = when (role) {
    Role.ANONYMOUS -> DashboardRoute
    Role.CITIZEN -> MyIncidentsRoute
    Role.OPERATOR -> PanelRoute
}