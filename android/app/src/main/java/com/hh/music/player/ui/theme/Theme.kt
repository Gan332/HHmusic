package com.hh.music.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF3E6846),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBFF0C5),
    onPrimaryContainer = Color(0xFF00210A),
    secondary = Color(0xFF526354),
    secondaryContainer = Color(0xFFD5E8D4),
    background = Color(0xFFF8FAF5),
    onBackground = Color(0xFF191C19),
    surface = Color(0xFFF8FAF5),
    onSurface = Color(0xFF191C19),
    surfaceVariant = Color(0xFFDEE5DC),
    onSurfaceVariant = Color(0xFF424940),
    outline = Color(0xFF727970),
    outlineVariant = Color(0xFFC2C9BF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA4D3AA),
    onPrimary = Color(0xFF0B3818),
    primaryContainer = Color(0xFF27502F),
    onPrimaryContainer = Color(0xFFBFF0C5),
    secondary = Color(0xFFB9CCB8),
    secondaryContainer = Color(0xFF3B4B3D),
    background = Color(0xFF101410),
    onBackground = Color(0xFFE1E3DE),
    surface = Color(0xFF101410),
    onSurface = Color(0xFFE1E3DE),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BF),
    outline = Color(0xFF8C9389),
    outlineVariant = Color(0xFF424940)
)

private val KazumiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun HHMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        shapes = KazumiShapes,
        typography = MaterialTheme.typography,
        content = content
    )
}
