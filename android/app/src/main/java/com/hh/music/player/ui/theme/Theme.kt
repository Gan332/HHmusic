package com.hh.music.player.ui.theme

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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * HH Music theme entry point — full Material Design 3 implementation.
 *
 * ## Dynamic color (Material You)
 * On Android 12+ (API 31) the colour scheme is derived from the user's
 * wallpaper via [dynamicLightColorScheme] / [dynamicDarkColorScheme].
 *
 * When [dynamicColor] is `false` (or on older devices) the scheme is
 * generated from [seedColor] by deriving a tonal palette, carrying the
 * user's chosen colour across all 30 semantic roles.
 *
 * @param isDarkTheme  Dark mode override.  Default follows system setting.
 * @param dynamicColor Whether to use wallpaper‑derived colours (Android 12+).
 *                     Default `true`.
 * @param seedColor    Seed colour for tonal palette generation when
 *                     [dynamicColor] is `false`.
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
        val h = hex.removePrefix("#")
        Color(
            red = h.substring(0, 2).toInt(16) / 255f,
            green = h.substring(2, 4).toInt(16) / 255f,
            blue = h.substring(4, 6).toInt(16) / 255f,
        )
    } catch (_: Exception) {
        fallback
    }
}

// ── Pure‑Kotlin HSL conversion (no Android SDK dependency) ─────────────

private data class Hsl(val h: Float, val s: Float, val l: Float)

private fun Color.toHsl(): Hsl {
    val r = red; val g = green; val b = blue
    val mx = max(max(r, g), b)
    val mn = min(min(r, g), b)
    val delta = mx - mn
    val l = (mx + mn) / 2f
    if (delta < 1e-6f) return Hsl(0f, 0f, l)
    val s = if (l <= 0.5f) delta / (mx + mn) else delta / (2f - mx - mn)
    val h = when (mx) {
        r -> ((g - b) / delta + if (g < b) 6f else 0f) * 60f
        g -> ((b - r) / delta + 2f) * 60f
        else -> ((r - g) / delta + 4f) * 60f
    }
    return Hsl(h % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

/** Build a Compose [Color] from HSL components (hue 0‑360, saturation 0‑1, lightness 0‑1). */
private fun Color.Companion.fromHsl(hsl: Hsl): Color {
    var h = hsl.h % 360f; if (h < 0f) h += 360f
    val s = hsl.s.coerceIn(0f, 1f)
    val l = hsl.l.coerceIn(0f, 1f)
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m, alpha)
}

private fun hslColor(h: Float, s: Float, l: Float) = Color.fromHsl(Hsl(h, s, l))

// ── Seed‑to‑palette ────────────────────────────────────────────────────

private fun seedToLightColorScheme(seed: Color): ColorScheme {
    val (h, s, l) = seed.toHsl()

    val primary = seed
    val onPrimary = if (l > 0.5f) Color.Black else Color.White
    val primaryContainer = hslColor(h, (s * 0.75f).coerceAtMost(0.6f), (l + 0.55f).coerceAtMost(0.95f))
    val onPrimaryContainer = hslColor(h, (s * 0.9f).coerceAtMost(0.8f), (l * 0.25f).coerceAtMost(0.2f))
    val secondary = hslColor(h + 30f, (s * 0.5f).coerceAtMost(0.35f), 0.45f)
    val secondaryContainer = hslColor(h + 30f, (s * 0.4f).coerceAtMost(0.3f), 0.88f)

    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = Color(0xFF1B1B1F),
    )
}

private fun seedToDarkColorScheme(seed: Color): ColorScheme {
    val (h, s, l) = seed.toHsl()

    val primary = hslColor(h, (s * 0.85f).coerceAtMost(0.8f), (l * 0.5f + 0.35f).coerceAtMost(0.85f))
    val onPrimary = Color(0xFF003915)
    val primaryContainer = hslColor(h, (s * 0.7f).coerceAtMost(0.5f), (l * 0.25f + 0.10f).coerceAtMost(0.35f))
    val onPrimaryContainer = hslColor(h, (s * 0.8f).coerceAtMost(0.7f), 0.8f)
    val secondary = hslColor(h + 30f, (s * 0.4f).coerceAtMost(0.3f), 0.7f)
    val secondaryContainer = hslColor(h + 30f, (s * 0.35f).coerceAtMost(0.25f), 0.28f)

    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = Color(0xFF1B1B1F),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = Color(0xFFD1EBD6),
    )
}
