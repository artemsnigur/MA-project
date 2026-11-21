package com.example.cityfixmaas.ui.navigation

import kotlinx.serialization.Serializable

// Screens of the app. Arguments are ids only; each screen loads its data by id.

// Public / shared
@Serializable data object DashboardRoute
@Serializable data object IncidentListRoute
@Serializable data class IncidentDetailRoute(val incidentId: String) // full ticket: author and operators
@Serializable data class PublicIncidentDetailRoute(val incidentId: String) // public copy: anyone
@Serializable data object LoginRoute
@Serializable data object RegisterRoute

// Citizen
@Serializable data object MyIncidentsRoute
@Serializable data object ReportIncidentRoute
@Serializable data object ProfileRoute
@Serializable data object AccountPendingRoute

// Operator
@Serializable data object PanelRoute
@Serializable data object PendingCitizensRoute
@Serializable data object CategoriesRoute

/** First screen for each [AppMode]. */
fun startRouteFor(mode: AppMode): Any = when (mode) {
    AppMode.ANONYMOUS -> DashboardRoute
    AppMode.PENDING_CITIZEN -> AccountPendingRoute
    AppMode.CITIZEN -> MyIncidentsRoute
    AppMode.OPERATOR -> PanelRoute
}
