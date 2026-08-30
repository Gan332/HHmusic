package com.hh.music.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import com.hh.music.player.ui.HHMusicNavHost
import com.hh.music.player.ui.theme.AppThemeColor
import com.hh.music.player.ui.theme.AppThemeMode
import com.hh.music.player.ui.theme.HHMusicTheme
import com.hh.music.player.ui.theme.MiuixThemeWrapper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as HHMusicApp
            val store = app.container.localStore
            val themeMode by store.themeMode.collectAsState(initial = AppThemeMode.SYSTEM.key)
            val themeColor by store.themeColor.collectAsState(initial = AppThemeColor.GREEN.key)
            val dynamicColor by store.dynamicColor.collectAsState(initial = true)
            val uiStyle by store.uiStyle.collectAsState(initial = "classic")
            val resolvedMode = AppThemeMode.from(themeMode)
            val resolvedColor = AppThemeColor.from(themeColor)
            val darkTheme = when (resolvedMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            val view = LocalView.current
            SideEffect {
                if (!view.isInEditMode) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        ) { darkTheme },
                        navigationBarStyle = SystemBarStyle.auto(
                            0xEEFFFFFF.toInt(),
                            0xEE000000.toInt()
                        ) { darkTheme }
                    )
                }
            }
            if (uiStyle == "miuix") {
                MiuixThemeWrapper(themeMode = resolvedMode) {
                    HHMusicNavHost(app.container)
                }
            } else {
                HHMusicTheme(
                    themeMode = resolvedMode,
                    themeColor = resolvedColor,
                    dynamicColor = dynamicColor
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
