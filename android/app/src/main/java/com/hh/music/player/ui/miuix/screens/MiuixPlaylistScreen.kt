package com.hh.music.player.ui.miuix.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.CloudSync
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.SavedPlaylist
import com.hh.music.player.data.Song
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalStoreProvider
import com.hh.music.player.ui.miuix.components.MiuixArtworkImage
import com.hh.music.player.ui.miuix.components.MiuixEmptyState
import com.hh.music.player.ui.miuix.components.MiuixErrorState
import com.hh.music.player.ui.miuix.components.MiuixLoadingState
import com.hh.music.player.ui.miuix.components.MiuixMiniPlayerBar
import com.hh.music.player.ui.miuix.components.MiuixSongActionsSheet
import com.hh.music.player.ui.miuix.components.MiuixSongRow
import com.hh.music.player.ui.playlist.PlaylistViewModel
import kotlinx.coroutines.launch

/** miuix (HyperOS) 版歌单详情页：封面头部 + 播放全部 + 收藏歌单 + 曲目列表。 */
@Composable
fun MiuixPlaylistScreen(
    playlistId: Long,
    repository: MusicRepository,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    cloudSync: CloudSync? = null,
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
    var actionsSong by remember { mutableStateOf<Song?>(null) }

    fun playFrom(index: Int) {
        val list = playlist?.tracks ?: return
        if (list.isNotEmpty()) player.playQueue(list, index)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    playlist?.name ?: "歌单",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { vm.load(playlistId) }) {
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
                state.loading && playlist == null -> MiuixLoadingState()
                state.error != null -> MiuixErrorState(
                    message = state.error.orEmpty(),
                    onRetry = { vm.load(playlistId) }
                )
                playlist == null || playlist.tracks.isEmpty() -> MiuixEmptyState(
                    hint = "歌单为空或加载失败"
                )
                else -> {
                    val tracks = playlist.tracks
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            MiuixPlaylistHeader(
                                coverUrl = playlist.coverImgUrl.orEmpty(),
                                name = playlist.name,
                                creator = playlist.creator?.nickname.orEmpty(),
                                trackCount = tracks.size,
                                isSaved = isSaved,
                                onPlayAll = { playFrom(0) },
                                onToggleSave = {
                                    val p = playlist
                                    // Local first (source of truth); cloud is best-effort.
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
                                    cloudSync?.pushPlaylistSubscribe(p.id, !isSaved)
                                }
                            )
                        }
                        itemsIndexed(tracks) { index, song ->
                            MiuixSongRow(
                                song = song,
                                index = index,
                                isActive = song.id == currentSong?.id,
                                isPlaying = song.id == currentSong?.id && isPlaying,
                                onClick = { playFrom(index) },
                                onLongClick = { actionsSong = song }
                            )
                        }
                    }
                }
            }
        }
    }

    MiuixSongActionsSheet(song = actionsSong, onDismiss = { actionsSong = null })
}

@Composable
private fun MiuixPlaylistHeader(
    coverUrl: String,
    name: String,
    creator: String,
    trackCount: Int,
    isSaved: Boolean,
    onPlayAll: () -> Unit,
    onToggleSave: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiuixArtworkImage(
            url = coverUrl,
            contentDescription = name,
            modifier = Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(20.dp))
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
            Row {
                FilledTonalButton(onClick = onPlayAll, enabled = trackCount > 0) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("播放全部")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onToggleSave) {
                    Icon(
                        if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isSaved) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isSaved) "已收藏" else "收藏")
                }
            }
        }
    }
}

