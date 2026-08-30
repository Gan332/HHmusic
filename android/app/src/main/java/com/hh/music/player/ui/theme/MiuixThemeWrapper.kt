package com.hh.music.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

/**
 * Miuix theme wrapper that applies Xiaomi HyperOS design language.
 *
 * @param themeMode Light/Dark/System mode
 * @param content Composable content
 */
@Composable
fun MiuixThemeWrapper(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val mode = when (themeMode) {
        AppThemeMode.LIGHT -> ColorSchemeMode.Light
        AppThemeMode.DARK -> ColorSchemeMode.Dark
        AppThemeMode.SYSTEM -> ColorSchemeMode.System
    }
    val controller = remember { ThemeController(mode) }
    MiuixTheme(controller = controller, content = content)
}
