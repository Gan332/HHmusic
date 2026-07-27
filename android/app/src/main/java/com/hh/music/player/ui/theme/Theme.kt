package com.hh.music.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * HH Music theme entry point — full Material Design 3 implementation.
 *
 * ## Dynamic color (Material You)
 * On Android 12+ (API 31) the colour scheme is derived from the user's
 * wallpaper via [dynamicLightColorScheme] / [dynamicDarkColorScheme],
 * giving every device a personalised look.
 *
 * On older devices (or when [dynamicColor] is explictly `false`) it falls
 * back to the hand‑crafted NetEase‑green palette defined in [HHColors].
 *
 * ## Sub‑systems
 * - **Color** — all 30 M3 color roles (primary, secondary, tertiary,
 *   error, neutral, inverse) mapped through [HHColors] or dynamic API.
 * - **Typography** — the full 15‑style M3 type scale ([HHMusicTypography]).
 * - **Shapes** — the 5‑tier M3 shape scale ([HHShapes]).
 *
 * @param isDarkTheme  Dark mode override.  Default follows system setting.
 * @param dynamicColor Whether to use wallpaper‑derived colours (Android 12+).
 *                     Default `true`.
 * @param content      The composable content tree.
 */
@Composable
fun HHMusicTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDarkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        isDarkTheme -> darkHHColors().toDarkColorScheme()
        else -> lightHHColors().toLightColorScheme()
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
