package com.hh.music.player.ui.miuix.scaffold

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Explore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.hh.music.player.ui.miuix.components.MiuixMiniPlayerBar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text

data class MiuixTopTab(val route: String, val label: String)

/**
 * Top-level Miuix shell: bottom [NavigationBar] + the current destination's
 * content, with a slim "now playing" pill above the bar that reuses the
 * Material3 mini-player layout but with Miuix colours.
 */
@Composable
fun MiuixMainScaffold(
    navController: NavHostController,
    currentRoute: String?,
    tabs: List<MiuixTopTab>,
    showBottomBar: Boolean,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Column {
                    MiuixMiniPlayerBar(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}

private fun tabIcon(tab: MiuixTopTab) = when (tab.route) {
    "discover" -> Icons.Filled.Explore
    "search" -> Icons.Filled.Search
    "library" -> Icons.Filled.LibraryMusic
    "settings" -> Icons.Filled.Settings
    else -> Icons.Filled.Explore
}
