package com.example.cityfixmaas.ui.components

import androidx.annotation.StringRes
import com.example.cityfixmaas.R
import com.example.cityfixmaas.data.model.IncidentType
import com.example.cityfixmaas.data.model.LifecycleState
import java.text.DateFormat
import java.util.Date

// Translated names for the enums shown on screen.

@StringRes
fun LifecycleState.labelRes(): Int = when (this) {
    LifecycleState.OPEN -> R.string.state_open
    LifecycleState.INFORMATION_REQUEST -> R.string.state_information_request
    LifecycleState.IN_RESOLUTION -> R.string.state_in_resolution
    LifecycleState.RESOLVED -> R.string.state_resolved
    LifecycleState.CLOSED -> R.string.state_closed
}

@StringRes
fun IncidentType.labelRes(): Int = when (this) {
    IncidentType.DAMAGE -> R.string.type_damage
    IncidentType.BREAKDOWN -> R.string.type_breakdown
    IncidentType.DESTRUCTION -> R.string.type_destruction
    IncidentType.THEFT -> R.string.type_theft
}

/** Date and time in the phone's format, e.g. "19/11/26, 14:05". */
fun formatTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
