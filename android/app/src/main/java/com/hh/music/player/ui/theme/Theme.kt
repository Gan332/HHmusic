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

/**
 * Material 3 Expressive 主题。
 *
 * 使用完整的 M3 色调体系（含 surfaceContainer 层级与 tertiary 色），
 * 并采用 Expressive 形态规范（更大的圆角：8/12/16/24/28dp）。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF3E6846),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBFF0C5),
    onPrimaryContainer = Color(0xFF00210A),
    inversePrimary = Color(0xFFA4D3AA),
    secondary = Color(0xFF526354),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E8D4),
    onSecondaryContainer = Color(0xFF101F13),
    tertiary = Color(0xFF3A6372),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBEEAFD),
    onTertiaryContainer = Color(0xFF001E29),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8FAF5),
    onBackground = Color(0xFF191C19),
    surface = Color(0xFFF8FAF5),
    onSurface = Color(0xFF191C19),
    surfaceVariant = Color(0xFFDEE5DC),
    onSurfaceVariant = Color(0xFF424940),
    outline = Color(0xFF727970),
    outlineVariant = Color(0xFFC2C9BF),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF3E6846),
    surfaceDim = Color(0xFFD8DBD4),
    surfaceBright = Color(0xFFF8FAF5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F5EE),
    surfaceContainer = Color(0xFFECF0E9),
    surfaceContainerHigh = Color(0xFFE6EAE3),
    surfaceContainerHighest = Color(0xFFE0E4DD)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA4D3AA),
    onPrimary = Color(0xFF0B3818),
    primaryContainer = Color(0xFF27502F),
    onPrimaryContainer = Color(0xFFBFF0C5),
    inversePrimary = Color(0xFF3E6846),
    secondary = Color(0xFFB9CCB8),
    onSecondary = Color(0xFF243426),
    secondaryContainer = Color(0xFF3B4B3D),
    onSecondaryContainer = Color(0xFFD5E8D4),
    tertiary = Color(0xFFA2CDDF),
    onTertiary = Color(0xFF063544),
    tertiaryContainer = Color(0xFF204C5B),
    onTertiaryContainer = Color(0xFFBEEAFD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101410),
    onBackground = Color(0xFFE1E3DE),
    surface = Color(0xFF101410),
    onSurface = Color(0xFFE1E3DE),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BF),
    outline = Color(0xFF8C9389),
    outlineVariant = Color(0xFF424940),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFFA4D3AA),
    surfaceDim = Color(0xFF101410),
    surfaceBright = Color(0xFF363A35),
    surfaceContainerLowest = Color(0xFF0B0F0B),
    surfaceContainerLow = Color(0xFF181C18),
    surfaceContainer = Color(0xFF1C201C),
    surfaceContainerHigh = Color(0xFF262B26),
    surfaceContainerHighest = Color(0xFF313631)
)

/** Expressive 形态：显著加大的圆角。 */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
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
        shapes = ExpressiveShapes,
        typography = MaterialTheme.typography,
        content = content
    )
}
