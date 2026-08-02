package com.hh.music.player.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    repository: MusicRepository,
    onOpenToplist: () -> Unit,
    onSearch: (String) -> Unit,
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
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "HH Music",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "听见此刻",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("刷新推荐") } },
                        state = rememberTooltipState()
                    ) {
                        FilledTonalIconButton(onClick = vm::refresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // 折叠态 M3E 搜索入口：点击直接进入全屏搜索页，避免内联展开导致错位。
                Surface(
                    shape = SearchBarDefaults.dockedShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = SearchBarDefaults.TonalElevation,
                    shadowElevation = SearchBarDefaults.ShadowElevation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { onSearch("") }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "搜索歌曲、歌手或歌单",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Box(Modifier.fillMaxSize()) {
                when {
                    state.loading && state.recommend.isEmpty() ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.error != null ->
                        ErrorState(state.error.orEmpty(), vm::refresh, Modifier.align(Alignment.Center))
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        item { QuickEntries(state, onOpenToplist, player::playQueue) }
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
}

@Composable
private fun QuickEntries(
    state: DiscoverState,
    onOpenToplist: () -> Unit,
    playQueue: (List<Song>, Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickEntry("排行榜", Icons.Filled.Leaderboard, onOpenToplist, Modifier.weight(1f))
        QuickEntry(
            "每日推荐",
            Icons.Filled.AutoAwesome,
            { if (state.recommend.isNotEmpty()) playQueue(state.recommend, 0) },
            Modifier.weight(1f)
        )
        QuickEntry(
            "新歌速递",
            Icons.Filled.Whatshot,
            { if (state.newSongs.isNotEmpty()) playQueue(state.newSongs, 0) },
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickEntry(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(onClick = onClick, modifier = modifier) {
        Column(
            Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.height(8.dp))
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
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongCarousel(
    songs: List<Song>,
    activeId: Long?,
    isPlaying: Boolean,
    onPlay: (Int) -> Unit
) {
    if (songs.isEmpty()) return
    val carouselState = rememberCarouselState(initialItem = 0, itemCount = { songs.size })
    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 200.dp,
        itemSpacing = 12.dp,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) { itemIndex ->
        val song = songs[itemIndex]
        key(song.id) {
            MediaTile(
                title = song.name,
                subtitle = song.artistText,
                imageUrl = song.coverUrl,
                active = song.id == activeId && isPlaying,
                onClick = { onPlay(itemIndex) },
                modifier = Modifier.maskClip(MaterialTheme.shapes.extraLarge)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistCarousel(
    playlists: List<RecommendPlaylistItem>,
    onClick: (Long) -> Unit
) {
    if (playlists.isEmpty()) return
    val carouselState = rememberCarouselState(initialItem = 0, itemCount = { playlists.size })
    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 156.dp,
        itemSpacing = 12.dp,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) { itemIndex ->
        val playlist = playlists[itemIndex]
        key(playlist.id) {
            MediaTile(
                title = playlist.name,
                subtitle = playlist.creatorName.ifBlank { "精选歌单" },
                imageUrl = playlist.coverUrl,
                active = false,
                onClick = { onClick(playlist.id) },
                modifier = Modifier.maskClip(MaterialTheme.shapes.extraLarge)
            )
        }
    }
}

@Composable
private fun MediaTile(
    title: String,
    subtitle: String,
    imageUrl: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (imageUrl.isNotBlank()) {
                AsyncImage(
                    imageUrl,
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(40.dp)
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
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ErrorState(message: String, retry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = retry) { Text("重新加载") }
    }
}
