package com.hh.music.player.ui.miuix

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hh.music.player.data.AppContainer
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalStoreProvider
import com.hh.music.player.ui.Routes
import com.hh.music.player.ui.miuix.library.MiuixLibraryScreen
import com.hh.music.player.ui.miuix.player.MiuixPlayerScreen
import com.hh.music.player.ui.miuix.playlist.MiuixPlaylistScreen
import com.hh.music.player.ui.miuix.search.MiuixSearchScreen
import com.hh.music.player.ui.miuix.settings.MiuixSettingsScreen
import com.hh.music.player.ui.miuix.scaffold.MiuixMainScaffold
import com.hh.music.player.ui.miuix.scaffold.MiuixTopTab

/**
 * Miuix (HyperOS-style) NavHost. Routes are intentionally identical to the
 * Material3 [com.hh.music.player.ui.HHMusicNavHost] so the data layer and
 * PlayerController can be shared between skins; only the visual tree swaps.
 */
@Composable
fun MiuixNavHost(container: AppContainer) {
    val navController: NavHostController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabs = remember {
        listOf(
            MiuixTopTab(Routes.DISCOVER, "发现"),
            MiuixTopTab(Routes.SEARCH, "搜索"),
            MiuixTopTab(Routes.LIBRARY, "音乐库"),
            MiuixTopTab(Routes.SETTINGS, "设置"),
        )
    }
    val showBottomBar = currentRoute in tabs.map { it.route }

    CompositionLocalProvider(
        LocalPlayerController provides container.playerController,
        LocalStoreProvider provides container.localStore
    ) {
        MiuixMainScaffold(
            navController = navController,
            currentRoute = currentRoute,
            tabs = tabs,
            showBottomBar = showBottomBar,
            content = { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Routes.DISCOVER,
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                ) {
                    composable(Routes.DISCOVER) {
                        MiuixDiscoverScreen(
                            repository = container.repository,
                            onOpenToplist = { navController.navigate(Routes.TOPLIST) },
                            onOpenPlaylist = { id -> navController.navigate(Routes.playlist(id)) },
                            onOpenPlayer = { navController.navigate(Routes.PLAYER) }
                        )
                    }
                    composable(Routes.SEARCH) {
                        MiuixSearchScreen(
                            repository = container.repository,
                            onOpenPlayer = { navController.navigate(Routes.PLAYER) }
                        )
                    }
                    composable(Routes.TOPLIST) {
                        MiuixPlaylistScreen(
                            repository = container.repository,
                            playlistId = -1L,
                            isToplist = true,
                            onBack = { navController.popBackStack() },
                            onOpenPlayer = { navController.navigate(Routes.PLAYER) }
                        )
                    }
                    composable(Routes.LIBRARY) {
                        MiuixLibraryScreen(
                            onOpenPlaylist = { id -> navController.navigate(Routes.playlist(id)) },
                            onOpenPlayer = { navController.navigate(Routes.PLAYER) }
                        )
                    }
                    composable(Routes.SETTINGS) {
                        MiuixSettingsScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.PLAYLIST) { backStackEntry ->
                        val id = backStackEntry.arguments?.getString("id")?.toLongOrNull()
                        if (id == null || id <= 0) {
                            androidx.compose.runtime.LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            MiuixPlaylistScreen(
                                repository = container.repository,
                                playlistId = id,
                                isToplist = false,
                                onBack = { navController.popBackStack() },
                                onOpenPlayer = { navController.navigate(Routes.PLAYER) }
                            )
                        }
                    }
                    composable(Routes.PLAYER) {
                        MiuixPlayerScreen(
                            repository = container.repository,
                            onBack = { navController.popBackStack() },
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                        )
                    }
                }
            }
        )
    }
}
