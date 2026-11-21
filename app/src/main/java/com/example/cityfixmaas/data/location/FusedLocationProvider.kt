package com.example.cityfixmaas.data.location

import android.annotation.SuppressLint
import android.content.Context
import com.example.cityfixmaas.data.AppConfig
import com.example.cityfixmaas.data.distanceTo
import com.example.cityfixmaas.data.model.LatLng
import com.example.cityfixmaas.data.repository.LocationProvider
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

/** The phone's current position from Google Play services. The screens ask for the permission. */
class FusedLocationProvider(context: Context) : LocationProvider {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    /** Null without permission or without a fix. */
    @SuppressLint("MissingPermission") // a missing permission throws SecurityException, caught below
    override suspend fun currentLocation(): LatLng? = runCatching {
        val location = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        location?.let { LatLng(it.latitude, it.longitude) }
    }.getOrNull()

    override fun isWithinAnonymousArea(location: LatLng): Boolean =
        location.distanceTo(AppConfig.ANONYMOUS_AREA_CENTER) <= AppConfig.ANONYMOUS_RADIUS_METERS
}
