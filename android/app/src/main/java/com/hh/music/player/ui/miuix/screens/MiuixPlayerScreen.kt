package com.hh.music.player.ui.miuix.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Song
import com.hh.music.player.playback.PlayMode
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalCloudSync
import com.hh.music.player.ui.LocalStoreProvider
import com.hh.music.player.ui.miuix.components.MiuixArtworkImage
import com.hh.music.player.ui.player.PlayerViewModel
import com.hh.music.player.ui.components.formatDuration
import kotlinx.coroutines.launch

@Composable
fun MiuixPlayerScreen(
    repository: MusicRepository,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    vm: PlayerViewModel = viewModel { PlayerViewModel(repository) }
) {
    val player = LocalPlayerController.current
    val store = LocalStoreProvider.current
    val cloudSync = LocalCloudSync.current
    val scope = rememberCoroutineScope()
    val song by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val playMode by player.playMode.collectAsState()
    val lyricState by vm.state.collectAsState()
    val position by player.positionMs.collectAsState()
    val duration by player.durationMs.collectAsState()
    val favorites by store.favorites.collectAsState(initial = emptyList())
    val isFav = song?.let { s -> favorites.any { it.id == s.id } } ?: false

    LaunchedEffect(song?.id) { song?.id?.let { vm.loadLyric(it) } }

    val playModeIcon = when (playMode) {
        PlayMode.SEQUENCE -> Icons.Filled.Repeat
        PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
        PlayMode.SHUFFLE -> Icons.Filled.Shuffle
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred cover background
        val coverUrl = song?.coverUrl.orEmpty()
        if (coverUrl.startsWith("http")) {
            MiuixArtworkImage(
                url = coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(48.dp)
                    .scale(1.15f)
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song?.name ?: "未在播放",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song?.artistText ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = {
                    song?.let {
                        val willLike = !isFav
                        scope.launch {
                            store.toggleFavorite(it)
                            cloudSync.pushLike(it.id, willLike)
                        }
                    }
                }) {
                    Icon(
                        if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Album art
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(280.dp)
            ) {
                if (coverUrl.startsWith("http")) {
                    MiuixArtworkImage(
                        url = coverUrl,
                        contentDescription = "封面",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Lyrics preview (if available)
            if (lyricState.lines.isNotEmpty()) {
                val current = lyricState.lines.lastOrNull { it.timeMs <= position }
                if (current != null) {
                    Text(
                        current.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Progress bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = if (duration > 0) position.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { player.seekTo((it * duration).toLong()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatDuration(position),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatDuration(duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Playback controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { player.cyclePlayMode() }) {
                    Icon(
                        playModeIcon,
                        contentDescription = "播放模式",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { player.playPrevious() }) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "上一首",
                        modifier = Modifier.size(36.dp)
                    )
                }
                FilledIconButton(
                    onClick = { player.togglePlayPause() },
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = { player.playNext() }) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "下一首",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
