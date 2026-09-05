package com.hh.music.player.ui.miuix.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Song
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.artist.ArtistUiState
import com.hh.music.player.ui.artist.ArtistViewModel
import com.hh.music.player.ui.miuix.components.MiuixArtworkImage
import com.hh.music.player.ui.miuix.components.MiuixEmptyState
import com.hh.music.player.ui.miuix.components.MiuixErrorState
import com.hh.music.player.ui.miuix.components.MiuixLoadingState
import com.hh.music.player.ui.miuix.components.MiuixMiniPlayerBar
import com.hh.music.player.ui.miuix.components.MiuixSongActionsSheet
import com.hh.music.player.ui.miuix.components.MiuixSongRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val albumYearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
private const val ARTIST_ALBUM_COLUMNS = 2


/**
 * miuix (HyperOS) 版歌手页：热门/最新切换、歌曲列表、专辑网格、
 * 分页加载更多。业务逻辑复用 [ArtistViewModel]。
 */
@Composable
fun MiuixArtistScreen(
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

    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            !onAlbumsTab && state.hasMore && !state.loadingMore &&
                lastVisible >= state.songs.size - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.songs.isNotEmpty()) vm.loadMore()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    artistName.ifBlank { "歌手" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { if (onAlbumsTab) vm.retryAlbums() else vm.retry() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            }
        },
        bottomBar = {
            MiuixMiniPlayerBar(player = player, onClick = onOpenPlayer)
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !onAlbumsTab && state.loading && state.songs.isEmpty() -> MiuixLoadingState()
                !onAlbumsTab && state.error != null && state.songs.isEmpty() ->
                    MiuixErrorState(message = state.error.orEmpty(), onRetry = vm::retry)
                else -> ArtistContent(
                    state = state,
                    onAlbumsTab = onAlbumsTab,
                    listState = listState,
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    vm = vm,
                    onPlayAll = { if (state.songs.isNotEmpty()) player.playQueue(state.songs, 0) },
                    onPlayAt = { index ->
                        if (state.songs.isNotEmpty()) player.playQueue(state.songs, index)
                    },
                    onShowActions = { actionsSong = it },
                    onOpenAlbum = onOpenAlbum
                )
            }
        }
    }

    MiuixSongActionsSheet(song = actionsSong, onDismiss = { actionsSong = null })
}

@Composable
private fun ArtistContent(
    state: ArtistUiState,
    onAlbumsTab: Boolean,
    listState: LazyListState,
    currentSongId: Long?,
    isPlaying: Boolean,
    vm: ArtistViewModel,
    onPlayAll: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onShowActions: (Song) -> Unit,
    onOpenAlbum: (Long) -> Unit
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.artistName.ifBlank { "歌手" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (onAlbumsTab) {
                            if (state.albums.isNotEmpty()) "共 ${state.albums.size} 张专辑" else "专辑"
                        } else {
                            if (state.total > 0) "共 ${state.total} 首歌曲" else "歌手歌曲"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!onAlbumsTab) {
                    FilledTonalButton(
                        onClick = onPlayAll,
                        enabled = state.songs.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("播放全部")
                    }
                }
            }
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
            if (!onAlbumsTab) {
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
        }
        if (onAlbumsTab) {
            albumsSection(state, vm, onOpenAlbum)
        } else {
            songsSection(state, currentSongId, isPlaying, onPlayAt, onShowActions, vm)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.songsSection(
    state: ArtistUiState,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlayAt: (Int) -> Unit,
    onShowActions: (Song) -> Unit,
    vm: ArtistViewModel
) {
    if (state.songs.isEmpty() && !state.loading) {
        item {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                MiuixEmptyState(hint = "这个歌手暂时没有可展示的歌曲")
            }
        }
    } else {
        itemsIndexed(state.songs) { index, song ->
            MiuixSongRow(
                song = song,
                index = index,
                isActive = song.id == currentSongId,
                isPlaying = song.id == currentSongId && isPlaying,
                onClick = { onPlayAt(index) },
                onLongClick = { onShowActions(song) }
            )
        }
        if (state.loadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
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

private fun androidx.compose.foundation.lazy.LazyListScope.albumsSection(
    state: ArtistUiState,
    vm: ArtistViewModel,
    onOpenAlbum: (Long) -> Unit
) {
    val albums = state.albums
    when {
        albums.isEmpty() && state.albumsLoading -> item {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                MiuixLoadingState()
            }
        }
        albums.isEmpty() && state.albumsError != null -> item {
            MiuixErrorState(
                message = state.albumsError.orEmpty(),
                onRetry = vm::retryAlbums,
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
            )
        }
        albums.isEmpty() -> item {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                MiuixEmptyState(hint = "这个歌手暂时没有专辑")
            }
        }
        else -> {
            itemsIndexed(albums.chunked(ARTIST_ALBUM_COLUMNS)) { _, row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    row.forEach { album ->
                        MiuixAlbumGridItem(
                            album = album,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenAlbum(album.id) }
                        )
                    }
                    repeat(ARTIST_ALBUM_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (state.albumsLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixAlbumGridItem(
    album: com.hh.music.player.data.AlbumItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        MiuixArtworkImage(
            url = album.picUrl.orEmpty(),
            contentDescription = album.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val meta = listOfNotNull(
            if (album.publishTime > 0) albumYearFormat.format(Date(album.publishTime)) else null,
            if (album.songCount > 0) "${album.songCount}首" else null
        ).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
