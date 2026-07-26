package com.hh.music.player.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf

import androidx.compose.ui.Modifier
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
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
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
val LocalPlayerController = staticCompositionLocalOf<PlayerController> {
    error("PlayerController not provided")
}

/** Provides the local store (favorites/recent/history). */
val LocalStoreProvider = staticCompositionLocalOf<LocalStore> {
    error("LocalStore not provided")
}

private data class TabItem(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
fun HHMusicNavHost(container: AppContainer) {
    val navController: NavHostController = rememberNavController()
    val tabs = listOf(
        TabItem(Routes.DISCOVER, "发现") { Icon(Icons.Filled.Explore, contentDescription = null) },
        TabItem(Routes.SEARCH, "搜索") { Icon(Icons.Filled.Search, contentDescription = null) },
        TabItem(Routes.LIBRARY, "音乐库") { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
        TabItem(Routes.SETTINGS, "设置") { Icon(Icons.Filled.Settings, contentDescription = null) }
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
                    NavigationBar {
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
                                label = { Text(tab.label) }
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
                        onPlaylistClick = { id -> navController.navigate(Routes.playlist(id)) },
                        onBack = { navController.popBackStack() },
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) }
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
                    val id = backStackEntry.arguments?.getString("id")?.toLongOrNull()
                    if (id == null || id <= 0) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
                        PlaylistScreen(
                            playlistId = id,
                            repository = container.repository,
                            onBack = { navController.popBackStack() },
                            onOpenPlayer = { navController.navigate(Routes.PLAYER) }
                        )
                    }
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
