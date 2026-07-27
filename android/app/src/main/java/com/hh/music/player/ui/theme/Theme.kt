package com.hh.music.player.ui.theme

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
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
 * generated from [seedColor] by deriving a tonal palette, carrying the
 * user's chosen colour across all 30 semantic roles.
 *
 * ## Sub‑systems
 * - **Color** — all 30 M3 color roles through dynamic API or seed‑derived palette.
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
@Composable
fun HHMusicTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Color = Color(0xFF1DB954),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDarkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        isDarkTheme -> seedToDarkColorScheme(seedColor)
        else -> seedToLightColorScheme(seedColor)
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

// ── Seed‑to‑palette helpers ────────────────────────────────────────────
// Derive a full M3‑compatible ColorScheme from a single seed colour.
// We use simple HSL manipulation to produce a tonal family that looks
// reasonable on light and dark backgrounds.

private fun seedToLightColorScheme(seed: Color): ColorScheme {
    val hsl = FloatArray(3)
    AndroidColor.colorToHSL(seed.toArgb(), hsl)
    val h = hsl[0]; val s = hsl[1]; val l = hsl[2]

    val primary = seed
    val onPrimary = if (l > 0.5f) Color(0xFF000000) else Color(0xFFFFFFFF)

    val lighter = hslColor(h, (s * 0.75f).coerceAtMost(0.6f), (l + 0.55f).coerceAtMost(0.95f))
    val onContainer = hslColor(h, (s * 0.9f).coerceAtMost(0.8f), (l * 0.25f).coerceAtMost(0.2f))
    val secondary = hslColor(h + 30f, (s * 0.5f).coerceAtMost(0.35f), 0.45f)
    val onSecondary = Color(0xFFFFFFFF)
    val secondaryContainer = hslColor(h + 30f, (s * 0.4f).coerceAtMost(0.3f), 0.88f)
    val tertiary = hslColor(h + 60f, (s * 0.4f).coerceAtMost(0.35f), 0.40f)

    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = lighter,
        onPrimaryContainer = onContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = Color(0xFF1B1B1F),
    )
}

private fun seedToDarkColorScheme(seed: Color): ColorScheme {
    val hsl = FloatArray(3)
    AndroidColor.colorToHSL(seed.toArgb(), hsl)
    val h = hsl[0]; val s = hsl[1]; val l = hsl[2]

    val primary = hslColor(h, (s * 0.85f).coerceAtMost(0.8f), (l * 0.5f + 0.35f).coerceAtMost(0.85f))
    val onPrimary = Color(0xFF003915)
    val container = hslColor(h, (s * 0.7f).coerceAtMost(0.5f), (l * 0.25f + 0.10f).coerceAtMost(0.35f))
    val onContainer = hslColor(h, (s * 0.8f).coerceAtMost(0.7f), 0.8f)
    val secondary = hslColor(h + 30f, (s * 0.4f).coerceAtMost(0.3f), 0.7f)
    val onSecondary = Color(0xFF1B1B1F)
    val secondaryContainer = hslColor(h + 30f, (s * 0.35f).coerceAtMost(0.25f), 0.28f)

    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = Color(0xFFD1EBD6),
    )
}

/** Build a [Color] from HSV/HSL‑style components (same units as [AndroidColor.HSLToColor]). */
private fun hslColor(hue: Float, saturation: Float, lightness: Float): Color {
    var h = hue % 360f
    if (h < 0f) h += 360f
    return Color(AndroidColor.HSLToColor(floatArrayOf(h, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))))
}
