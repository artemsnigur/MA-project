package com.example.cityfixmaas.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.cityfixmaas.data.media.base64ToBitmap

/** Shows a Base64 JPEG from Firestore, or a grey box with an icon while there is none yet. */
@Composable
fun Base64Image(
    base64: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val bitmap = remember(base64) { base64ToBitmap(base64) }
    val shaped = modifier.clip(RoundedCornerShape(8.dp))
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription = contentDescription, contentScale = ContentScale.Crop, modifier = shaped)
    } else {
        Box(shaped.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Image, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
