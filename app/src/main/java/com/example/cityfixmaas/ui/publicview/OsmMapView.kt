package com.example.cityfixmaas.ui.publicview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.cityfixmaas.data.AppConfig
import com.example.cityfixmaas.data.model.PublicTicket
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun OsmMapView(
    tickets: List<PublicTicket>,
    categoryNames: Map<String, String>,
    onMarkerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.0)
            controller.setCenter(
                GeoPoint(
                    AppConfig.ANONYMOUS_AREA_CENTER.latitude,
                    AppConfig.ANONYMOUS_AREA_CENTER.longitude,
                ),
            )
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlays.removeAll { it is Marker }

            tickets.forEach { ticket ->
                val marker = Marker(map).apply {
                    position = GeoPoint(ticket.approxLocation.latitude, ticket.approxLocation.longitude)
                    title = categoryNames[ticket.categoryId] ?: ticket.categoryId
                    snippet = "Lat: ${ticket.approxLocation.latitude}, Lon: ${ticket.approxLocation.longitude}"
                    setOnMarkerClickListener { _, _ ->
                        onMarkerClick(ticket.id)
                        true
                    }
                }
                map.overlays.add(marker)
            }
            map.invalidate()
        },
    )
}

@Composable
fun SingleIncidentMapView(
    latitude: Double,
    longitude: Double,
    title: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
            controller.setCenter(GeoPoint(latitude, longitude))
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlays.removeAll { it is Marker }
            val marker = Marker(map).apply {
                position = GeoPoint(latitude, longitude)
                this.title = title
            }
            map.overlays.add(marker)
            map.controller.setCenter(GeoPoint(latitude, longitude))
            map.invalidate()
        },
    )
}
