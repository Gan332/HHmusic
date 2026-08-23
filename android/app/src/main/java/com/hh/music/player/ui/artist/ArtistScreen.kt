@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.hh.music.player.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.AlbumItem
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Song
import com.hh.music.player.playback.PlayerController
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.components.ArtworkImage
import com.hh.music.player.ui.components.EmptyState
import com.hh.music.player.ui.components.ErrorState
import com.hh.music.player.ui.components.MiniPlayerBar
import com.hh.music.player.ui.components.SongActionsSheet
import com.hh.music.player.ui.components.SongRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ALBUM_COLUMNS = 2

/**
 * 歌手页：热门/最新歌曲、专辑网格、播放全部、分页加载更多。
 * 入口来自搜索页歌手结果区。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artistId: Long,
    artistName: String,
    repository: MusicRepository,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    vm: ArtistViewModel = viewModel { ArtistViewModel(repository, artistId, artistName) }
) {
    val state by vm.state.collectAsState()
    val player = LocalPlayerController.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    var actionsSong by remember { mutableStateOf<Song?>(null) }
    val onAlbumsTab = state.selectedTab == ArtistUiState.TAB_ALBUMS

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(artistName.ifBlank { "歌手" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { if (onAlbumsTab) vm.retryAlbums() else vm.retry() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !onAlbumsTab && state.loading && state.songs.isEmpty() ->
                    LoadingIndicator(Modifier.align(Alignment.Center))
                !onAlbumsTab && state.error != null && state.songs.isEmpty() ->
                    ErrorState(message = state.error.orEmpty(), onRetry = vm::retry, modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        ArtistHeader(
                            name = artistName,
                            subtitle = if (onAlbumsTab) {
                                if (state.albums.isNotEmpty()) "共 ${state.albums.size} 张专辑" else "专辑"
                            } else {
                                if (state.total > 0) "共 ${state.total} 首歌曲" else "歌手歌曲"
                            },
                            onPlayAll = {
                                if (!onAlbumsTab && state.songs.isNotEmpty()) player.playQueue(state.songs, 0)
                            },
                            playAllEnabled = !onAlbumsTab && state.songs.isNotEmpty()
                        )
                    }
                    item {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            SegmentedButton(
                                selected = !onAlbumsTab,
                                onClick = { vm.setTab(ArtistUiState.TAB_SONGS) },
                                shape = SegmentedButtonDefaults.itemShape(0, 2)
                            ) { Text("歌曲") }
                            SegmentedButton(
                                selected = onAlbumsTab,
                                onClick = { vm.setTab(ArtistUiState.TAB_ALBUMS) },
                                shape = SegmentedButtonDefaults.itemShape(1, 2)
                            ) { Text("专辑") }
                        }
                    }
                    if (onAlbumsTab) {
                        albumsSection(state, vm, onOpenAlbum)
                    } else {
                        songsSection(state, vm, currentSong, isPlaying, player, onShowActions = { actionsSong = it })
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

/** 歌曲 Tab：热门/最新切换 + 歌曲列表 + 分页。 */
private fun LazyListScope.songsSection(
    state: ArtistUiState,
    vm: ArtistViewModel,
    currentSong: Song?,
    isPlaying: Boolean,
    player: PlayerController,
    onShowActions: (Song) -> Unit
) {
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
                onLongClick = { onShowActions(song) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        }
        if (state.hasMore || state.loadingMore) {
            item {
                LoadMoreFooter(
                    loadingMore = state.loadingMore,
                    onLoadMore = vm::loadMore
                )
            }
        }
        if (state.error != null) {
            item {
                Text(
                    state.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/** 专辑 Tab：两列封面网格 + 分页。 */
private fun LazyListScope.albumsSection(
    state: ArtistUiState,
    vm: ArtistViewModel,
    onOpenAlbum: (Long) -> Unit
) {
    val albums = state.albums
    when {
        albums.isEmpty() && state.albumsLoading -> item {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        }
        albums.isEmpty() && state.albumsError != null -> item {
            ErrorState(
                message = state.albumsError.orEmpty(),
                onRetry = vm::retryAlbums,
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
            )
        }
        albums.isEmpty() -> item {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                EmptyState(hint = "这个歌手暂时没有专辑")
            }
        }
        else -> {
            itemsIndexed(albums.chunked(ALBUM_COLUMNS)) { _, row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    row.forEach { album ->
                        AlbumGridItem(
                            album = album,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenAlbum(album.id) }
                        )
                    }
                    repeat(ALBUM_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (state.albumsMore || state.albumsLoading) {
                item {
                    LoadMoreFooter(
                        loadingMore = state.albumsLoading,
                        onLoadMore = vm::loadMoreAlbums
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadMoreFooter(loadingMore: Boolean, onLoadMore: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loadingMore) {
            LoadingIndicator(modifier = Modifier.size(26.dp))
        } else {
            TextButton(onClick = onLoadMore) { Text("加载更多") }
        }
    }
}

@Composable
private fun AlbumGridItem(
    album: AlbumItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(modifier.clickable(onClick = onClick).padding(6.dp)) {
        ArtworkImage(
            url = album.picUrl.orEmpty(),
            contentDescription = album.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.large)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            album.name.ifBlank { "未知专辑" },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            albumSubtitle(album),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun albumSubtitle(album: AlbumItem): String {
    val parts = mutableListOf<String>()
    if (album.publishTime > 0) parts += yearFormat.format(Date(album.publishTime))
    if (album.songCount > 0) parts += "${album.songCount}首"
    return parts.joinToString(" · ").ifBlank { "专辑" }
}

private val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())

@Composable
private fun ArtistHeader(
    name: String,
    subtitle: String,
    onPlayAll: () -> Unit,
    playAllEnabled: Boolean
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
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = onPlayAll, enabled = playAllEnabled) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("播放全部")
            }
        }
    }
}
