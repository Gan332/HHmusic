package com.hh.music.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * HH Music theme entry point.
 *
 * Bridges the custom [HHTheme] system (colors, shapes, dimens) to
 * Material 3's [MaterialTheme] so existing components keep working
 * while new components can use [HHTheme.colors] / [HHTheme.shapes] /
 * [HHTheme.dimens] directly.
 *
 * Inspired by SaltUI's clean composition-local architecture:
 * - [HHColors] provides semantic roles (highlight, text, subText,
 *   background, subBackground, popup, stroke, surfaceTint, scrim).
 * - [HHShapes] provides consistent rounded-corner shapes.
 * - [HHDimens] provides a spacing system.
 *
 * @param isDarkTheme Whether to use the dark or light palette.
 * @param content The composable content tree.
 */
@Composable
fun HHMusicTheme(
    isDarkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (isDarkTheme) darkHHColors() else lightHHColors()

    // Bridge to Material 3 color slots so existing components work seamlessly.
    val materialColorScheme = if (isDarkTheme) {
        darkColorScheme(
            primary = colors.highlight,
            onPrimary = colors.onHighlight,
            primaryContainer = colors.highlight.copy(alpha = 0.15f),
            onPrimaryContainer = colors.highlight,
            secondary = colors.highlight,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.subBackground,
            onSurface = colors.text,
            surfaceVariant = colors.surfaceTint,
            onSurfaceVariant = colors.subText,
            outline = colors.stroke,
            outlineVariant = colors.stroke.copy(alpha = 0.5f),
            scrim = colors.scrim,
            error = Color(0xFFCF6679),
        )
    } else {
        lightColorScheme(
            primary = colors.highlight,
            onPrimary = colors.onHighlight,
            primaryContainer = colors.highlight.copy(alpha = 0.12f),
            onPrimaryContainer = colors.highlight,
            secondary = colors.highlight,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.subBackground,
            onSurface = colors.text,
            surfaceVariant = colors.surfaceTint,
            onSurfaceVariant = colors.subText,
            outline = colors.stroke,
            outlineVariant = colors.stroke.copy(alpha = 0.5f),
            scrim = colors.scrim,
            error = Color(0xFFB3261E),
        )
    }

    HHThemeProvider(
        colors = colors,
        shapes = HHShapes.default(),
        dimens = HHDimens.default(),
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}
