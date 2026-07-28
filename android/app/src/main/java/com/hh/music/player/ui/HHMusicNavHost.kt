package com.hh.music.player.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hh.music.player.data.AppContainer
import com.hh.music.player.data.local.LocalStore
import com.hh.music.player.playback.PlayerController
import com.hh.music.player.ui.library.LibraryScreen
import com.hh.music.player.ui.settings.SettingsScreen
import com.hh.music.player.ui.discover.DiscoverScreen
import com.hh.music.player.ui.player.PlayerScreen
import com.hh.music.player.ui.playlist.PlaylistScreen
import com.hh.music.player.ui.playlist.ToplistScreen
import com.hh.music.player.ui.search.SearchScreen

object Routes {
    const val DISCOVER = "discover"
    const val SEARCH = "search"
    const val TOPLIST = "toplist"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val PLAYLIST = "playlist/{id}"
    const val PLAYER = "player"

    fun playlist(id: Long) = "playlist/$id"
}

/** Provides the app-wide player controller to composables. */
val LocalPlayerController = compositionLocalOf<PlayerController> {
    error("PlayerController not provided")
}

/** Provides the local store (favorites/recent/history). */
val LocalStoreProvider = compositionLocalOf<LocalStore> {
    error("LocalStore not provided")
}

private data class TabItem(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
fun HHMusicNavHost(container: AppContainer) {
    val navController: NavHostController = rememberNavController()
    val tabs = listOf(
        TabItem(Routes.DISCOVER, "首页") { Icon(Icons.Filled.Home, contentDescription = null) },
        TabItem(Routes.SEARCH, "发现") { Icon(Icons.Filled.Explore, contentDescription = null) },
        TabItem(Routes.LIBRARY, "收藏") { Icon(Icons.Filled.Favorite, contentDescription = null) },
        TabItem(Routes.SETTINGS, "设置") { Icon(Icons.Filled.Tune, contentDescription = null) }
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.route }

    CompositionLocalProvider(
        LocalPlayerController provides container.playerController,
        LocalStoreProvider provides container.localStore
    ) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp
                    ) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = tab.icon,
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.DISCOVER,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Routes.DISCOVER) {
                    DiscoverScreen(
                        repository = container.repository,
                        onOpenToplist = { navController.navigate(Routes.TOPLIST) },
                        onOpenSearch = { navController.navigate(Routes.SEARCH) },
                        onOpenPlaylist = { id -> navController.navigate(Routes.playlist(id)) },
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) }
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        repository = container.repository,
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) }
                    )
                }
                composable(Routes.TOPLIST) {
                    ToplistScreen(
                        repository = container.repository,
                        onPlaylistClick = { id -> navController.navigate(Routes.playlist(id)) }
                    )
                }
                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        onOpenPlaylist = { id -> navController.navigate(Routes.playlist(id)) },
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        store = container.localStore,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.PLAYLIST) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                    PlaylistScreen(
                        playlistId = id,
                        repository = container.repository,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.PLAYER) {
                    PlayerScreen(
                        repository = container.repository,
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
