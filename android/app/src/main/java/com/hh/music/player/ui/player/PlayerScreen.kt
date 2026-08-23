package com.hh.music.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.EqualizerPresets
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Song
import com.hh.music.player.playback.PlayMode
import com.hh.music.player.playback.PlaybackEngine
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalStoreProvider
import com.hh.music.player.ui.LocalEqualizerController
import com.hh.music.player.ui.ProgressStyle
import com.hh.music.player.ui.theme.LyricFontScale
import com.hh.music.player.ui.components.ArtworkImage
import com.hh.music.player.ui.components.SongActionsSheet
import com.hh.music.player.ui.components.WaveformSlider
import com.hh.music.player.ui.components.rememberWaveformAmplitudes
import com.hh.music.player.ui.components.SongRow
import com.hh.music.player.ui.components.formatDuration
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerScreen(
    repository: MusicRepository,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    vm: PlayerViewModel = viewModel { PlayerViewModel(repository) }
) {
    val player = LocalPlayerController.current
    val store = LocalStoreProvider.current
    val equalizer = LocalEqualizerController.current
    val cloudSync = com.hh.music.player.ui.LocalCloudSync.current
    val song by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val queue by player.queue.collectAsState()
    val queueStats = remember(queue) { PlaybackEngine.queueStats(queue) }
    val playMode by player.playMode.collectAsState()
    val lyricState by vm.state.collectAsState()
    val playbackError by player.playbackError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val progressStyleKey by store.progressStyle.collectAsState(initial = ProgressStyle.SLIDER.key)
    val progressStyle = ProgressStyle.fromKey(progressStyleKey)

    val favorites by store.favorites.collectAsState(initial = emptyList())
    val isFav = song?.let { s -> favorites.any { it.id == s.id } } ?: false

    var showQueue by remember { mutableStateOf(false) }
    var queueMenuIndex by remember { mutableStateOf<Int?>(null) }

    val resolving by player.resolvingCurrent.collectAsState()
    val speed by player.speed.collectAsState()
    val sleepRemaining by player.sleepTimerRemaining.collectAsState()

    LaunchedEffect(song?.id) { song?.id?.let { vm.loadLyric(it) } }

    var speedMenu by remember { mutableStateOf(false) }
    var timerMenu by remember { mutableStateOf(false) }
    var eqOpen by remember { mutableStateOf(false) }
    var actionsSong by remember { mutableStateOf<Song?>(null) }

    // Present playback failures (VIP / copyright / network) once per new error.
    // The action offered depends on the failure: retryable → 重试, otherwise → 下一首.
    LaunchedEffect(playbackError?.songId, playbackError?.retryable) {
        val err = playbackError ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = err.message,
            actionLabel = if (err.retryable) "重试" else "下一首",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            if (err.retryable) player.retryCurrentSong() else player.playNext()
        }
        player.clearPlaybackError()
    }

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
        // SPICaMusic style: blurred cover as the page background with a scrim.
        val coverUrl = song?.coverUrl.orEmpty()
        if (coverUrl.startsWith("http")) {
            ArtworkImage(
                url = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(48.dp)
                    .scale(1.15f)
                    .alpha(0.38f)
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        }
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Top bar (does NOT read position -> stable unless song/playing changes)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song?.name ?: "未在播放",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1
                    )
                    Text(
                        song?.artistText ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(if (isFav) "取消收藏" else "收藏") } },
                    state = rememberTooltipState()
                ) {
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
                            tint = if (isFav) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text("播放队列") } },
                    state = rememberTooltipState()
                ) {
                    FilledTonalIconButton(onClick = { showQueue = true }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "队列")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Cover (stable; recomposes on song change only)
            val currentSongForCover = song
            if (currentSongForCover != null) {
                ArtworkImage(
                    url = currentSongForCover.coverUrl,
                    contentDescription = currentSongForCover.name,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .align(Alignment.CenterHorizontally)
                )
                // 播放地址解析中 — subtle inline wavy progress until the URL is hot-swapped in.
                if (resolving) {
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .align(Alignment.CenterHorizontally)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Lyrics — isolated composable that reads position; only this subtree recomposes per second.
            LyricsSection(
                lyricState = lyricState,
                positionFlow = player.positionMs,
                onRetryLyric = { song?.id?.let { vm.loadLyric(it) } },
                onSeek = player::seekTo,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Progress slider — isolated; only this recomposes with position.
            ProgressSection(player = player, style = progressStyle, songId = song?.id ?: 0L)

            Spacer(Modifier.height(10.dp))

            // M3E 浮动工具栏：倍速 / 定时 / 音效（自 v1.6 起从顶栏迁移至此）。
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                // 倍速：chip 弹出档位选择，全局生效并持久化
                Box {
                    TextButton(onClick = { speedMenu = true }) {
                        Text(
                            speedLabel(speed),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        player.speedOptions().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(speedLabel(option)) },
                                onClick = {
                                    player.setSpeed(option)
                                    speedMenu = false
                                },
                                trailingIcon = {
                                    if (option == speed) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                }
                            )
                        }
                    }
                }
                // 定时关闭：激活时图标高亮并在旁边显示剩余时间
                Box {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("定时关闭") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = { timerMenu = true }) {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = "定时关闭",
                                tint = if (sleepRemaining != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DropdownMenu(expanded = timerMenu, onDismissRequest = { timerMenu = false }) {
                        listOf(15, 30, 45, 60, 90).forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text("$minutes 分钟") },
                                onClick = {
                                    player.startSleepTimer(minutes)
                                    timerMenu = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("当前曲目结束") },
                            onClick = {
                                player.startSleepTimerToEndOfTrack()
                                timerMenu = false
                            }
                        )
                        if (sleepRemaining != null) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("取消定时") },
                                onClick = {
                                    player.cancelSleepTimer()
                                    timerMenu = false
                                }
                            )
                        }
                    }
                }
                if (sleepRemaining != null) {
                    Text(
                        formatSleepRemaining(sleepRemaining),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                // 音效：均衡器预置面板
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text("音效") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = { eqOpen = true }) {
                        Icon(Icons.Filled.Equalizer, contentDescription = "音效")
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Controls (stable; reads isPlaying + playMode only)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Each control sits in an equal-width, centered slot so the row never drifts.
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(playModeDesc) } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = { player.cyclePlayMode() }) {
                            Icon(playModeIcon, contentDescription = playModeDesc, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("上一首") } },
                        state = rememberTooltipState()
                    ) {
                        FilledTonalIconButton(onClick = { player.playPrevious() }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(28.dp))
                        }
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularPlayButton(
                        player = player,
                        isPlaying = isPlaying,
                        style = progressStyle
                    )
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("下一首") } },
                        state = rememberTooltipState()
                    ) {
                        FilledTonalIconButton(onClick = { player.playNext() }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "下一首", modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // M3E 底部弹层播放队列
    if (showQueue) {
        ModalBottomSheet(onDismissRequest = {
            showQueue = false
            queueMenuIndex = null
        }) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "播放队列",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(enabled = queue.isNotEmpty(), onClick = { player.clearQueue() }) {
                        Text("清空")
                    }
                }
                if (queue.isNotEmpty()) {
                    Text(
                        "${queueStats.count} 首 · 总时长 ${formatQueueDuration(queueStats.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (queue.isEmpty()) {
                    Text(
                        "队列为空 — 从列表中选择歌曲开始播放",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        textAlign = TextAlign.Center
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                ) {
                    itemsIndexed(queue) { index, s ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SongRow(
                                song = s, index = index,
                                isActive = s.id == song?.id,
                                isPlaying = s.id == song?.id && isPlaying,
                                onClick = {
                                    player.playAt(index)
                                    showQueue = false
                                },
                                onLongClick = { actionsSong = s },
                                modifier = Modifier.weight(1f)
                            )
                            Column {
                                if (s.id == song?.id) {
                                    Text(
                                        if (isPlaying) "正在播放" else "当前",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    enabled = index > 0,
                                    onClick = { player.moveQueueItem(index, index - 1) }
                                ) { Icon(Icons.Filled.ArrowDropUp, contentDescription = "上移") }
                                IconButton(
                                    enabled = index < queue.lastIndex,
                                    onClick = { player.moveQueueItem(index, index + 1) }
                                ) { Icon(Icons.Filled.ArrowDropDown, contentDescription = "下移") }
                            }
                            Box {
                                IconButton(onClick = { queueMenuIndex = index }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                                }
                                DropdownMenu(
                                    expanded = queueMenuIndex == index,
                                    onDismissRequest = { queueMenuIndex = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("移到顶部") },
                                        enabled = index > 0,
                                        onClick = {
                                            player.moveToQueueTop(index)
                                            queueMenuIndex = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("移到底部") },
                                        enabled = index < queue.lastIndex,
                                        onClick = {
                                            player.moveToQueueBottom(index)
                                            queueMenuIndex = null
                                        }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("从队列移除") },
                                        onClick = {
                                            player.removeFromQueue(index)
                                            queueMenuIndex = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // v1.5 歌曲操作弹层（播放/下一首/入队/收藏/下载）
    SongActionsSheet(song = actionsSong, onDismiss = { actionsSong = null })

    // v1.5 均衡器面板
    if (eqOpen) {
        EqualizerSheet(
            store = store,
            equalizer = equalizer,
            onDismiss = { eqOpen = false }
        )
    }
}

/** 顶栏速度 chip 文案：1.0x / 0.75x / 2.0x … */
private fun speedLabel(speed: Float): String {
    val s = speed.coerceIn(0.5f, 2f)
    return if (s % 1f == 0f) "%.0fx".format(s) else "%.2fx".format(s).trimEnd('0').trimEnd('.') + "x"
}

/** 队列总时长文案：HH:MM:SS。 */
private fun formatQueueDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

/** 定时关闭剩余时间 mm:ss（或 hh:mm:ss）。 */
private fun formatSleepRemaining(ms: Long?): String {
    if (ms == null) return ""
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * 音效面板：均衡器（开关 + 预置 chips + 自定义频段滑杆）、重低音、环绕声、淡入淡出。
 * 读 [LocalEqualizerController] 的可用性与频段；写入 [LocalStore] 即时应用到播放会话。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun EqualizerSheet(
    store: com.hh.music.player.data.local.LocalStore,
    equalizer: com.hh.music.player.playback.EqualizerController,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val eqEnabled by store.equalizerEnabled.collectAsState(initial = false)
    val presetKey by store.equalizerPreset.collectAsState(initial = EqualizerPresets.DEFAULT)
    val bandsString by store.equalizerBands.collectAsState(initial = "")
    val available by equalizer.isAvailable.collectAsState()
    val bandFreqs by equalizer.bandFreqs.collectAsState()
    val bassAvailable by equalizer.bassAvailable.collectAsState()
    val virtualizerAvailable by equalizer.virtualizerAvailable.collectAsState()
    val bassOn by store.bassBoostEnabled.collectAsState(initial = false)
    val bassStrength by store.bassBoostStrength.collectAsState(initial = 500)
    val virtOn by store.virtualizerEnabled.collectAsState(initial = false)
    val virtStrength by store.virtualizerStrength.collectAsState(initial = 500)
    val fadeSec by store.fadeDurationSec.collectAsState(initial = 0)

    val customBands = remember(bandsString) { EqualizerPresets.parseBands(bandsString) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("音效", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    if (available) "已连接音频会话" else "当前设备不支持",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (available) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error
                )
                Switch(
                    checked = eqEnabled && available,
                    onCheckedChange = { scope.launch { store.setEqualizerEnabled(it && available) } },
                    enabled = available
                )
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EqualizerPresets.PRESETS.forEach { key ->
                    FilterChip(
                        selected = presetKey == key,
                        enabled = available && eqEnabled,
                        onClick = {
                            scope.launch {
                                store.setEqualizerPreset(key)
                                if (key == EqualizerPresets.CUSTOM && customBands.isEmpty()) {
                                    // 用当前设备频段数初始化一条平直曲线
                                    val count = equalizer.bandCount.value.takeIf { it > 0 } ?: 10
                                    store.setEqualizerBands(EqualizerPresets.serializeBands(List(count) { 0 }))
                                }
                            }
                        },
                        label = { Text(EqualizerPresets.displayName(key)) }
                    )
                }
            }
            if (available && eqEnabled && presetKey == EqualizerPresets.CUSTOM && bandFreqs.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "自定义频段（毫贝）",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                bandFreqs.forEachIndexed { i, freqHz ->
                    val value = customBands.getOrElse(i) { 0 }
                    val freqLabel = if (freqHz >= 1000) "${freqHz / 1000}k" else "$freqHz"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            freqLabel,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(44.dp)
                        )
                        Slider(
                            value = value.toFloat().coerceIn(-1500f, 1500f),
                            onValueChange = { newValue ->
                                val updated = customBands.toMutableList()
                                while (updated.size <= i) updated.add(0)
                                updated[i] = newValue.toInt()
                                scope.launch { store.setEqualizerBands(EqualizerPresets.serializeBands(updated)) }
                            },
                            valueRange = -1500f..1500f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${value / 10} dB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(48.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            // ---- v1.7 重低音 ----
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("重低音", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (bassAvailable) "增强低频力度" else "当前设备不支持",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (bassAvailable) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error
                    )
                }
                Switch(
                    checked = bassOn && bassAvailable,
                    onCheckedChange = { scope.launch { store.setBassBoostEnabled(it && bassAvailable) } },
                    enabled = bassAvailable
                )
            }
            var localBass by remember(bassStrength) { mutableStateOf(bassStrength.toFloat()) }
            Slider(
                value = localBass.coerceIn(0f, 1000f),
                onValueChange = { localBass = it },
                onValueChangeFinished = { scope.launch { store.setBassBoostStrength(localBass.toInt()) } },
                enabled = bassAvailable && bassOn,
                valueRange = 0f..1000f
            )

            // ---- v1.7 环绕声 ----
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("环绕声", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (virtualizerAvailable) "虚拟化空间声场" else "当前设备不支持",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (virtualizerAvailable) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error
                    )
                }
                Switch(
                    checked = virtOn && virtualizerAvailable,
                    onCheckedChange = { scope.launch { store.setVirtualizerEnabled(it && virtualizerAvailable) } },
                    enabled = virtualizerAvailable
                )
            }
            var localVirt by remember(virtStrength) { mutableStateOf(virtStrength.toFloat()) }
            Slider(
                value = localVirt.coerceIn(0f, 1000f),
                onValueChange = { localVirt = it },
                onValueChangeFinished = { scope.launch { store.setVirtualizerStrength(localVirt.toInt()) } },
                enabled = virtualizerAvailable && virtOn,
                valueRange = 0f..1000f
            )

            // ---- v1.7 淡入淡出 ----
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("歌曲淡入淡出", style = MaterialTheme.typography.titleSmall)
            Text(
                "曲目自然衔接时渐弱/渐强；手动切歌不受影响",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlaybackEngine.FADE_OPTIONS_SEC.forEach { sec ->
                    FilterChip(
                        selected = fadeSec == sec,
                        onClick = { scope.launch { store.setFadeDurationSec(sec) } },
                        label = { Text(if (sec == 0) "关" else "${sec}秒") }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
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
    onRetryLyric: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val lyricList = lyricState.lines
    val position by positionFlow.collectAsState()
    val store = LocalStoreProvider.current
    val scope = rememberCoroutineScope()
    val showTranslation by store.showLyricTranslation.collectAsState(initial = true)
    val showRomanization by store.showLyricRomanization.collectAsState(initial = false)
    val fontScaleKey by store.lyricFontScale.collectAsState(initial = LyricFontScale.MEDIUM.key)
    val fontScale = LyricFontScale.from(fontScaleKey)
    val lineStyle = when (fontScale) {
        LyricFontScale.SMALL -> MaterialTheme.typography.bodyMedium
        LyricFontScale.MEDIUM -> MaterialTheme.typography.bodyLarge
        LyricFontScale.LARGE -> MaterialTheme.typography.titleMedium
    }
    val secondaryStyle = when (fontScale) {
        LyricFontScale.SMALL -> MaterialTheme.typography.bodySmall
        LyricFontScale.MEDIUM -> MaterialTheme.typography.bodySmall
        LyricFontScale.LARGE -> MaterialTheme.typography.bodyMedium
    }
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
        when {
            lyricList.isNotEmpty() -> Column(Modifier.fillMaxSize()) {
                LyricOptionsRow(
                    showTranslation = showTranslation,
                    showRomanization = showRomanization,
                    fontScale = fontScale,
                    onToggleTranslation = {
                        scope.launch { store.setShowLyricTranslation(!showTranslation) }
                    },
                    onToggleRomanization = {
                        scope.launch { store.setShowLyricRomanization(!showRomanization) }
                    },
                    onFontScale = { font ->
                        scope.launch { store.setLyricFontScale(font.key) }
                    }
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 20.dp)
                ) {
                    itemsIndexed(lyricList) { index, line ->
                        val active = index == activeIndex
                        Text(
                            text = line.text.ifBlank { "♪" },
                            style = lineStyle,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeek(line.timeMs) }
                                .padding(vertical = 5.dp)
                        )
                        if (showRomanization) {
                            lyricState.romanizations[line.timeMs]?.let { roma ->
                                Text(
                                    roma,
                                    style = secondaryStyle,
                                    color = if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                                )
                            }
                        }
                        if (showTranslation) {
                            lyricState.translations[line.timeMs]?.let { trans ->
                                Text(
                                    trans,
                                    style = secondaryStyle,
                                    color = if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            lyricState.error -> TextButton(
                onClick = onRetryLyric,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text("歌词加载失败，点击重试")
            }

            else -> Text(
                if (lyricState.loading) "歌词加载中..." else "暂无歌词",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricOptionsRow(
    showTranslation: Boolean,
    showRomanization: Boolean,
    fontScale: LyricFontScale,
    onToggleTranslation: () -> Unit,
    onToggleRomanization: () -> Unit,
    onFontScale: (LyricFontScale) -> Unit
) {
    var fontMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(if (showTranslation) "隐藏翻译" else "显示翻译") } },
            state = rememberTooltipState()
        ) {
            IconButton(onClick = onToggleTranslation) {
                Icon(
                    Icons.Filled.Translate,
                    contentDescription = "翻译",
                    tint = if (showTranslation) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(if (showRomanization) "隐藏罗马音" else "显示罗马音") } },
            state = rememberTooltipState()
        ) {
            IconButton(onClick = onToggleRomanization) {
                Icon(
                    Icons.Filled.Subtitles,
                    contentDescription = "罗马音",
                    tint = if (showRomanization) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("歌词字号") } },
                state = rememberTooltipState()
            ) {
                IconButton(onClick = { fontMenu = true }) {
                    Icon(
                        Icons.Filled.FormatSize,
                        contentDescription = "歌词字号",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DropdownMenu(expanded = fontMenu, onDismissRequest = { fontMenu = false }) {
                LyricFontScale.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text("${option.label}字号") },
                        onClick = {
                            onFontScale(option)
                            fontMenu = false
                        },
                        trailingIcon = {
                            if (option == fontScale) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        }
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "${fontScale.label}字号",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Isolated progress slider — recomposes per second without touching the rest of the player. */
@Composable
private fun ProgressSection(
    player: com.hh.music.player.playback.PlayerController,
    style: ProgressStyle,
    songId: Long
) {
    val position by player.positionMs.collectAsState()
    val duration by player.durationMs.collectAsState()
    var seekValue by remember { mutableStateOf<Float?>(null) }
    val sliderPos = seekValue ?: (if (duration > 0) position.toFloat() / duration else 0f)
    Column(Modifier.fillMaxWidth()) {
        val fraction = sliderPos.coerceIn(0f, 1f)
        when (style) {
            ProgressStyle.SLIDER -> Slider(
                value = fraction,
                onValueChange = { seekValue = it },
                onValueChangeFinished = {
                    seekValue?.let { player.seekTo((it * duration).toLong()) }
                    seekValue = null
                },
                modifier = Modifier.fillMaxWidth()
            )
            ProgressStyle.LINEAR -> LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round
            )
            ProgressStyle.CIRCULAR -> Unit
            ProgressStyle.WAVEFORM -> WaveformSlider(
                progress = fraction,
                onProgressChange = { seekValue = it },
                onProgressChangeFinished = {
                    seekValue?.let { player.seekTo((it * duration).toLong()) }
                    seekValue = null
                },
                amplitudes = rememberWaveformAmplitudes(songId),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(position), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDuration(duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 播放/暂停按钮：环形样式时套一圈可拖动的 M3E CircularProgressIndicator。
 * 独立成 composable，避免位置每秒刷新影响整行控制区。
 */
@Composable
private fun CircularPlayButton(
    player: com.hh.music.player.playback.PlayerController,
    isPlaying: Boolean,
    style: ProgressStyle
) {
    if (style != ProgressStyle.CIRCULAR) {
        FilledIconButton(
            onClick = { player.togglePlayPause() },
            modifier = Modifier.size(68.dp)
        ) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(38.dp)
            )
        }
        return
    }

    val position by player.positionMs.collectAsState()
    val duration by player.durationMs.collectAsState()
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Box(
        modifier = Modifier.size(88.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeWidth = 4.dp,
            strokeCap = StrokeCap.Round
        )
        FilledIconButton(
            onClick = { player.togglePlayPause() },
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(34.dp)
            )
        }
    }
}
