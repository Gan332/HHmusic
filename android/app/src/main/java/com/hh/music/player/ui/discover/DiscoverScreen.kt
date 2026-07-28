package com.hh.music.player.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Song
import com.hh.music.player.network.RecommendPlaylistItem
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.components.MiniPlayerBar

@Composable
fun DiscoverScreen(
    repository: MusicRepository,
    onOpenToplist: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
    vm: DiscoverViewModel = viewModel { DiscoverViewModel(repository) }
) {
    val state by vm.state.collectAsState()
    val player = LocalPlayerController.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("HH Music", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text("听见此刻", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
                Surface(
                    onClick = onOpenSearch,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text("搜索歌曲、歌手或歌单", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> ErrorState(state.error.orEmpty(), vm::refresh, Modifier.align(Alignment.Center))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QuickEntry("排行榜", Icons.Filled.Leaderboard, onOpenToplist, Modifier.weight(1f))
                            QuickEntry("每日推荐", Icons.Filled.AutoAwesome, {
                                if (state.recommend.isNotEmpty()) player.playQueue(state.recommend, 0)
                            }, Modifier.weight(1f))
                            QuickEntry("新歌速递", Icons.Filled.Whatshot, {
                                if (state.newSongs.isNotEmpty()) player.playQueue(state.newSongs, 0)
                            }, Modifier.weight(1f))
                        }
                    }
                    item {
                        SectionTitle("每日推荐", "为你精选")
                        SongCarousel(
                            songs = state.recommend.take(10),
                            activeId = currentSong?.id,
                            isPlaying = isPlaying,
                            onPlay = { index -> player.playQueue(state.recommend, index) }
                        )
                    }
                    item {
                        Spacer(Modifier.height(18.dp))
                        SectionTitle("推荐歌单", "更多好音乐")
                        PlaylistCarousel(state.playlists, onOpenPlaylist)
                    }
                    item {
                        Spacer(Modifier.height(18.dp))
                        SectionTitle("新歌速递", "最近上新")
                        SongCarousel(
                            songs = state.newSongs.take(10),
                            activeId = currentSong?.id,
                            isPlaying = isPlaying,
                            onPlay = { index -> player.playQueue(state.newSongs, index) }
                        )
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
            MiniPlayerBar(player, onOpenPlayer, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun QuickEntry(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(7.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SongCarousel(
    songs: List<Song>,
    activeId: Long?,
    isPlaying: Boolean,
    onPlay: (Int) -> Unit
) {
    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            MediaTile(
                title = song.name,
                subtitle = song.artistText,
                imageUrl = song.coverUrl,
                active = song.id == activeId && isPlaying,
                onClick = { onPlay(index) }
            )
        }
    }
}

@Composable
private fun PlaylistCarousel(playlists: List<RecommendPlaylistItem>, onClick: (Long) -> Unit) {
    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(playlists, key = { it.id }) { playlist ->
            MediaTile(playlist.name, playlist.creatorName.ifBlank { "精选歌单" }, playlist.coverUrl, false) { onClick(playlist.id) }
        }
    }
}

@Composable
private fun MediaTile(
    title: String,
    subtitle: String,
    imageUrl: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(Modifier.width(148.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (imageUrl.isNotBlank()) {
                AsyncImage(imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(38.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "播放")
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp)
        )
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ErrorState(message: String, retry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.TextButton(onClick = retry) { Text("重新加载") }
    }
}
