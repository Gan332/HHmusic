package com.hh.music.player.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.network.RecommendPlaylistItem
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.components.MiniPlayerBar
import com.hh.music.player.ui.components.SongRow

@Composable
fun DiscoverScreen(
    repository: MusicRepository,
    onOpenPlaylist: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
    vm: DiscoverViewModel = viewModel { DiscoverViewModel(repository) }
) {
    val state by vm.state.collectAsState()
    val player = LocalPlayerController.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("发现", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    // Section: 每日推荐
                    item { SectionHeader("每日推荐") }
                    itemsIndexed(state.recommend.take(10)) { index, song ->
                        SongRow(
                            song = song, index = index,
                            isActive = song.id == currentSong?.id,
                            isPlaying = song.id == currentSong?.id && isPlaying,
                            onClick = { if (state.recommend.isNotEmpty()) player.playQueue(state.recommend, index) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    }

                    // Section: 新歌速递
                    item { Spacer(Modifier.height(16.dp)); SectionHeader("新歌速递") }
                    itemsIndexed(state.newSongs.take(10)) { index, song ->
                        SongRow(
                            song = song, index = index,
                            isActive = song.id == currentSong?.id,
                            isPlaying = song.id == currentSong?.id && isPlaying,
                            onClick = { if (state.newSongs.isNotEmpty()) player.playQueue(state.newSongs, index) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    }

                    // Section: 推荐歌单
                    item { Spacer(Modifier.height(16.dp)); SectionHeader("推荐歌单") }
                    item {
                        PlaylistGrid(state.playlists, onOpenPlaylist)
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
            MiniPlayerBar(player = player, onClick = onOpenPlayer, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun PlaylistGrid(
    playlists: List<RecommendPlaylistItem>,
    onClick: (Long) -> Unit
) {
    // Simple two-column-ish vertical list of cards.
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        playlists.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item ->
                    PlaylistCard(item, Modifier.weight(1f)) { onClick(item.id) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PlaylistCard(item: RecommendPlaylistItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        if (item.coverUrl.isNotBlank()) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(10.dp))
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(10.dp))
            ) {}
        }
        Text(
            item.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
