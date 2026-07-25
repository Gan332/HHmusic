package com.hh.music.player.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * HH Music theme — provides [HHColors], [HHShapes], and [HHDimens] to the
 * composable tree via CompositionLocal, inspired by SaltUI's theme architecture.
 *
 * Usage:
 * ```kotlin
 * HHMusicTheme(isDarkTheme = true) {
 *     // composable tree
 * }
 * ```
 *
 * Access:
 * ```kotlin
 * val colors = HHTheme.colors
 * val shapes = HHTheme.shapes
 * val dimens = HHTheme.dimens
 * ```
 */
object HHTheme {
    val colors: HHColors
        @Composable @ReadOnlyComposable get() = LocalHHColors.current

    val shapes: HHShapes
        @Composable @ReadOnlyComposable get() = LocalHHShapes.current

    val dimens: HHDimens
        @Composable @ReadOnlyComposable get() = LocalHHDimens.current
}

// ── CompositionLocals ──────────────────────────────────────────────────────

private val LocalHHColors = staticCompositionLocalOf { lightHHColors() }
private val LocalHHShapes = staticCompositionLocalOf { HHShapes.default() }
private val LocalHHDimens = staticCompositionLocalOf { HHDimens.default() }

/**
 * Provides [HHColors], [HHShapes], and [HHDimens] to the subtree.
 *
 * @param colors Active color palette (light or dark).
 * @param shapes Shape definitions.
 * @param dimens Spacing/dimension definitions.
 * @param content Child composable tree.
 */
@Composable
fun HHThemeProvider(
    colors: HHColors,
    shapes: HHShapes = HHShapes.default(),
    dimens: HHDimens = HHDimens.default(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalHHColors provides colors,
        LocalHHShapes provides shapes,
        LocalHHDimens provides dimens,
        content = content,
    )
}
