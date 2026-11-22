package com.example.cityfixmaas.data.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Videos are stored like photos: Base64 in one Firestore document (1 MiB limit, no Cloud
// Storage on the free plan). So they are short and small: at most 8 s, 240 px high, ~250 kbit/s,
// no sound, which gives about 250 KB.

object VideoSize {
    const val MAX_DURATION_MS = 8_000L
    const val HEIGHT = 240
    const val BITRATE = 250_000

    /** Base64 adds a third; with the first frame this stays below the 1 MiB document limit. */
    const val MAX_BYTES = 600_000
}

/** Shortens and compresses [input] into [output] (H.264 MP4) with Media3 Transformer. */
@OptIn(UnstableApi::class) // Transformer is stable in practice but still marked "unstable" by Media3
suspend fun compressVideo(context: Context, input: File, output: File) = withContext(Dispatchers.Main) {
    // Transformer must be used from a thread with a Looper, so the main thread; the work itself runs in the background.
    suspendCancellableCoroutine { continuation ->
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(VideoSize.BITRATE).build())
                    .build(),
            )
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) = continuation.resume(Unit)
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) =
                    continuation.resumeWithException(exportException)
            })
            .build()
        val item = MediaItem.Builder()
            .setUri(Uri.fromFile(input))
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(VideoSize.MAX_DURATION_MS).build())
            .build()
        val edited = EditedMediaItem.Builder(item)
            .setRemoveAudio(true)
            .setEffects(Effects(listOf(), listOf(Presentation.createForHeight(VideoSize.HEIGHT))))
            .build()
        transformer.start(edited, output.path)
        continuation.invokeOnCancellation { transformer.cancel() }
    }
}

/** The first frame, used as the video's picture and thumbnail. */
fun videoPoster(file: File): Bitmap {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.path)
        return retriever.getFrameAtTime(0) ?: error("Could not read the video")
    } finally {
        retriever.release()
    }
}
