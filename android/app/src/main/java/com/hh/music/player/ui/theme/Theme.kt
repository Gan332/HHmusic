package com.hh.music.player.ui.theme

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Brightness
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * HH Music theme entry point — full Material Design 3 implementation.
 *
 * ## Dynamic color (Material You)
 * On Android 12+ (API 31) the colour scheme is derived from the user's
 * wallpaper via [dynamicLightColorScheme] / [dynamicDarkColorScheme],
 * giving every device a personalised look.
 *
 * When [dynamicColor] is `false` (or on older devices) the scheme is
 * generated from [seedColor] using M3 tonal palette algorithms, so the
 * user's chosen colour is carried across all 30 semantic roles.
 *
 * ## Sub‑systems
 * - **Color** — all 30 M3 color roles through dynamic API or [ColorScheme.fromSeedColor].
 * - **Typography** — the full 15‑style M3 type scale ([HHMusicTypography]).
 * - **Shapes** — the 5‑tier M3 shape scale ([HHShapes]).
 *
 * @param isDarkTheme  Dark mode override.  Default follows system setting.
 * @param dynamicColor Whether to use wallpaper‑derived colours (Android 12+).
 *                     Default `true`.
 * @param seedColor    Seed color for tonal palette generation when
 *                     [dynamicColor] is `false`.  Ignored when dynamic is active.
 *                     Default: NetEase green (#1DB954).
 * @param content      The composable content tree.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HHMusicTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Color = Color(0xFF1DB954),
    content: @Composable () -> Unit,
) {
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDarkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        else -> ColorScheme.fromSeedColor(
            seedColor = seedColor,
            brightness = if (isDarkTheme) Brightness.Dark else Brightness.Light,
        )
    }

    val shapes = HHShapes.default()

    HHThemeProvider(
        colors = if (isDarkTheme) darkHHColors() else lightHHColors(),
        shapes = shapes,
        dimens = HHDimens.default(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HHMusicTypography,
            shapes = shapes.toMaterialShapes(),
            content = content,
        )
    }
}

/**
 * Parse a hex color string (e.g. "#1DB954") to Compose [Color].
 * Returns [fallback] on parse failure.
 */
fun parseHexColor(hex: String?, fallback: Color = Color(0xFF1DB954)): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        Color(AndroidColor.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}
