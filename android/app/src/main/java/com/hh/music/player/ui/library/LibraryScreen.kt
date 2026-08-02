package com.hh.music.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hh.music.player.data.SavedPlaylist
import com.hh.music.player.data.Song
import com.hh.music.player.data.local.LocalStore
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalStoreProvider
import com.hh.music.player.ui.components.MiniPlayerBar
import com.hh.music.player.ui.components.SongRow

private enum class LibraryTab(val label: String) {
    SONGS("歌曲"),
    RECENT("最近"),
    PLAYLISTS("歌单")
}

/**
 * 音乐库 — 本地个人内容的统一浏览入口：
 * 收藏歌曲 + 最近播放 + 收藏歌单，M3E 分段按钮切换 + 点选即播/即开。
 */
@Composable
fun LibraryScreen(
    onOpenPlaylist: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
    store: LocalStore = LocalStoreProvider.current,
    vm: LibraryViewModel = viewModel { LibraryViewModel(store) }
) {
    val favorites by vm.favorites.collectAsState()
    val recent by vm.recent.collectAsState()
    val savedPlaylists by vm.savedPlaylists.collectAsState()
    val player = LocalPlayerController.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    var tab by remember { mutableStateOf(LibraryTab.SONGS) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 8.dp)) {
                Text(
                    "音乐库",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    LibraryTab.entries.forEachIndexed { index, t ->
                        SegmentedButton(
                            selected = tab == t,
                            onClick = { tab = t },
                            shape = SegmentedButtonDefaults.itemShape(index, LibraryTab.entries.size)
                        ) {
                            Text(t.label)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                LibraryTab.SONGS -> SongListPane(
                    songs = favorites,
                    emptyHint = "还没有收藏的歌曲，去播放页点 ♥ 吧",
                    emptyIcon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    onPlay = { index -> if (favorites.isNotEmpty()) player.playQueue(favorites, index) }
                )
                LibraryTab.RECENT -> SongListPane(
                    songs = recent,
                    emptyHint = "暂无播放记录",
                    emptyIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    onPlay = { index -> if (recent.isNotEmpty()) player.playQueue(recent, index) }
                )
                LibraryTab.PLAYLISTS -> PlaylistPane(
                    playlists = savedPlaylists,
                    emptyHint = "还没有收藏的歌单",
                    onOpen = onOpenPlaylist
                )
            }
            MiniPlayerBar(player = player, onClick = onOpenPlayer, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun SongListPane(
    songs: List<Song>,
    emptyHint: String,
    emptyIcon: @Composable () -> Unit,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlay: (Int) -> Unit
) {
    if (songs.isEmpty()) {
        EmptyPane(emptyHint, emptyIcon)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
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
private fun PlaylistPane(
    playlists: List<SavedPlaylist>,
    emptyHint: String,
    onOpen: (Long) -> Unit
) {
    if (playlists.isEmpty()) {
        EmptyPane(emptyHint) { Icon(Icons.Filled.QueueMusic, contentDescription = null) }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(playlists) { _, pl ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(pl.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(56.dp).clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    if (pl.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = pl.coverUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        pl.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (pl.creator.isNotBlank()) pl.creator else "未知创建者",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun EmptyPane(hint: String, icon: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(72.dp).clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(32.dp)) { icon() }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
