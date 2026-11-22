package com.example.cityfixmaas.ui.components

import android.util.Base64
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
fun MediaPreview(imageBase64: String?, videoBase64: String?, modifier: Modifier = Modifier, contentDescription: String? = null) {
    var playing by rememberSaveable { mutableStateOf(false) }
    Box(if (videoBase64 != null) modifier.clickable { playing = true } else modifier, contentAlignment = Alignment.Center) {
        Base64Image(imageBase64, Modifier.matchParentSize(), contentDescription)
        if (videoBase64 != null) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.cd_play_video),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape),
            )
        }
    }
    if (playing && videoBase64 != null) VideoDialog(videoBase64, onDismiss = { playing = false })
}

/** Plays a Base64 MP4: it is written to a cache file, which the Android VideoView can open. */
@Composable
private fun VideoDialog(videoBase64: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val file by produceState<File?>(null, videoBase64) {
        value = withContext(Dispatchers.IO) {
            File(context.cacheDir, "play-${videoBase64.hashCode()}.mp4").apply { writeBytes(Base64.decode(videoBase64, Base64.DEFAULT)) }
        }
    }
    Dialog(onDismissRequest = onDismiss) {
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
