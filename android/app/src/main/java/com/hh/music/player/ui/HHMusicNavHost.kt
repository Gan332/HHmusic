package com.hh.music.player.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Search
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
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
import com.hh.music.player.ui.discover.DiscoverScreen
import com.hh.music.player.ui.mine.MineScreen
import com.hh.music.player.ui.player.PlayerScreen
import com.hh.music.player.ui.playlist.PlaylistScreen
import com.hh.music.player.ui.playlist.ToplistScreen
import com.hh.music.player.ui.search.SearchScreen

object Routes {
    const val DISCOVER = "discover"
    const val SEARCH = "search"
    const val TOPLIST = "toplist"
    const val MINE = "mine"
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
        TabItem(Routes.DISCOVER, "发现") { Icon(Icons.Filled.Explore, contentDescription = null) },
        TabItem(Routes.SEARCH, "搜索") { Icon(Icons.AutoMirrored.Filled.Search, contentDescription = null) },
        TabItem(Routes.TOPLIST, "排行榜") { Icon(Icons.Filled.Leaderboard, contentDescription = null) },
        TabItem(Routes.MINE, "我的") { Icon(Icons.Filled.Person, contentDescription = null) }
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
                        onBack = { navController.popBackStack() },
                        onPlaylistClick = { id -> navController.navigate(Routes.playlist(id)) }
                    )
                }
                composable(Routes.MINE) {
                    MineScreen(
                        store = container.localStore,
                        onOpenPlaylist = { id -> navController.navigate(Routes.playlist(id)) },
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) }
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
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
