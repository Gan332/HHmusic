package com.hh.music.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.hh.music.player.ui.HHMusicNavHost
import com.hh.music.player.ui.miuix.MiuixNavHost
import com.hh.music.player.ui.theme.HHMusicMiuixTheme
import com.hh.music.player.ui.theme.HHMusicTheme
import com.hh.music.player.ui.theme.UiStyle
import com.hh.music.player.ui.theme.parseHexColor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as HHMusicApp
            val store = app.container.localStore
            val isDarkThemePref by store.isDarkTheme.collectAsState(initial = false)
            val dynamicColor by store.dynamicColor.collectAsState(initial = true)
            val themeColorHex by store.themeColor.collectAsState(initial = "#1DB954")
            val uiStyleKey by store.uiStyle.collectAsState(initial = UiStyle.MATERIAL.key)
            val seedColor = remember(themeColorHex) { parseHexColor(themeColorHex) }
            // Honour explicit user pref; fall back to system when they didn't pick.
            val isDarkTheme = isDarkThemePref || isSystemInDarkTheme()

            // Skins own their own theme tree; the rest of the wiring
            // (NavController routes, PlayerController, LocalStore) is shared.
            when (UiStyle.from(uiStyleKey)) {
                UiStyle.MIUIX -> HHMusicMiuixTheme(
                    isDarkTheme = isDarkTheme,
                    seedColor = seedColor,
                ) {
                    MiuixNavHost(app.container)
                }
                UiStyle.MATERIAL -> HHMusicTheme(
                    isDarkTheme = isDarkTheme,
                    dynamicColor = dynamicColor,
                    seedColor = seedColor,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        HHMusicNavHost(app.container)
                    }
                }
            }
        }
    }
}
