package com.hh.music.player.ui.miuix.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.SavedPlaylist
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalStoreProvider
import com.hh.music.player.ui.miuix.components.MiuixSongActionMenu
import com.hh.music.player.ui.miuix.components.MiuixSongRow
import com.hh.music.player.ui.playlist.PlaylistViewModel
import com.hh.music.player.ui.playlist.ToplistViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Single shell used for both Toplist (id = -1) and a single playlist. Reuses
 * the Material3 view models — Miuix only owns the visual layer.
 */
@Composable
fun MiuixPlaylistScreen(
    repository: MusicRepository,
    playlistId: Long,
    isToplist: Boolean,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    if (isToplist) {
        MiuixToplistBody(repository = repository, onBack = onBack)
    } else {
        MiuixPlaylistBody(repository = repository, playlistId = playlistId, onBack = onBack)
    }
}

@Composable
private fun MiuixToplistBody(
    repository: MusicRepository,
    onBack: () -> Unit,
) {
    val vm: ToplistViewModel = viewModel { ToplistViewModel(repository) }
    val state by vm.state.collectAsState()
    val player = LocalPlayerController.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    LaunchedEffect(Unit) { vm.load() }
    val colors = MiuixTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = "排行榜",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Text(state.error ?: "", color = colors.error, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    itemsIndexed(state.items) { index, item ->
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(item.name, color = colors.onSurface)
                                Text("点击进入歌单", style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixPlaylistBody(
    repository: MusicRepository,
    playlistId: Long,
    onBack: () -> Unit,
) {
    val vm: PlaylistViewModel = viewModel { PlaylistViewModel(repository) }
    val state by vm.state.collectAsState()
    val store = LocalStoreProvider.current
    val player = LocalPlayerController.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val savedPlaylists by store.savedPlaylists.collectAsState(initial = emptyList())
    val isSaved = savedPlaylists.any { it.id == playlistId }
    val colors = MiuixTheme.colorScheme
    LaunchedEffect(playlistId) { vm.load(playlistId) }

    fun playFrom(index: Int) {
        val list = state.playlist?.tracks ?: return
        if (list.isNotEmpty()) player.playQueue(list, index)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = state.playlist?.name ?: "歌单",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val p = state.playlist ?: return@IconButton
                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        kotlinx.coroutines.launch(scope.coroutineContext) {
                            store.toggleSavedPlaylist(
                                SavedPlaylist(
                                    id = p.id,
                                    name = p.name,
                                    coverUrl = p.coverImgUrl ?: "",
                                    creator = p.creator?.nickname ?: ""
                                )
                            )
                        }
                    }) {
                        Icon(
                            if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "收藏歌单",
                            tint = if (isSaved) colors.primary else colors.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Text(state.error ?: "", color = colors.error, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                else -> {
                    val tracks = state.playlist?.tracks.orEmpty()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        itemsIndexed(tracks) { index, song ->
                            MiuixSongRow(
                                song = song, index = index,
                                isActive = song.id == currentSong?.id,
                                isPlaying = song.id == currentSong?.id && isPlaying,
                                onClick = { playFrom(index) },
                                trailing = { MiuixSongActionMenu(player = player, song = song) }
                            )
                        }
                    }
                }
            }
        }
    }
}
