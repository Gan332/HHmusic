package com.hh.music.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.ColorScheme
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

private val BlueLight = LightColors.copy(
    primary = Color(0xFF2E6FA3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD2E4FF),
    onPrimaryContainer = Color(0xFF001D34),
    inversePrimary = Color(0xFFA9C9F0),
    secondary = Color(0xFF52606F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E4F5),
    onSecondaryContainer = Color(0xFF0F1D29),
    tertiary = Color(0xFF00696D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9CF1F5),
    onTertiaryContainer = Color(0xFF002022),
    surfaceTint = Color(0xFF2E6FA3)
)

private val BlueDark = DarkColors.copy(
    primary = Color(0xFFA9C9F0),
    onPrimary = Color(0xFF0A3050),
    primaryContainer = Color(0xFF164A70),
    onPrimaryContainer = Color(0xFFD2E4FF),
    inversePrimary = Color(0xFF2E6FA3),
    secondary = Color(0xFFBAC8D9),
    onSecondary = Color(0xFF243240),
    secondaryContainer = Color(0xFF3A4857),
    onSecondaryContainer = Color(0xFFD6E4F5),
    tertiary = Color(0xFF80D4D9),
    onTertiary = Color(0xFF003739),
    tertiaryContainer = Color(0xFF005053),
    onTertiaryContainer = Color(0xFF9CF1F5),
    surfaceTint = Color(0xFFA9C9F0)
)

private val OrangeLight = LightColors.copy(
    primary = Color(0xFFB45E23),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC3),
    onPrimaryContainer = Color(0xFF3A1C00),
    inversePrimary = Color(0xFFFFB784),
    secondary = Color(0xFF745944),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC1),
    onSecondaryContainer = Color(0xFF2A1707),
    tertiary = Color(0xFF5B6291),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDEE1FF),
    onTertiaryContainer = Color(0xFF151B48),
    surfaceTint = Color(0xFFB45E23)
)

private val OrangeDark = DarkColors.copy(
    primary = Color(0xFFFFB784),
    onPrimary = Color(0xFF512400),
    primaryContainer = Color(0xFF713800),
    onPrimaryContainer = Color(0xFFFFDCC3),
    inversePrimary = Color(0xFFB45E23),
    secondary = Color(0xFFE6BE9F),
    onSecondary = Color(0xFF3E2A19),
    secondaryContainer = Color(0xFF57402E),
    onSecondaryContainer = Color(0xFFFFDCC1),
    tertiary = Color(0xFFBEC2F5),
    onTertiary = Color(0xFF2A2F62),
    tertiaryContainer = Color(0xFF42477A),
    onTertiaryContainer = Color(0xFFDEE1FF),
    surfaceTint = Color(0xFFFFB784)
)

private fun AppThemeColor.lightScheme(): ColorScheme = when (this) {
    AppThemeColor.GREEN -> LightColors
    AppThemeColor.BLUE -> BlueLight
    AppThemeColor.ORANGE -> OrangeLight
}

private fun AppThemeColor.darkScheme(): ColorScheme = when (this) {
    AppThemeColor.GREEN -> DarkColors
    AppThemeColor.BLUE -> BlueDark
    AppThemeColor.ORANGE -> OrangeDark
}

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
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    themeColor: AppThemeColor = AppThemeColor.GREEN,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> themeColor.darkScheme()
        else -> themeColor.lightScheme()
    }

    MaterialTheme(
        colorScheme = colors,
        shapes = ExpressiveShapes,
        typography = MaterialTheme.typography,
        content = content
    )
}
