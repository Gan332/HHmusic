package com.hh.music.player.ui.miuix.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
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
import com.hh.music.player.network.RecommendPlaylistItem
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.discover.DiscoverViewModel
import com.hh.music.player.ui.miuix.components.MiuixArtworkImage
import com.hh.music.player.ui.miuix.components.MiuixErrorState
import com.hh.music.player.ui.miuix.components.MiuixLoadingState
import com.hh.music.player.ui.miuix.components.MiuixMiniPlayerBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiuixDiscoverScreen(
    repository: MusicRepository,
    onOpenToplist: () -> Unit,
    onOpenPlaza: () -> Unit = {},
    onPersonalFm: () -> Unit = {},
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
            TopAppBar(
                title = {
                    Column {
                        Text("HH Music", fontWeight = FontWeight.SemiBold)
                        Text("听见此刻", style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh(force = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            MiuixMiniPlayerBar(player = player, onClick = onOpenPlayer)
        }
    ) { padding ->
        when {
            state.allEmpty && state.recommend.loading -> MiuixLoadingState()
            state.allEmpty && state.allFailed -> MiuixErrorState("推荐加载失败，请检查网络", { vm.refresh(force = true) })
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    // 搜索入口
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(56.dp)
                            .clickable { onSearch("") }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text("搜索歌曲、歌手或歌单", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    // 快捷入口
                    QuickEntries(
                        state,
                        onOpenToplist,
                        onOpenPlaza,
                        onPersonalFm,
                        { songs -> player.playQueue(songs, 0) }
                    )
                }
                item {
                    Spacer(Modifier.height(10.dp))
                    SectionTitle(title = "每日推荐", subtitle = "为你精选")
                    SectionContent(state.recommend, onRetry = { vm.refresh(force = true) }) {
                        SongCarousel(
                            songs = it.take(10),
                            activeId = currentSong?.id,
                            isPlaying = isPlaying,
                            onPlay = { index -> player.playQueue(state.recommend.data, index) }
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(18.dp))
                    SectionTitle(title = "推荐歌单", subtitle = "更多好音乐")
                    SectionContent(state.playlists, onRetry = { vm.refresh(force = true) }) {
                        PlaylistCarousel(it, onOpenPlaylist)
                    }
                }
                item {
                    Spacer(Modifier.height(18.dp))
                    SectionTitle(title = "新歌速递", subtitle = "最近上新")
                    SectionContent(state.newSongs, onRetry = { vm.refresh(force = true) }) {
                        SongCarousel(
                            songs = it.take(10),
                            activeId = currentSong?.id,
                            isPlaying = isPlaying,
                            onPlay = { index -> player.playQueue(state.newSongs.data, index) }
                        )
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}

@Composable
private fun QuickEntries(
    state: com.hh.music.player.ui.discover.DiscoverState,
    onOpenToplist: () -> Unit,
    onOpenPlaza: () -> Unit,
    onPersonalFm: () -> Unit,
    onPlay: (List<Song>) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickEntry("排行榜", Icons.Filled.Leaderboard, onOpenToplist, Modifier.weight(1f))
        QuickEntry("歌单广场", Icons.Filled.QueueMusic, onOpenPlaza, Modifier.weight(1f))
        QuickEntry(
            "私人FM",
            Icons.Filled.Radio,
            {
                android.widget.Toast.makeText(context, "私人FM加载中…", android.widget.Toast.LENGTH_SHORT).show()
                onPersonalFm()
            },
            Modifier.weight(1f)
        )
        QuickEntry(
            "每日推荐",
            Icons.Filled.AutoAwesome,
            { if (state.recommend.data.isNotEmpty()) onPlay(state.recommend.data) },
            Modifier.weight(1f)
        )
        QuickEntry(
            "新歌速递",
            Icons.Filled.Whatshot,
            { if (state.newSongs.data.isNotEmpty()) onPlay(state.newSongs.data) },
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
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun <T> SectionContent(
    section: com.hh.music.player.ui.discover.SectionState<T>,
    onRetry: () -> Unit,
    content: @Composable (List<T>) -> Unit
) {
    when {
        section.data.isNotEmpty() -> content(section.data)
        section.error -> Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("该模块加载失败", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRetry) { Text("重试") }
        }
        else -> Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun SongCarousel(
    songs: List<Song>,
    activeId: Long?,
    isPlaying: Boolean,
    onPlay: (Int) -> Unit
) {
    if (songs.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(songs) { itemIndex, song ->
            MiuixMediaTile(
                title = song.name,
                subtitle = song.artistText,
                imageUrl = song.coverUrl,
                active = song.id == activeId && isPlaying,
                onClick = { onPlay(itemIndex) },
                modifier = Modifier.width(200.dp)
            )
        }
    }
}

@Composable
private fun PlaylistCarousel(
    playlists: List<RecommendPlaylistItem>,
    onClick: (Long) -> Unit
) {
    if (playlists.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(playlists) { itemIndex, playlist ->
            MiuixMediaTile(
                title = playlist.name,
                subtitle = playlist.creatorName.ifBlank { "精选歌单" },
                imageUrl = playlist.coverUrl,
                active = false,
                onClick = { onClick(playlists[itemIndex].id) },
                modifier = Modifier.width(156.dp)
            )
        }
    }
}

@Composable
private fun MiuixMediaTile(
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
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onClick)
        ) {
            MiuixArtworkImage(
                url = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
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
