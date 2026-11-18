package com.example.cityfixmaas.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns a picked or captured photo into a Base64 JPEG small enough for a Firestore document.
 * Accepts content:// URIs and file:// URIs (B2 copies picked photos into filesDir first).
 */
class ImageCompressor(private val context: Context) {

    /**
     * Downscales so the longer side is at most [maxDimension], applies the EXIF rotation and
     * encodes as JPEG, lowering the quality until the Base64 string fits [maxBase64Length].
     */
    suspend fun toBase64Jpeg(
        uri: Uri,
        maxDimension: Int,
        maxBase64Length: Int = Int.MAX_VALUE,
        quality: Int = DEFAULT_QUALITY,
    ): String = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(uri, maxDimension)
        try {
            var q = quality
            var base64 = encode(bitmap, q)
            while (base64.length > maxBase64Length) {
                require(q > MIN_QUALITY) { "Photo is too large" }
                q = max(MIN_QUALITY, q - QUALITY_STEP)
                base64 = encode(bitmap, q)
            }
            base64
        } finally {
            bitmap.recycle()
        }
    }

    private fun encode(bitmap: Bitmap, quality: Int): String {
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decodeScaled(uri: Uri, maxDimension: Int): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Could not read the photo" }

        // Cheap power-of-two subsampling first, exact scaling afterwards.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val decoded = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("Could not read the photo")

        val longer = max(decoded.width, decoded.height)
        val scaled = if (longer > maxDimension) {
            val factor = maxDimension.toFloat() / longer
            Bitmap.createScaledBitmap(
                decoded, (decoded.width * factor).roundToInt(), (decoded.height * factor).roundToInt(), true,
            ).also { if (it !== decoded) decoded.recycle() }
        } else decoded

        val degrees = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        if (degrees == 0) return scaled
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
            .also { if (it !== scaled) scaled.recycle() }
    }

    companion object {
        const val DEFAULT_QUALITY = 70
        private const val MIN_QUALITY = 30
        private const val QUALITY_STEP = 10

        /** Profile photo: shown small, stored inside users/{uid}. */
        const val PROFILE_MAX_DIMENSION = 512
        const val PROFILE_MAX_BASE64 = 150_000

        /** Ticket photo: one document per photo in tickets/{id}/media (1 MiB limit). */
        const val PHOTO_MAX_DIMENSION = 1280
        const val PHOTO_MAX_BASE64 = 700_000

        /** Thumbnail kept inside the ticket and publicTickets documents. */
        const val THUMBNAIL_MAX_DIMENSION = 200
        const val THUMBNAIL_MAX_BASE64 = 30_000
    }
}
