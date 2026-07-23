package com.hh.music.player.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// NetEase-inspired dark palette with a green accent.
private val Green500 = Color(0xFF1DB954)
private val Green700 = Color(0xFF168A3D)
private val DarkBg = Color(0xFF0F0F0F)
private val DarkSurface = Color(0xFF1C1C1C)
private val DarkSurfaceVariant = Color(0xFF2A2A2A)
private val LightText = Color(0xFFFFFFFF)
private val MutedText = Color(0xFFB3B3B3)

private val HHColorScheme = darkColorScheme(
    primary = Green500,
    onPrimary = Color.Black,
    primaryContainer = Green700,
    secondary = Green500,
    background = DarkBg,
    onBackground = LightText,
    surface = DarkSurface,
    onSurface = LightText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = MutedText,
)

@Composable
fun HHMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HHColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
