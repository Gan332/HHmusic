package com.hh.music.player.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.SavedPlaylist
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalStoreProvider
import com.hh.music.player.ui.components.ArtworkImage
import com.hh.music.player.ui.components.MiniPlayerBar
import com.hh.music.player.ui.components.SongRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: Long,
    repository: MusicRepository,
    onBack: () -> Unit,
    vm: PlaylistViewModel = viewModel { PlaylistViewModel(repository) }
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(playlistId) { vm.load(playlistId) }

    val player = LocalPlayerController.current
    val store = LocalStoreProvider.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()

    val savedPlaylists by store.savedPlaylists.collectAsState(initial = emptyList())
    val playlist = state.playlist
    val isSaved = savedPlaylists.any { it.id == playlistId }
    val scope = rememberCoroutineScope()

    fun playFrom(index: Int) {
        val list = playlist?.tracks ?: return
        if (list.isNotEmpty()) player.playQueue(list, index)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "歌单") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text(if (isSaved) "取消收藏" else "收藏歌单")
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = {
                            val p = playlist ?: return@IconButton
                            scope.launch {
                                store.toggleSavedPlaylist(
                                    SavedPlaylist(
                                        id = p.id,
                                        name = p.name,
                                        coverUrl = p.coverImgUrl ?: "",
                                        creator = p.creator?.nickname ?: ""
                                    )
                                )
                            }
                        }) {
                            Icon(
                                if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "收藏歌单",
                                tint = if (isSaved) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { vm.load(playlistId) },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading && playlist == null ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.error != null -> Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                    else -> {
                        val tracks = playlist?.tracks.orEmpty()
                        LazyColumn(Modifier.fillMaxSize()) {
                            if (playlist != null) {
                                item {
                                    PlaylistHeader(
                                        coverUrl = playlist.coverImgUrl.orEmpty(),
                                        name = playlist.name,
                                        creator = playlist.creator?.nickname.orEmpty(),
                                        trackCount = tracks.size,
                                        onPlayAll = { playFrom(0) }
                                    )
                                }
                            }
                            itemsIndexed(tracks) { index, song ->
                                SongRow(
                                    song = song,
                                    index = index,
                                    isActive = song.id == currentSong?.id,
                                    isPlaying = song.id == currentSong?.id && isPlaying,
                                    onClick = { playFrom(index) }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            }
                            item { Spacer(Modifier.height(72.dp)) }
                        }
                    }
                }
                MiniPlayerBar(
                    player = player,
                    onClick = { },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    coverUrl: String,
    name: String,
    creator: String,
    trackCount: Int,
    onPlayAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkImage(
                    url = coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(112.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOf(creator.ifBlank { "精选歌单" }, "$trackCount 首").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = onPlayAll, enabled = trackCount > 0) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("播放全部")
            }
        }
    }
}
