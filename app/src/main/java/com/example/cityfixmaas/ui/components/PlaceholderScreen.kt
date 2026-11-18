package com.example.cityfixmaas.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** TEMP: stands in for a screen until its feature task (F1–F3) replaces it. */
@Composable
fun PlaceholderScreen(@StringRes titleRes: Int, modifier: Modifier = Modifier, detail: String? = null) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(listOfNotNull(stringResource(titleRes), detail).joinToString(" "))
    }
}
