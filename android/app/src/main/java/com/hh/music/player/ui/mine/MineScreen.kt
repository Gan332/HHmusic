package com.hh.music.player.ui.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import com.hh.music.player.ui.components.EmptyState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.SavedPlaylist
import com.hh.music.player.data.local.LocalStore
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.components.ArtworkImage
import com.hh.music.player.ui.components.MiniPlayerBar
import com.hh.music.player.ui.components.SongRow

@Composable
fun MineScreen(
    store: LocalStore,
    onOpenPlaylist: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
    vm: MineViewModel = viewModel { MineViewModel(store) }
) {
    val favorites by vm.favorites.collectAsState()
    val recent by vm.recent.collectAsState()
    val savedPlaylists by vm.savedPlaylists.collectAsState()
    val player = LocalPlayerController.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()

    var tab by remember { mutableStateOf(0) }
    var showClearRecentDialog by remember { mutableStateOf(false) }
    val tabs = listOf("收藏", "最近播放", "歌单")

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("我的", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
                TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    tabs.forEachIndexed { index, t ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(t) })
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> SongListPane(
                    songs = favorites,
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    emptyHint = "还没有收藏的歌曲",
                    onPlay = { index -> if (favorites.isNotEmpty()) player.playQueue(favorites, index) }
                )
                1 -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            enabled = recent.isNotEmpty(),
                            onClick = { showClearRecentDialog = true }
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("清空")
                        }
                    }
                    SongListPane(
                        songs = recent,
                        currentSongId = currentSong?.id,
                        isPlaying = isPlaying,
                        emptyHint = "暂无播放记录",
                        onPlay = { index -> if (recent.isNotEmpty()) player.playQueue(recent, index) },
                        modifier = Modifier.weight(1f)
                    )
                }
                2 -> SavedPlaylistList(
                    playlists = savedPlaylists,
                    onOpen = onOpenPlaylist,
                    emptyHint = "还没有收藏的歌单"
                )
            }
            MiniPlayerBar(player = player, onClick = onOpenPlayer, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (showClearRecentDialog) {
        AlertDialog(
            onDismissRequest = { showClearRecentDialog = false },
            title = { Text("清空最近播放") },
            text = { Text("将删除全部最近播放记录，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearRecent()
                    showClearRecentDialog = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearRecentDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SongListPane(
    songs: List<com.hh.music.player.data.Song>,
    currentSongId: Long?,
    isPlaying: Boolean,
    emptyHint: String,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) {
        EmptyState(
            hint = emptyHint,
            modifier = modifier
        )
        return
    }
    LazyColumn(modifier.fillMaxSize()) {
        itemsIndexed(songs) { index, song ->
            SongRow(
                song = song, index = index,
                isActive = song.id == currentSongId,
                isPlaying = song.id == currentSongId && isPlaying,
                onClick = { onPlay(index) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun SavedPlaylistList(
    playlists: List<SavedPlaylist>,
    onOpen: (Long) -> Unit,
    emptyHint: String
) {
    if (playlists.isEmpty()) {
        EmptyState(
            hint = emptyHint,
            icon = Icons.Filled.QueueMusic
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        itemsIndexed(playlists) { _, item ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(item.id) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
ArtworkImage(
                    url = item.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.creator, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}
