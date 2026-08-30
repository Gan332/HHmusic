package com.hh.music.player.ui.miuix.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.hh.music.player.ui.discover.DiscoverViewModel
import com.hh.music.player.ui.miuix.components.MiuixSongActionMenu
import com.hh.music.player.ui.miuix.components.MiuixSongRow
import top.yukonga.miuix.kmp.basic.AssistChip
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixDiscoverScreen(
    repository: MusicRepository,
    onOpenToplist: () -> Unit,
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
            TopAppBar(
                title = "发现",
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                return@Box
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                state.error?.let { error ->
                    item {
                        Text(
                            error,
                            color = MiuixTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(onClick = onOpenToplist, label = { Text("排行榜") })
                    }
                }
                item { SmallTitle("每日推荐") }
                itemsIndexed(state.recommend.take(10)) { index, song ->
                    MiuixSongRow(
                        song = song, index = index,
                        isActive = song.id == currentSong?.id,
                        isPlaying = song.id == currentSong?.id && isPlaying,
                        onClick = { if (state.recommend.isNotEmpty()) player.playQueue(state.recommend, index) },
                        trailing = { MiuixSongActionMenu(player = player, song = song) }
                    )
                }
                item { SmallTitle("新歌速递") }
                itemsIndexed(state.newSongs.take(10)) { index, song ->
                    MiuixSongRow(
                        song = song, index = index,
                        isActive = song.id == currentSong?.id,
                        isPlaying = song.id == currentSong?.id && isPlaying,
                        onClick = { if (state.newSongs.isNotEmpty()) player.playQueue(state.newSongs, index) },
                        trailing = { MiuixSongActionMenu(player = player, song = song) }
                    )
                }
                item { SmallTitle("推荐歌单") }
                item { PlaylistGrid(state.playlists, onOpenPlaylist) }
            }
        }
    }
}

@Composable
private fun PlaylistGrid(
    playlists: List<RecommendPlaylistItem>,
    onClick: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        playlists.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item ->
                    PlaylistCard(item, Modifier.weight(1f)) { onClick(item.id) }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PlaylistCard(item: RecommendPlaylistItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val corner = RoundedCornerShape(20.dp)
    Column(modifier = modifier.clickable(onClick = onClick)) {
        if (item.coverUrl.isNotBlank()) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(corner)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(colors.surfaceVariant, corner)
            )
        }
        Text(
            item.name,
            style = MiuixTheme.textStyles.body2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = colors.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
