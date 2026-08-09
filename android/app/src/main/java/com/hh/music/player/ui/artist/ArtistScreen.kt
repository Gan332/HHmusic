package com.hh.music.player.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Song
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.components.EmptyState
import com.hh.music.player.ui.components.ErrorState
import com.hh.music.player.ui.components.MiniPlayerBar
import com.hh.music.player.ui.components.SongActionsSheet
import com.hh.music.player.ui.components.SongRow

/**
 * 歌手页：热门/最新歌曲、播放全部、分页加载更多。
 * 入口来自搜索页歌手结果区；v1 暂不包含专辑 Tab。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artistId: Long,
    artistName: String,
    repository: MusicRepository,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    vm: ArtistViewModel = viewModel { ArtistViewModel(repository, artistId, artistName) }
) {
    val state by vm.state.collectAsState()
    val player = LocalPlayerController.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    var actionsSong by remember { mutableStateOf<Song?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(artistName.ifBlank { "歌手" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = vm::retry) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.songs.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null && state.songs.isEmpty() ->
                    ErrorState(message = state.error, onRetry = vm::retry, modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        ArtistHeader(
                            name = artistName,
                            total = state.total,
                            onPlayAll = {
                                if (state.songs.isNotEmpty()) player.playQueue(state.songs, 0)
                            }
                        )
                    }
                    item {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            SegmentedButton(
                                selected = state.order == "hot",
                                onClick = { vm.setOrder("hot") },
                                shape = SegmentedButtonDefaults.itemShape(0, 2)
                            ) { Text("热门") }
                            SegmentedButton(
                                selected = state.order == "time",
                                onClick = { vm.setOrder("time") },
                                shape = SegmentedButtonDefaults.itemShape(1, 2)
                            ) { Text("最新") }
                        }
                    }
                    if (state.songs.isEmpty() && !state.loading) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                            ) {
                                EmptyState(hint = "这个歌手暂时没有可展示的歌曲")
                            }
                        }
                    } else {
                        itemsIndexed(state.songs) { index, song ->
                            SongRow(
                                song = song,
                                index = index,
                                isActive = song.id == currentSong?.id,
                                isPlaying = song.id == currentSong?.id && isPlaying,
                                onClick = {
                                    if (state.songs.isNotEmpty()) player.playQueue(state.songs, index)
                                },
                                onLongClick = { actionsSong = song }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        }
                        if (state.hasMore || state.loadingMore) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (state.loadingMore) {
                                        CircularProgressIndicator(modifier = Modifier.size(26.dp))
                                    } else {
                                        TextButton(onClick = vm::loadMore) { Text("加载更多") }
                                    }
                                }
                            }
                        }
                        if (state.error != null) {
                            item {
                                Text(
                                    state.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
            MiniPlayerBar(
                player = player,
                onClick = onOpenPlayer,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    SongActionsSheet(song = actionsSong, onDismiss = { actionsSong = null })
}

@Composable
private fun ArtistHeader(
    name: String,
    total: Int,
    onPlayAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name.ifBlank { "未知歌手" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (total > 0) "共 $total 首歌曲" else "歌手歌曲",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = onPlayAll) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("播放全部")
            }
        }
    }
}
