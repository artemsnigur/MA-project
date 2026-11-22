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

// Videos go to Google Drive through the relay (server/relay), because Cloud Storage needs the
// paid Firebase plan. They are compressed first so the upload stays small on mobile data:
// at most 60 s, 480 px high, ~1 Mbit/s, which gives up to about 8 MB.

object VideoSize {
    const val MAX_DURATION_MS = 60_000L
    const val HEIGHT = 480
    const val BITRATE = 1_000_000

    /** Base64 adds a third, and the relay accepts requests of up to about 50 MB. */
    const val MAX_BYTES = 20_000_000
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
