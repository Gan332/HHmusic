package com.hh.music.player.ui.miuix.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.hh.music.player.ui.components.formatDuration
import com.hh.music.player.ui.player.LyricState
import com.hh.music.player.ui.player.PlayerViewModel
import com.hh.music.player.ui.player.WaveProgressBar
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixPlayerScreen(
    repository: MusicRepository,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    vm: PlayerViewModel = viewModel { PlayerViewModel(repository) }
) {
    val player = LocalPlayerController.current
    val store = LocalStoreProvider.current
    val song by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val position by player.positionMs.collectAsState()
    val duration by player.durationMs.collectAsState()
    val playMode by player.playMode.collectAsState()
    val lyricState by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val error by player.error.collectAsState()
    LaunchedEffect(error) { error?.let { snackbarHostState.showSnackbar(it) } }
    val favorites by store.favorites.collectAsState(initial = emptyList())
    val isFav = song?.let { s -> favorites.any { it.id == s.id } } ?: false
    val waveProgress by store.waveProgress.collectAsState(initial = false)
    val colors = MiuixTheme.colorScheme

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(song?.name ?: "未在播放", style = MiuixTheme.textStyles.title4, color = colors.onBackground)
                    Text(song?.artistText ?: "", style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceVariant)
                }
                IconButton(onClick = {
                    val s = song ?: return@IconButton
                    scope.launch {
                        val liked = !favorites.any { it.id == s.id }
                        val synced = repository.likeSong(s.id, liked).isSuccess
                        store.toggleFavorite(s)
                        if (!synced) snackbarHostState.showSnackbar("已本地收藏，未同步到网易云")
                    }
                }) {
                    Icon(
                        if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (isFav) colors.primary else colors.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置")
                }
                IconButton(onClick = { /* TODO: 队列抽屉（Miuix 暂用 Material OverlayBottomSheet 或 Material3 Surface） */ }) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "队列")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(Modifier.height(8.dp))
                val coverSong = song
                if (coverSong != null && coverSong.coverUrl.startsWith("http")) {
                    AsyncImage(
                        model = coverSong.coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(280.dp)
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(28.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(280.dp)
                            .align(Alignment.CenterHorizontally)
                            .background(colors.surfaceVariant, RoundedCornerShape(28.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(80.dp), tint = colors.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(16.dp))
                LyricsSection(lyricState, position, modifier = Modifier.weight(1f).fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                ProgressSection(player, position, duration, waveProgress)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = { player.cyclePlayMode() }) {
                        Icon(
                            when (playMode) {
                                PlayMode.SEQUENCE -> Icons.Filled.Repeat
                                PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                                PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                            },
                            contentDescription = when (playMode) {
                                PlayMode.SEQUENCE -> "顺序播放"
                                PlayMode.REPEAT_ONE -> "单曲循环"
                                PlayMode.SHUFFLE -> "随机播放"
                            },
                            tint = colors.primary
                        )
                    }
                    IconButton(onClick = { player.playPrevious() }) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(36.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(50))
                            .background(colors.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = { player.togglePlayPause() }) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = colors.onPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    IconButton(onClick = { player.playNext() }) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "下一首", modifier = Modifier.size(36.dp))
                    }
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun LyricsSection(lyricState: LyricState, position: Long, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val list = lyricState.lines
    val activeIndex = remember(list, position) {
        var idx = -1
        for (i in list.indices) {
            if (list[i].timeMs <= position) idx = i else break
        }
        idx
    }
    Box(modifier) {
        if (list.isEmpty()) {
            Text(
                if (lyricState.loading) "歌词加载中..." else "暂无歌词",
                color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize()) {
                androidx.compose.foundation.lazy.itemsIndexed(list) { index, line ->
                    val active = index == activeIndex
                    Text(
                        text = line.text.ifBlank { "♪" },
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) colors.primary else colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressSection(
    player: com.hh.music.player.playback.PlayerController,
    position: Long,
    duration: Long,
    useWaveProgress: Boolean,
) {
    if (useWaveProgress) {
        WaveProgressBar(
            progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            onSeek = { frac -> player.seekTo((frac * duration).toLong()) }
        )
    } else {
        Slider(
            value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            onValueChange = { v -> if (duration > 0) player.seekTo((v * duration).toLong()) },
            modifier = Modifier.fillMaxWidth()
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatDuration(position), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariant)
        Text(formatDuration(duration), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariant)
    }
}
