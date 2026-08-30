package com.hh.music.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * Miuix (HyperOS-style) theme wrapper. Independent of the Material3 [HHMusicTheme]
 * skin — chosen by [com.hh.music.player.data.local.LocalStore.uiStyle] =
 * [UiStyle.MIUIX].
 *
 * Seed colour derives from the same hex stored by the Material skin so the two
 * skins stay visually consistent when the user picks a brand colour.
 */
@Composable
fun HHMusicMiuixTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    seedColor: Color = Color(0xFF1DB954),
    content: @Composable () -> Unit,
) {
    val controller = remember(isDarkTheme, seedColor) {
        ThemeController(
            colorSchemeMode = if (isDarkTheme) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
            keyColor = seedColor,
            isDark = isDarkTheme,
        )
    }
    MiuixTheme(controller = controller, content = content)
}
