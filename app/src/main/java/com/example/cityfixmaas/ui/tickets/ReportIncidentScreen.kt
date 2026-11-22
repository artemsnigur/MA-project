package com.example.cityfixmaas.ui.tickets

import androidx.compose.material.icons.filled.Videocam
import android.widget.Toast
import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.example.cityfixmaas.R
import com.example.cityfixmaas.data.AppConfig
import com.example.cityfixmaas.data.model.IncidentType
import com.example.cityfixmaas.data.model.LatLng
import com.example.cityfixmaas.data.model.TicketDraft
import com.example.cityfixmaas.data.repository.CategoryRepository
import com.example.cityfixmaas.data.repository.LocationProvider
import com.example.cityfixmaas.data.repository.TicketRepository
import com.example.cityfixmaas.di.appContainer
import com.example.cityfixmaas.ui.components.labelRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ReportUiState(
    val categoryId: String = "",
    val incidentType: IncidentType = IncidentType.DAMAGE,
    val description: String = "",
    val publicScope: Boolean = true,
    val location: LatLng? = null,
    val isCapturingLocation: Boolean = false,
    val photos: List<Uri> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
    /** The report joined an existing ticket for the same problem nearby. */
    val joinedExisting: Boolean = false,
)

class ReportIncidentViewModel(
    private val tickets: TicketRepository,
    categories: CategoryRepository,
    private val locationProvider: LocationProvider,
    roadDefect: Boolean,
) : ViewModel() {

    val categories = categories.observeCategories(activeOnly = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** From Impact Detection the form starts as a road damage report; the citizen can still change it. */
    private val _state = MutableStateFlow(
        if (roadDefect) ReportUiState(categoryId = AppConfig.ROAD_CATEGORY_ID, incidentType = IncidentType.DAMAGE) else ReportUiState(),
    )
    val state = _state.asStateFlow()

    init {
        captureLocation()
    }

    fun captureLocation() {
        viewModelScope.launch {
            _state.update { it.copy(isCapturingLocation = true) }
            val here = locationProvider.currentLocation() ?: AppConfig.ANONYMOUS_AREA_CENTER
            _state.update { it.copy(location = here, isCapturingLocation = false) }
        }
    }

    fun edit(change: (ReportUiState) -> ReportUiState) = _state.update { change(it).copy(error = null) }

    fun addPhotos(newPhotos: List<Uri>) {
        if (newPhotos.isEmpty()) return
        _state.update { current ->
            val combined = (current.photos + newPhotos).distinct()
            current.copy(photos = combined, error = null)
        }
    }

    fun removePhoto(uri: Uri) {
        _state.update { current ->
            current.copy(photos = current.photos.filter { it != uri })
        }
    }

    /** The repository checks the required fields (category, description, at least one photo). */
    fun send() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            val loc = s.location ?: locationProvider.currentLocation() ?: AppConfig.ANONYMOUS_AREA_CENTER
            val draft = TicketDraft(s.categoryId, s.incidentType, s.description, s.publicScope, loc, s.photos)
            val result = tickets.createTicket(draft)
            _state.update {
                it.copy(
                    isSaving = false,
                    done = result.isSuccess,
                    joinedExisting = result.getOrNull()?.joinedExisting == true,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    companion object {
        fun factory(roadDefect: Boolean) = viewModelFactory {
            initializer {
                val container = appContainer()
                ReportIncidentViewModel(container.tickets, container.categories, container.location, roadDefect)
            }
        }
    }
}

@Composable
fun ReportIncidentScreen(
    roadDefect: Boolean,
    onDone: () -> Unit,
    viewModel: ReportIncidentViewModel = viewModel(factory = ReportIncidentViewModel.factory(roadDefect)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Photo picker launcher (Gallery)
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        viewModel.addPhotos(uris)
    }

    // Camera capture
    var tempCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val tempCameraUri = tempCameraUriString?.let { Uri.parse(it) }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempCameraUri?.let { viewModel.addPhotos(listOf(it)) }
        }
    }
    val recordVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success) {
            tempCameraUri?.let { viewModel.addPhotos(listOf(it)) }
        }
    }

    // Location permissions
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.captureLocation()
        }
    }

    // The system camera app takes the photo, so the app itself needs no CAMERA permission.
    fun launchCamera() {
        val uri = createTempFileUri(context, ".jpg")
        tempCameraUriString = uri.toString()
        takePictureLauncher.launch(uri)
    }

    // Also the system camera app; the video is shortened and compressed before upload (VideoSize).
    fun launchVideoCamera() {
        val uri = createTempFileUri(context, ".mp4")
        tempCameraUriString = uri.toString()
        recordVideoLauncher.launch(uri)
    }

    LaunchedEffect(state.done) {
        if (state.done) {
            if (state.joinedExisting) Toast.makeText(context, R.string.report_joined_existing, Toast.LENGTH_LONG).show()
            onDone()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        // Category section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.report_category), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    FilterChip(
                        selected = state.categoryId == category.id,
                        onClick = { viewModel.edit { it.copy(categoryId = category.id) } },
                        label = { Text(category.name) },
                    )
                }
            }
        }

        // Incident Type section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.report_type), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IncidentType.entries.forEach { type ->
                    FilterChip(
                        selected = state.incidentType == type,
                        onClick = { viewModel.edit { it.copy(incidentType = type) } },
                        label = { Text(stringResource(type.labelRes())) },
                    )
                }
            }
        }

        // Description
        OutlinedTextField(
            value = state.description,
            onValueChange = { text -> viewModel.edit { it.copy(description = text) } },
            label = { Text(stringResource(R.string.report_description)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        // Location card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    val loc = state.location
                    val text = if (state.isCapturingLocation) {
                        stringResource(R.string.report_location_detecting)
                    } else if (loc != null) {
                        stringResource(R.string.report_location_captured, loc.latitude, loc.longitude)
                    } else {
                        stringResource(R.string.location_not_set)
                    }
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(
                    onClick = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                    enabled = !state.isCapturingLocation,
                ) {
                    if (state.isCapturingLocation) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.report_location_refresh))
                    }
                }
            }
        }

        // Public/Private Switch
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.report_public), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = state.publicScope,
                onCheckedChange = { checked -> viewModel.edit { it.copy(publicScope = checked) } },
            )
        }

        // Photos section (At least 1 photo required)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.detail_photos) + " *",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.photos.isNotEmpty()) {
                    Text(
                        stringResource(R.string.report_photos_selected, state.photos.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.report_pick_photos))
                }

                OutlinedButton(onClick = { launchCamera() }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.report_camera))
                }

                OutlinedButton(onClick = { launchVideoCamera() }) {
                    Icon(Icons.Default.Videocam, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.report_record_video))
                }
            }

            // Photo preview thumbnails
            if (state.photos.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.photos.forEach { uri ->
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        ) {
                            if (context.contentResolver.getType(uri)?.startsWith("video/") == true) {
                                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Videocam, contentDescription = stringResource(R.string.cd_video))
                                }
                            } else {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = stringResource(R.string.cd_incident_thumbnail),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            IconButton(
                                onClick = { viewModel.removePhoto(uri) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(48.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.report_remove_photo),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.report_error_photos),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Error message
        state.error?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Submit Button
        Button(
            onClick = viewModel::send,
            enabled = !state.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.report_send))
            }
        }
    }
    }
}

/** Creates a temporary file in cache for the camera app and wraps it with FileProvider. */
private fun createTempFileUri(context: Context, extension: String): Uri {
    val tempFile = File.createTempFile("ticket_media_", extension, context.cacheDir).apply {
        createNewFile()
        deleteOnExit()
    }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        tempFile,
    )
}
