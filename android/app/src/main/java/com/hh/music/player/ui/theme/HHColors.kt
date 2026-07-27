package com.hh.music.player.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.graphics.Color

/**
 * HH Music color system — full Material Design 3 color roles.
 *
 * Maps the NetEase‑inspired green brand colour (#1DB954) across all M3 semantic
 * roles for both light and dark palettes.  Each role follows the M3 naming so
 * callers (including [MaterialTheme]) always use the correct contrast pair.
 *
 * @see <a href="https://m3.material.io/styles/color/roles">M3 color roles</a>
 */
@Stable
class HHColors(
    // ── Primary ──────────────────────────────────────────────────────────
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    // ── Secondary ────────────────────────────────────────────────────────
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    // ── Tertiary ─────────────────────────────────────────────────────────
    tertiary: Color,
    onTertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
    // ── Error ────────────────────────────────────────────────────────────
    error: Color,
    onError: Color,
    errorContainer: Color,
    onErrorContainer: Color,
    // ── Background ───────────────────────────────────────────────────────
    background: Color,
    onBackground: Color,
    // ── Surface ──────────────────────────────────────────────────────────
    surface: Color,
    onSurface: Color,
    surfaceVariant: Color,
    onSurfaceVariant: Color,
    // ── Outline ─────────────────────────────────────────────────────────
    outline: Color,
    outlineVariant: Color,
    // ── Inverse ──────────────────────────────────────────────────────────
    inverseSurface: Color,
    inverseOnSurface: Color,
    inversePrimary: Color,
    // ── Misc ─────────────────────────────────────────────────────────────
    surfaceTint: Color,
    scrim: Color,
) {
    // ── Delegated properties (Compose‑observable, structural equality) ──
    val primary by mutableStateOf(primary, structuralEqualityPolicy())
    val onPrimary by mutableStateOf(onPrimary, structuralEqualityPolicy())
    val primaryContainer by mutableStateOf(primaryContainer, structuralEqualityPolicy())
    val onPrimaryContainer by mutableStateOf(onPrimaryContainer, structuralEqualityPolicy())

    val secondary by mutableStateOf(secondary, structuralEqualityPolicy())
    val onSecondary by mutableStateOf(onSecondary, structuralEqualityPolicy())
    val secondaryContainer by mutableStateOf(secondaryContainer, structuralEqualityPolicy())
    val onSecondaryContainer by mutableStateOf(onSecondaryContainer, structuralEqualityPolicy())

    val tertiary by mutableStateOf(tertiary, structuralEqualityPolicy())
    val onTertiary by mutableStateOf(onTertiary, structuralEqualityPolicy())
    val tertiaryContainer by mutableStateOf(tertiaryContainer, structuralEqualityPolicy())
    val onTertiaryContainer by mutableStateOf(onTertiaryContainer, structuralEqualityPolicy())

    val error by mutableStateOf(error, structuralEqualityPolicy())
    val onError by mutableStateOf(onError, structuralEqualityPolicy())
    val errorContainer by mutableStateOf(errorContainer, structuralEqualityPolicy())
    val onErrorContainer by mutableStateOf(onErrorContainer, structuralEqualityPolicy())

    val background by mutableStateOf(background, structuralEqualityPolicy())
    val onBackground by mutableStateOf(onBackground, structuralEqualityPolicy())

    val surface by mutableStateOf(surface, structuralEqualityPolicy())
    val onSurface by mutableStateOf(onSurface, structuralEqualityPolicy())
    val surfaceVariant by mutableStateOf(surfaceVariant, structuralEqualityPolicy())
    val onSurfaceVariant by mutableStateOf(onSurfaceVariant, structuralEqualityPolicy())

    val outline by mutableStateOf(outline, structuralEqualityPolicy())
    val outlineVariant by mutableStateOf(outlineVariant, structuralEqualityPolicy())

    val inverseSurface by mutableStateOf(inverseSurface, structuralEqualityPolicy())
    val inverseOnSurface by mutableStateOf(inverseOnSurface, structuralEqualityPolicy())
    val inversePrimary by mutableStateOf(inversePrimary, structuralEqualityPolicy())

    val surfaceTint by mutableStateOf(surfaceTint, structuralEqualityPolicy())
    val scrim by mutableStateOf(scrim, structuralEqualityPolicy())

    /** Build a [MaterialTheme]‑style light color scheme from this instance. */
    fun toLightColorScheme() = lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        inversePrimary = inversePrimary,
        surfaceTint = surfaceTint,
        scrim = scrim,
    )

    /** Build a [MaterialTheme]‑style dark color scheme from this instance. */
    fun toDarkColorScheme() = darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        inversePrimary = inversePrimary,
        surfaceTint = surfaceTint,
        scrim = scrim,
    )

    /** Backward‑compat aliases (old SaltUI names). */
    val highlight: Color get() = primary
    val onHighlight: Color get() = onPrimary
    val text: Color get() = onSurface
    val subText: Color get() = onSurfaceVariant
    val subBackground: Color get() = surface
    val popup: Color get() = surface
    val stroke: Color get() = outline
}

// ── Light palette (NetEase‑inspired green) ──────────────────────────────

private val LightPrimary = Color(0xFF1DB954)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFA7F5BA)
private val LightOnPrimaryContainer = Color(0xFF00210D)

private val LightSecondary = Color(0xFF4E6555)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFD1EBD6)
private val LightOnSecondaryContainer = Color(0xFF0C1F15)

private val LightTertiary = Color(0xFF3C6472)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFBFE9F9)
private val LightOnTertiaryContainer = Color(0xFF001F29)

private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)

private val LightBackground = Color(0xFFF5F5F5)
private val LightOnBackground = Color(0xFF1E1715)

private val LightSurface = Color(0xFFFFFFFF)
private val LightOnSurface = Color(0xFF1E1715)
private val LightSurfaceVariant = Color(0xFFE8E8E8)
private val LightOnSurfaceVariant = Color(0xFF6A6A6A)

private val LightOutline = Color(0xFFC4C4C4)
private val LightOutlineVariant = Color(0xFFDEDEDE)

private val LightInverseSurface = Color(0xFF333333)
private val LightInverseOnSurface = Color(0xFFF0F0F0)
private val LightInversePrimary = Color(0xFF8BE89E)

private val LightSurfaceTint = Color(0xFF1DB954)
private val LightScrim = Color(0x40000000)

// ── Dark palette (rich, high contrast) ─────────────────────────────────

private val DarkPrimary = Color(0xFF6EDB7A)
private val DarkOnPrimary = Color(0xFF003915)
private val DarkPrimaryContainer = Color(0xFF005224)
private val DarkOnPrimaryContainer = Color(0xFFA7F5BA)

private val DarkSecondary = Color(0xFFB6CFBA)
private val DarkOnSecondary = Color(0xFF22352A)
private val DarkSecondaryContainer = Color(0xFF384C3E)
private val DarkOnSecondaryContainer = Color(0xFFD1EBD6)

private val DarkTertiary = Color(0xFFA3CDDD)
private val DarkOnTertiary = Color(0xFF063543)
private val DarkTertiaryContainer = Color(0xFF234C5A)
private val DarkOnTertiaryContainer = Color(0xFFBFE9F9)

private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)

private val DarkBackground = Color(0xFF0C0C0C)
private val DarkOnBackground = Color(0xFFEBEEF1)

private val DarkSurface = Color(0xFF191919)
private val DarkOnSurface = Color(0xFFEBEEF1)
private val DarkSurfaceVariant = Color(0xFF2C2C2C)
private val DarkOnSurfaceVariant = Color(0xFFB3B3B3)

private val DarkOutline = Color(0xFF555555)
private val DarkOutlineVariant = Color(0xFF3C3C3C)

private val DarkInverseSurface = Color(0xFFEBEEF1)
private val DarkInverseOnSurface = Color(0xFF191919)
private val DarkInversePrimary = Color(0xFF1D823E)

private val DarkSurfaceTint = Color(0xFF6EDB7A)
private val DarkScrim = Color(0x80000000)

// ── Public factory functions ───────────────────────────────────────────

fun lightHHColors(
    primary: Color = LightPrimary,
    onPrimary: Color = LightOnPrimary,
    primaryContainer: Color = LightPrimaryContainer,
    onPrimaryContainer: Color = LightOnPrimaryContainer,
    secondary: Color = LightSecondary,
    onSecondary: Color = LightOnSecondary,
    secondaryContainer: Color = LightSecondaryContainer,
    onSecondaryContainer: Color = LightOnSecondaryContainer,
    tertiary: Color = LightTertiary,
    onTertiary: Color = LightOnTertiary,
    tertiaryContainer: Color = LightTertiaryContainer,
    onTertiaryContainer: Color = LightOnTertiaryContainer,
    error: Color = LightError,
    onError: Color = LightOnError,
    errorContainer: Color = LightErrorContainer,
    onErrorContainer: Color = LightOnErrorContainer,
    background: Color = LightBackground,
    onBackground: Color = LightOnBackground,
    surface: Color = LightSurface,
    onSurface: Color = LightOnSurface,
    surfaceVariant: Color = LightSurfaceVariant,
    onSurfaceVariant: Color = LightOnSurfaceVariant,
    outline: Color = LightOutline,
    outlineVariant: Color = LightOutlineVariant,
    inverseSurface: Color = LightInverseSurface,
    inverseOnSurface: Color = LightInverseOnSurface,
    inversePrimary: Color = LightInversePrimary,
    surfaceTint: Color = LightSurfaceTint,
    scrim: Color = LightScrim,
): HHColors = HHColors(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
    outlineVariant = outlineVariant,
    inverseSurface = inverseSurface,
    inverseOnSurface = inverseOnSurface,
    inversePrimary = inversePrimary,
    surfaceTint = surfaceTint,
    scrim = scrim,
)

fun darkHHColors(
    primary: Color = DarkPrimary,
    onPrimary: Color = DarkOnPrimary,
    primaryContainer: Color = DarkPrimaryContainer,
    onPrimaryContainer: Color = DarkOnPrimaryContainer,
    secondary: Color = DarkSecondary,
    onSecondary: Color = DarkOnSecondary,
    secondaryContainer: Color = DarkSecondaryContainer,
    onSecondaryContainer: Color = DarkOnSecondaryContainer,
    tertiary: Color = DarkTertiary,
    onTertiary: Color = DarkOnTertiary,
    tertiaryContainer: Color = DarkTertiaryContainer,
    onTertiaryContainer: Color = DarkOnTertiaryContainer,
    error: Color = DarkError,
    onError: Color = DarkOnError,
    errorContainer: Color = DarkErrorContainer,
    onErrorContainer: Color = DarkOnErrorContainer,
    background: Color = DarkBackground,
    onBackground: Color = DarkOnBackground,
    surface: Color = DarkSurface,
    onSurface: Color = DarkOnSurface,
    surfaceVariant: Color = DarkSurfaceVariant,
    onSurfaceVariant: Color = DarkOnSurfaceVariant,
    outline: Color = DarkOutline,
    outlineVariant: Color = DarkOutlineVariant,
    inverseSurface: Color = DarkInverseSurface,
    inverseOnSurface: Color = DarkInverseOnSurface,
    inversePrimary: Color = DarkInversePrimary,
    surfaceTint: Color = DarkSurfaceTint,
    scrim: Color = DarkScrim,
): HHColors = HHColors(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
    outlineVariant = outlineVariant,
    inverseSurface = inverseSurface,
    inverseOnSurface = inverseOnSurface,
    inversePrimary = inversePrimary,
    surfaceTint = surfaceTint,
    scrim = scrim,
)
