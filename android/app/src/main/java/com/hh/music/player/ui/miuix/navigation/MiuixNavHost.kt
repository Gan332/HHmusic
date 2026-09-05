package com.hh.music.player.ui.miuix.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.net.Uri
import com.hh.music.player.data.AppContainer
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalStoreProvider
import com.hh.music.player.ui.LocalDownloadManager
import com.hh.music.player.ui.LocalEqualizerController
import com.hh.music.player.ui.LocalCloudSync
import com.hh.music.player.ui.Routes
import com.hh.music.player.ui.miuix.screens.MiuixDiscoverScreen
import com.hh.music.player.ui.miuix.screens.MiuixSearchScreen
import com.hh.music.player.ui.miuix.screens.MiuixLibraryScreen
import com.hh.music.player.ui.miuix.screens.MiuixPlayerScreen
import com.hh.music.player.ui.miuix.screens.MiuixSettingsScreen
import com.hh.music.player.ui.miuix.screens.MiuixToplistScreen
import com.hh.music.player.ui.miuix.screens.MiuixPlazaScreen
import com.hh.music.player.ui.miuix.screens.MiuixPlaylistScreen
import com.hh.music.player.ui.miuix.screens.MiuixArtistScreen
import com.hh.music.player.ui.miuix.screens.MiuixAlbumScreen

@Composable
fun MiuixNavHost(container: AppContainer) {
    val navController: NavHostController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    CompositionLocalProvider(
        LocalPlayerController provides container.playerController,
        LocalStoreProvider provides container.localStore,
        LocalDownloadManager provides container.downloadManager,
        LocalEqualizerController provides container.equalizerController,
        LocalCloudSync provides container.cloudSync
    ) {
        Scaffold(
            bottomBar = {
                val tabs = listOf(
                    Routes.DISCOVER to "首页",
                    Routes.SEARCH to "发现",
                    Routes.LIBRARY to "收藏",
                    Routes.SETTINGS to "设置"
                )
                val showBottomBar = currentRoute in tabs.map { it.first } || 
                    currentRoute?.startsWith("search") == true ||
                    currentRoute?.startsWith("playlist") == true ||
                    currentRoute?.startsWith("artist") == true ||
                    currentRoute?.startsWith("album") == true ||
                    currentRoute == Routes.TOPLIST ||
                    currentRoute == Routes.PLAZA ||
                    currentRoute == Routes.PLAYER

                if (showBottomBar && currentRoute != Routes.PLAYER) {
                    NavigationBar {
                        tabs.forEach { (route, label) ->
                            NavigationBarItem(
                                selected = currentRoute == route,
                                onClick = {
                                    val navRoute = if (route == Routes.SEARCH) Routes.search() else route
                                    navController.navigate(navRoute) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        when (route) {
                                            Routes.DISCOVER -> Icons.Filled.Home
                                            Routes.SEARCH -> Icons.Filled.Explore
                                            Routes.LIBRARY -> Icons.Filled.Favorite
                                            Routes.SETTINGS -> Icons.Filled.Tune
                                            else -> Icons.Filled.Home
                                        },
                                        contentDescription = null
                                    )
                                },
                                label = { Text(label) }
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
                    MiuixDiscoverScreen(
                        repository = container.repository,
                        onOpenToplist = { navController.navigate(Routes.TOPLIST) },
                        onOpenPlaza = { navController.navigate(Routes.PLAZA) },
                        onPersonalFm = {
                            // Handle personal FM
                        },
                        onSearch = { kw -> navController.navigate(Routes.search(kw)) },
                        onOpenPlaylist = { id -> navController.navigate(Routes.playlist(id)) },
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) }
                    )
                }

                composable(
                    route = Routes.SEARCH,
                    arguments = listOf(navArgument(Routes.SEARCH_ARG) { defaultValue = "" })
                ) { backStackEntry ->
                    val keyword = backStackEntry.arguments?.getString(Routes.SEARCH_ARG).orEmpty()
                    MiuixSearchScreen(
                        repository = container.repository,
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                        initialQuery = keyword,
                        onOpenArtist = { artist ->
                            navController.navigate(Routes.artist(artist.id, artist.name))
                        }
                    )
                }

                composable(Routes.LIBRARY) {
                    MiuixLibraryScreen(
                        onOpenPlaylist = { id -> navController.navigate(Routes.playlist(id)) },
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                        onOpenDiscover = {
                            navController.navigate(Routes.DISCOVER) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        repository = container.repository,
                        cloudSync = container.cloudSync
                    )
                }

                composable(Routes.SETTINGS) {
                    MiuixSettingsScreen(
                        store = container.localStore,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.PLAYER) {
                    MiuixPlayerScreen(
                        repository = container.repository,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.TOPLIST) {
                    MiuixToplistScreen(
                        repository = container.repository,
                        onPlaylistClick = { id -> navController.navigate(Routes.playlist(id)) },
                        onBack = { navController.popBackStack() },
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) }
                    )
                }

                composable(Routes.PLAZA) {
                    MiuixPlazaScreen(
                        repository = container.repository,
                        onBack = { navController.popBackStack() },
                        onOpenPlaylist = { id -> navController.navigate(Routes.playlist(id)) },
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) }
                    )
                }

                composable(
                    route = Routes.PLAYLIST,
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { backStackEntry ->
                    MiuixPlaylistScreen(
                        playlistId = backStackEntry.arguments?.getLong("id") ?: 0L,
                        repository = container.repository,
                        onBack = { navController.popBackStack() },
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                        cloudSync = container.cloudSync
                    )
                }

                composable(
                    route = Routes.ARTIST,
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType },
                        navArgument("name") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    MiuixArtistScreen(
                        artistId = backStackEntry.arguments?.getLong("id") ?: 0L,
                        artistName = backStackEntry.arguments?.getString("name").orEmpty(),
                        repository = container.repository,
                        onBack = { navController.popBackStack() },
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                        onOpenAlbum = { id -> navController.navigate(Routes.album(id)) }
                    )
                }

                composable(
                    route = Routes.ALBUM,
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { backStackEntry ->
                    MiuixAlbumScreen(
                        albumId = backStackEntry.arguments?.getLong("id") ?: 0L,
                        repository = container.repository,
                        onBack = { navController.popBackStack() },
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) }
                    )
                }
            }
        }
    }
}
