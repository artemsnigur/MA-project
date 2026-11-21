package com.example.cityfixmaas.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.cityfixmaas.data.model.LatLng
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * OpenStreetMap (osmdroid, free, no API key) with one marker at [location].
 * Operators and authors see the exact place (zoom 16); public details pass a smaller [zoom]
 * so the approximate place is shown at a zoomed-out scale.
 */
@Composable
fun IncidentMap(location: LatLng, zoom: Double, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember {
        // OSM tile servers block generic app ids (e.g. "com.example..."); they need a descriptive one.
        Configuration.getInstance().userAgentValue = "CityFix-AMOV-student-project/1.0"
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
        }
    }
    // MapView is a classic Android View: tell it when the screen goes away.
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose { mapView.onDetach() }
    }
    AndroidView(
        factory = { mapView },
        modifier = modifier.clipToBounds(), // a classic View would otherwise draw over its neighbours
        update = { map ->
            val point = GeoPoint(location.latitude, location.longitude)
            map.controller.setZoom(zoom)
            map.controller.setCenter(point)
            map.overlays.clear()
            map.overlays.add(Marker(map).apply { position = point })
            map.invalidate()
        },
    )
}
