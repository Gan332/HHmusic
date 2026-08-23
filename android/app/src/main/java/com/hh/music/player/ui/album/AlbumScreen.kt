@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.hh.music.player.ui.album

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.components.ArtworkImage
import com.hh.music.player.ui.components.ErrorState
import com.hh.music.player.ui.components.MiniPlayerBar
import com.hh.music.player.ui.components.SongRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())

/** 专辑详情页：封面/发行年份/曲目数、播放全部、完整曲目列表。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
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

    val album = state.album

    fun playFrom(index: Int) {
        val list = album?.songs ?: return
        if (list.isNotEmpty()) player.playQueue(list, index)
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(album?.name ?: "专辑", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load(albumId) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && album == null ->
                    LoadingIndicator(Modifier.align(Alignment.Center))
                state.error != null -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = { vm.load(albumId) },
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> {
                    val tracks = album?.songs.orEmpty()
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (album != null) {
                            item {
                                AlbumHeader(
                                    coverUrl = album.coverImgUrl.orEmpty(),
                                    name = album.name,
                                    artistText = tracks.firstOrNull()?.artistText ?: "",
                                    year = if (album.publishTime > 0) yearFormat.format(Date(album.publishTime)) else "",
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
                onClick = onOpenPlayer,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun AlbumHeader(
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
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(MaterialTheme.shapes.extraLarge),
            contentAlignment = Alignment.Center
        ) {
            ArtworkImage(
                url = coverUrl,
                contentDescription = name,
                modifier = Modifier.fillMaxSize()
            )
        }
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
