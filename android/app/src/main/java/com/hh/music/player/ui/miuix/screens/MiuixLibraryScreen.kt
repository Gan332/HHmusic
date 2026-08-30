package com.hh.music.player.ui.miuix.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.CloudSync
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Song
import com.hh.music.player.data.local.LocalStore
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalStoreProvider
import com.hh.music.player.ui.components.EmptyState
import com.hh.music.player.ui.library.LibraryViewModel
import com.hh.music.player.ui.miuix.components.MiuixSongRow

private enum class MiuixLibraryTab(val label: String) {
    SONGS("歌曲"),
    RECENT("最近"),
    PLAYLISTS("歌单")
}

@Composable
fun MiuixLibraryScreen(
    onOpenPlaylist: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenDiscover: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    repository: MusicRepository? = null,
    cloudSync: CloudSync? = null,
    store: LocalStore = LocalStoreProvider.current,
    vm: LibraryViewModel = viewModel { LibraryViewModel(store, repository, cloudSync) }
) {
    val favorites by vm.favorites.collectAsState()
    val recent by vm.recent.collectAsState()
    val savedPlaylists by vm.savedPlaylists.collectAsState()
    val player = LocalPlayerController.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    var tab by remember { mutableStateOf(MiuixLibraryTab.SONGS) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "音乐库",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    if (tab == MiuixLibraryTab.SONGS && favorites.isNotEmpty()) {
                        TextButton(onClick = { player.playQueue(favorites, 0) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("全部播放")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    MiuixLibraryTab.entries.forEachIndexed { index, t ->
                        SegmentedButton(
                            selected = tab == t,
                            onClick = { tab = t },
                            shape = SegmentedButtonDefaults.itemShape(index, MiuixLibraryTab.entries.size)
                        ) { Text(t.label) }
                    }
                }
            }
        }
    ) { padding ->
        when (tab) {
            MiuixLibraryTab.SONGS -> {
                if (favorites.isEmpty()) {
                    EmptyState(
                        hint = "还没有收藏歌曲",
                        icon = Icons.Filled.QueueMusic,
                        actionLabel = "去发现",
                        onAction = onOpenDiscover
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                        itemsIndexed(favorites) { index, song ->
                            MiuixSongRow(
                                song = song,
                                index = index,
                                isActive = song.id == currentSong?.id,
                                isPlaying = song.id == currentSong?.id && isPlaying,
                                onClick = { player.playQueue(favorites, index) },
                                onLongClick = { }
                            )
                        }
                    }
                }
            }
            MiuixLibraryTab.RECENT -> {
                if (recent.isEmpty()) {
                    EmptyState(
                        hint = "还没有播放记录",
                        icon = Icons.Filled.QueueMusic
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                        itemsIndexed(recent) { index, song ->
                            MiuixSongRow(
                                song = song,
                                index = index,
                                isActive = song.id == currentSong?.id,
                                isPlaying = song.id == currentSong?.id && isPlaying,
                                onClick = { player.playQueue(recent, index) }
                            )
                        }
                    }
                }
            }
            MiuixLibraryTab.PLAYLISTS -> {
                if (savedPlaylists.isEmpty()) {
                    EmptyState(
                        hint = "还没有收藏歌单",
                        icon = Icons.Filled.QueueMusic,
                        actionLabel = "去发现",
                        onAction = onOpenDiscover
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                        itemsIndexed(savedPlaylists) { _, playlist ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenPlaylist(playlist.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    // Playlist cover placeholder
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.QueueMusic,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        playlist.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        "${playlist.trackCount}首",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
