package com.hh.music.player.ui.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.graphics.Color

/**
 * HH Music color system, inspired by SaltUI's clean semantic color model.
 *
 * Provides a minimal set of semantic color roles that map to a music player's
 * visual needs: a strong highlight/accent, clear text hierarchy, layered
 * backgrounds, and crisp strokes for separation.
 */
@Stable
class HHColors(
    /** Primary accent — green (NetEase inspired). */
    highlight: Color,
    /** Text on highlight backgrounds. */
    onHighlight: Color,
    /** Primary text (high-emphasis). */
    text: Color,
    /** Secondary text (medium-emphasis). */
    subText: Color,
    /** Main screen background. */
    background: Color,
    /** Surface / card background, layered above [background]. */
    subBackground: Color,
    /** Popup / dialog / overlay background. */
    popup: Color,
    /** Dividers, borders, strokes. */
    stroke: Color,
    /** Elevated surface tint (e.g. bottom bar, top app bar). */
    surfaceTint: Color,
    /** Semi-transparent scrim for overlays. */
    scrim: Color,
) {
    val highlight by mutableStateOf(highlight, structuralEqualityPolicy())
    val onHighlight by mutableStateOf(onHighlight, structuralEqualityPolicy())
    val text by mutableStateOf(text, structuralEqualityPolicy())
    val subText by mutableStateOf(subText, structuralEqualityPolicy())
    val background by mutableStateOf(background, structuralEqualityPolicy())
    val subBackground by mutableStateOf(subBackground, structuralEqualityPolicy())
    val popup by mutableStateOf(popup, structuralEqualityPolicy())
    val stroke by mutableStateOf(stroke, structuralEqualityPolicy())
    val surfaceTint by mutableStateOf(surfaceTint, structuralEqualityPolicy())
    val scrim by mutableStateOf(scrim, structuralEqualityPolicy())

    fun copy(
        highlight: Color = this.highlight,
        onHighlight: Color = this.onHighlight,
        text: Color = this.text,
        subText: Color = this.subText,
        background: Color = this.background,
        subBackground: Color = this.subBackground,
        popup: Color = this.popup,
        stroke: Color = this.stroke,
        surfaceTint: Color = this.surfaceTint,
        scrim: Color = this.scrim,
    ): HHColors = HHColors(
        highlight = highlight,
        onHighlight = onHighlight,
        text = text,
        subText = subText,
        background = background,
        subBackground = subBackground,
        popup = popup,
        stroke = stroke,
        surfaceTint = surfaceTint,
        scrim = scrim,
    )
}

/**
 * Holds both light and dark [HHColors] for dynamic theme switching.
 */
@Stable
data class HHDynamicColors(
    val light: HHColors,
    val dark: HHColors,
) {
    companion object {
        fun default(): HHDynamicColors = HHDynamicColors(
            light = lightHHColors(),
            dark = darkHHColors(),
        )
    }
}

// ── Light palette (NetEase-inspired green accent) ──────────────────────────

private val LightHighlight = Color(0xFF1DB954)       // NetEase green
private val LightOnHighlight = Color(0xFFFFFFFF)
private val LightText = Color(0xFF1E1715)
private val LightSubText = Color(0xFF8C8C8C)
private val LightBackground = Color(0xFFF5F5F5)
private val LightSubBackground = Color(0xFFFFFFFF)
private val LightPopup = Color(0xFFFFFFFF)
private val LightStroke = LightSubText.copy(alpha = 0.15f)
private val LightSurfaceTint = Color(0xFFE8E8E8)
private val LightScrim = Color(0x40000000)

// ── Dark palette (rich, high contrast) ─────────────────────────────────────

private val DarkHighlight = Color(0xFF1DB954)         // same green pops on dark
private val DarkOnHighlight = Color(0xFFFFFFFF)
private val DarkText = Color(0xFFEBEEF1)
private val DarkSubText = Color(0xBFE1E6EB)
private val DarkBackground = Color(0xFF0C0C0C)
private val DarkSubBackground = Color(0xFF191919)
private val DarkPopup = Color(0xFF222222)
private val DarkStroke = DarkSubText.copy(alpha = 0.1f)
private val DarkSurfaceTint = Color(0xFF1C1C1C)
private val DarkScrim = Color(0x80000000)

fun lightHHColors(
    highlight: Color = LightHighlight,
    onHighlight: Color = LightOnHighlight,
    text: Color = LightText,
    subText: Color = LightSubText,
    background: Color = LightBackground,
    subBackground: Color = LightSubBackground,
    popup: Color = LightPopup,
    stroke: Color = LightStroke,
    surfaceTint: Color = LightSurfaceTint,
    scrim: Color = LightScrim,
): HHColors = HHColors(
    highlight = highlight,
    onHighlight = onHighlight,
    text = text,
    subText = subText,
    background = background,
    subBackground = subBackground,
    popup = popup,
    stroke = stroke,
    surfaceTint = surfaceTint,
    scrim = scrim,
)

fun darkHHColors(
    highlight: Color = DarkHighlight,
    onHighlight: Color = DarkOnHighlight,
    text: Color = DarkText,
    subText: Color = DarkSubText,
    background: Color = DarkBackground,
    subBackground: Color = DarkSubBackground,
    popup: Color = DarkPopup,
    stroke: Color = DarkStroke,
    surfaceTint: Color = DarkSurfaceTint,
    scrim: Color = DarkScrim,
): HHColors = HHColors(
    highlight = highlight,
    onHighlight = onHighlight,
    text = text,
    subText = subText,
    background = background,
    subBackground = subBackground,
    popup = popup,
    stroke = stroke,
    surfaceTint = surfaceTint,
    scrim = scrim,
)
