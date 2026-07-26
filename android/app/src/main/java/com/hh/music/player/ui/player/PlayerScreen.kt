package com.hh.music.player.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.playback.PlayMode
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalStoreProvider
import com.hh.music.player.ui.components.SongRow
import com.hh.music.player.ui.components.formatDuration
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    repository: MusicRepository,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    vm: PlayerViewModel = viewModel { PlayerViewModel(repository) }
) {
    val player = LocalPlayerController.current
    val store = LocalStoreProvider.current
    val song by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val queue by player.queue.collectAsState()
    val playMode by player.playMode.collectAsState()
    val lyricState by vm.state.collectAsState()
    val scope = rememberCoroutineScope()

    val favorites by store.favorites.collectAsState(initial = emptyList())
    val isFav = song?.let { s -> favorites.any { it.id == s.id } } ?: false

    var showQueue by remember { mutableStateOf(false) }

    LaunchedEffect(song?.id) { song?.id?.let { vm.loadLyric(it) } }

    val playModeIcon = when (playMode) {
        PlayMode.SEQUENCE -> Icons.Filled.Repeat
        PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
        PlayMode.SHUFFLE -> Icons.Filled.Shuffle
    }
    val playModeDesc = when (playMode) {
        PlayMode.SEQUENCE -> "顺序播放"
        PlayMode.REPEAT_ONE -> "单曲循环"
        PlayMode.SHUFFLE -> "随机播放"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Top bar (does NOT read position -> stable unless song/playing changes)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(song?.name ?: "未在播放", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(song?.artistText ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { song?.let { scope.launch { store.toggleFavorite(it) } } }) {
                    Icon(
                        if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置")
                }
                IconButton(onClick = { showQueue = !showQueue }) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "队列")
                }
            }

            Spacer(Modifier.height(12.dp))

            // Cover (stable; recomposes on song change only)
            val coverSong = song
            if (coverSong != null && coverSong.coverUrl.startsWith("http")) {
                AsyncImage(
                    model = coverSong.coverUrl, contentDescription = null,
                    modifier = Modifier.fillMaxWidth(0.7f).height(280.dp).clip(RoundedCornerShape(16.dp)).align(Alignment.CenterHorizontally)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth(0.7f).height(280.dp).clip(RoundedCornerShape(16.dp)).align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(80.dp)) }
            }

            Spacer(Modifier.height(16.dp))

            // Lyrics — isolated composable that reads position; only this subtree recomposes per second.
            LyricsSection(
                lyricState = lyricState,
                positionFlow = player.positionMs,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Progress slider — isolated; only this recomposes with position.
            ProgressSection(player = player)

            Spacer(Modifier.height(4.dp))

            // Controls (stable; reads isPlaying + playMode only)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { player.cyclePlayMode() }, modifier = Modifier.weight(1f)) {
                    Icon(playModeIcon, contentDescription = playModeDesc, tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { player.playPrevious() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(36.dp))
                }
                FilledIconButton(onClick = { player.togglePlayPause() }, modifier = Modifier.weight(1f).size(64.dp)) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { player.playNext() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一首", modifier = Modifier.size(36.dp))
                }
                Box(Modifier.weight(1f))
            }
        }

        // Queue drawer overlay
        if (showQueue) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("播放队列", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        itemsIndexedSafe(queue) { index, s ->
                            SongRow(
                                song = s, index = index,
                                isActive = s.id == song?.id,
                                isPlaying = s.id == song?.id && isPlaying,
                                onClick = { player.playAt(index) }
                            )
                        }
                    }
                    TextButton(onClick = { showQueue = false }, modifier = Modifier.align(Alignment.End)) { Text("关闭") }
                }
            }
        }
    }
}

/**
 * Reads position in isolation so the per-second update recomposes ONLY this subtree,
 * not the whole player. Uses [derivedStateOf] so scrolling recomposition fires only
 * when the active line actually changes.
 */
@Composable
private fun LyricsSection(
    lyricState: LyricState,
    positionFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    modifier: Modifier = Modifier
) {
    val lyricList = lyricState.lines
    val position by positionFlow.collectAsState()
    val activeIndex by remember(lyricList) {
        derivedStateOf {
            var idx = -1
            for (i in lyricList.indices) {
                if (lyricList[i].timeMs <= position) idx = i else break
            }
            idx
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && lyricList.isNotEmpty()) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    Box(modifier) {
        if (lyricList.isEmpty()) {
            Text(
                if (lyricState.loading) "歌词加载中..." else "暂无歌词",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 32.dp)
            ) {
                itemsIndexed(lyricList) { index, line ->
                    val active = index == activeIndex
                    Text(
                        text = line.text.ifBlank { "♪" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    )
                    lyricState.translations[line.timeMs]?.let { trans ->
                        Text(
                            trans,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Isolated progress slider — recomposes per second without touching the rest of the player. */
@Composable
private fun ProgressSection(player: com.hh.music.player.playback.PlayerController) {
    val position by player.positionMs.collectAsState()
    val duration by player.durationMs.collectAsState()
    var seekValue by remember { mutableStateOf<Float?>(null) }
    val sliderPos = seekValue ?: (if (duration > 0) position.toFloat() / duration else 0f)
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = sliderPos.coerceIn(0f, 1f),
            onValueChange = { seekValue = it },
            onValueChangeFinished = {
                seekValue?.let { player.seekTo((it * duration).toLong()) }
                seekValue = null
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(position), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDuration(duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Helper to iterate with index without an explicit import at usage sites. */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedSafe(
    items: List<com.hh.music.player.data.Song>,
    itemContent: @Composable (index: Int, item: com.hh.music.player.data.Song) -> Unit
) {
    this.items(items.size) { index -> itemContent(index, items[index]) }
}
