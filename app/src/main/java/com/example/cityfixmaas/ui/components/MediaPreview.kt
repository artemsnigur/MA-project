package com.example.cityfixmaas.ui.components

import androidx.compose.material3.CircularProgressIndicator
import java.net.URL
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.cityfixmaas.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A photo, or for a video its first frame with a play button that opens the player. */
@Composable
fun MediaPreview(imageBase64: String?, videoUrl: String?, modifier: Modifier = Modifier, contentDescription: String? = null) {
    var playing by rememberSaveable { mutableStateOf(false) }
    Box(if (videoUrl != null) modifier.clickable { playing = true } else modifier, contentAlignment = Alignment.Center) {
        Base64Image(imageBase64, Modifier.matchParentSize(), contentDescription)
        if (videoUrl != null) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.cd_play_video),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape),
            )
        }
    }
    if (playing && videoUrl != null) VideoDialog(videoUrl, onDismiss = { playing = false })
}

/** Plays a video from Google Drive: it is downloaded to a cache file, which the Android VideoView can open. */
@Composable
private fun VideoDialog(videoUrl: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val file by produceState<File?>(null, videoUrl) {
        value = withContext(Dispatchers.IO) {
            val cached = File(context.cacheDir, "play-${videoUrl.hashCode()}.mp4")
            if (!cached.exists()) {
                val part = File(context.cacheDir, "${cached.name}.part") // renamed only when complete
                runCatching { URL(videoUrl).openStream().use { input -> part.outputStream().use { input.copyTo(it) } } }
                    .onSuccess { part.renameTo(cached) }
            }
            cached.takeIf { it.exists() }
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        if (file == null) CircularProgressIndicator() // downloading
        file?.let { video ->
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                        setVideoPath(video.path)
                        setOnPreparedListener { start() }
                    }
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f),
            )
        }
    }
}
