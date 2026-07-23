package com.hh.music.player.ui.playlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.SavedPlaylist
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalStoreProvider
import com.hh.music.player.ui.components.MiniPlayerBar
import com.hh.music.player.ui.components.SongRow

@Composable
fun PlaylistScreen(
    playlistId: Long,
    repository: MusicRepository,
    onBack: () -> Unit,
    vm: PlaylistViewModel = viewModel { PlaylistViewModel(repository) }
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(playlistId) { vm.load(playlistId) }

    val player = LocalPlayerController.current
    val store = LocalStoreProvider.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()

    val savedPlaylists by store.savedPlaylists.collectAsState(initial = emptyList())
    val playlist = state.playlist
    val isSaved = savedPlaylists.any { it.id == playlistId }

    fun playFrom(index: Int) {
        val list = playlist?.tracks ?: return
        if (list.isNotEmpty()) player.playQueue(list, index)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "歌单") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        playlist?.let {
                            store.toggleSavedPlaylist(
                                SavedPlaylist(
                                    id = it.id,
                                    name = it.name,
                                    coverUrl = it.coverImgUrl ?: "",
                                    creator = it.creator?.nickname ?: ""
                                )
                            )
                        }
                    }) {
                        Icon(
                            if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "收藏歌单",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    state.error!!, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                else -> {
                    val tracks = playlist?.tracks.orEmpty()
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(tracks) { index, song ->
                            SongRow(
                                song = song,
                                index = index,
                                isActive = song.id == currentSong?.id,
                                isPlaying = song.id == currentSong?.id && isPlaying,
                                onClick = { playFrom(index) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        }
                        item { Spacer(Modifier.height(72.dp)) }
                    }
                }
            }
            MiniPlayerBar(
                player = player,
                onClick = { },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
