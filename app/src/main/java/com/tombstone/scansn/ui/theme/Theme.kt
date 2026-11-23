package com.tombstone.scansn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF146B62),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F3EC),
    onPrimaryContainer = Color(0xFF123C3A),
    secondary = Color(0xFF4D625F),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EFEE),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF172321),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FE3CF),
    primaryContainer = Color(0xFF164F49),
    onPrimaryContainer = Color(0xFFD5F3EC),
    background = Color(0xFF111817),
    surface = Color(0xFF192220),
)

@Composable
fun ScanSNTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
