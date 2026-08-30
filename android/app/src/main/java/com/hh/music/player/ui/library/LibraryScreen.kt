package com.hh.music.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hh.music.player.data.local.LocalStore
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalStoreProvider
import com.hh.music.player.ui.components.MiniPlayerBar
import com.hh.music.player.ui.components.SongActionMenu
import com.hh.music.player.ui.components.SongRow

/**
 * 音乐库 — 本地个人内容的统一浏览入口：
 * 收藏歌曲 + 最近播放 + 收藏歌单，三栏卡片 + 点选即播/即开。
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

    Scaffold(
        topBar = {
            Text(
                "音乐库",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize()) {
                // ---- 收藏的歌曲 ----
                item { SectionHeader("我收藏的歌曲", "${favorites.size} 首") }
                if (favorites.isEmpty()) {
                    item { EmptyRow("还没有收藏的歌曲，去播放页点 ♥ 吧") }
                } else {
                    itemsIndexed(favorites.take(20)) { index, song ->
                        SongRow(
                            song = song, index = index,
                            isActive = song.id == currentSong?.id,
                            isPlaying = song.id == currentSong?.id && isPlaying,
                            onClick = { player.playQueue(favorites, index) },
                            trailing = { SongActionMenu(player = player, song = song) }
                        )
                        HorizontalDivider()
                    }
                }

                // ---- 最近播放 ----
                item { Spacer(Modifier.height(12.dp)); SectionHeader("最近播放", "${recent.size} 首") }
                if (recent.isEmpty()) {
                    item { EmptyRow("暂无播放记录") }
                } else {
                    itemsIndexed(recent.take(20)) { index, song ->
                        SongRow(
                            song = song, index = index,
                            isActive = song.id == currentSong?.id,
                            isPlaying = song.id == currentSong?.id && isPlaying,
                            onClick = { player.playQueue(recent, index) },
                            trailing = { SongActionMenu(player = player, song = song) }
                        )
                        HorizontalDivider()
                    }
                }

                // ---- 收藏的歌单 ----
                item { Spacer(Modifier.height(12.dp)); SectionHeader("收藏的歌单", "${savedPlaylists.size} 个") }
                if (savedPlaylists.isEmpty()) {
                    item { EmptyRow("还没有收藏的歌单") }
                } else {
                    itemsIndexed(savedPlaylists) { _, pl ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenPlaylist(pl.id) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (pl.coverUrl.isNotBlank()) {
                                AsyncImage(model = pl.coverUrl, contentDescription = null, modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(pl.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                Text(if (pl.creator.isNotBlank()) pl.creator else "未知创建者", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
            MiniPlayerBar(player = player, onClick = onOpenPlayer, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyRow(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
}
