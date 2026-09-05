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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.hh.music.player.ui.album.AlbumViewModel
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

private val miuixAlbumYearFormat = SimpleDateFormat("yyyy", Locale.getDefault())

/** miuix (HyperOS) 版专辑详情页：封面/发行年份/曲目数、播放全部、完整曲目列表。 */
@Composable
fun MiuixAlbumScreen(
    albumId: Long,
    repository: MusicRepository,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    vm: AlbumViewModel = viewModel { AlbumViewModel(repository) }
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(albumId) { vm.load(albumId) }

    val player = LocalPlayerController.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    var actionsSong by remember { mutableStateOf<Song?>(null) }

    val album = state.album

    fun playFrom(index: Int) {
        val list = album?.songs ?: return
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
                    album?.name ?: "专辑",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { vm.load(albumId) }) {
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
                state.loading && album == null -> MiuixLoadingState()
                state.error != null -> MiuixErrorState(
                    message = state.error.orEmpty(),
                    onRetry = { vm.load(albumId) }
                )
                album == null || album.songs.isEmpty() -> MiuixEmptyState(
                    hint = "专辑为空或加载失败"
                )
                else -> {
                    val tracks = album.songs
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            MiuixAlbumHeader(
                                coverUrl = album.coverImgUrl.orEmpty(),
                                name = album.name,
                                artistText = tracks.firstOrNull()?.artistText ?: "",
                                year = if (album.publishTime > 0) {
                                    miuixAlbumYearFormat.format(Date(album.publishTime))
                                } else "",
                                trackCount = tracks.size,
                                onPlayAll = { playFrom(0) }
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
private fun MiuixAlbumHeader(
    coverUrl: String,
    name: String,
    artistText: String,
    year: String,
    trackCount: Int,
    onPlayAll: () -> Unit
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
                name.ifBlank { "未知专辑" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            val meta = listOfNotNull(
                artistText.takeIf { it.isNotBlank() },
                year.takeIf { it.isNotBlank() },
                "$trackCount 首"
            ).joinToString(" · ")
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = onPlayAll, enabled = trackCount > 0) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("播放全部")
            }
        }
    }
}

