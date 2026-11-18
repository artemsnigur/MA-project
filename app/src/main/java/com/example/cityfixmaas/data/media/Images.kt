package com.example.cityfixmaas.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

// Photos are stored in Firestore as Base64-encoded JPEG strings (no Cloud Storage on the free plan).

/** Sizes (longer side, in pixels) used across the app. */
object ImageSize {
    const val PROFILE = 400
    const val PHOTO = 1280
    const val THUMBNAIL = 200
}

/**
 * Reads a photo, shrinks it so the longer side is at most [maxSize] pixels, compresses it as
 * JPEG and returns it as Base64 text. Call it off the main thread (Dispatchers.IO).
 */
fun uriToBase64Jpeg(context: Context, uri: Uri, maxSize: Int, quality: Int = 70): String {
    val original = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
        ?: throw IllegalArgumentException("Could not read the photo")

    val scale = maxSize.toFloat() / maxOf(original.width, original.height)
    val resized = if (scale < 1f) {
        Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
    } else {
        original
    }

    val bytes = ByteArrayOutputStream().use { out ->
        resized.compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

/**
 * Turns stored Base64 text back into an image.
 * In Compose: `base64ToBitmap(ref.thumbnailBase64)?.asImageBitmap()`, then `Image(bitmap = ...)`.
 */
fun base64ToBitmap(base64: String?): Bitmap? {
    if (base64.isNullOrEmpty()) return null
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
