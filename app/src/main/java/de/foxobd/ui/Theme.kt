package de.foxobd.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val FoxOrange = Color(0xFFFF7A2F)
val WarnRed = Color(0xFFE5484D)
val OkGreen = Color(0xFF30A46C)

private val Dark = darkColorScheme(
    primary = FoxOrange,
    onPrimary = Color(0xFF1A0E06),
    secondary = Color(0xFFFFB68A),
    background = Color(0xFF121212),
    surface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFF262626),
    error = WarnRed,
)

private val Light = lightColorScheme(
    primary = Color(0xFFD9560F),
    secondary = Color(0xFF8C4A22),
    error = WarnRed,
)

@Composable
fun FoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
