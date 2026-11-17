package com.example.cityfixmaas.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

/**
 * Photos are stored in Firestore as Base64-encoded JPEG strings (no Cloud Storage on the free plan).
 * In Compose: `base64ToBitmap(ref.thumbnailBase64)?.asImageBitmap()`, then `Image(bitmap = ...)`.
 * Decode off the main thread for full-size images (e.g. in the ViewModel or with remember + LaunchedEffect).
 */
fun base64ToBitmap(base64: String?): Bitmap? {
    if (base64.isNullOrEmpty()) return null
    return runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
