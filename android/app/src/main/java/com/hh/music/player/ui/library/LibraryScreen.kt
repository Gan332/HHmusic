package com.hh.music.player.ui.library

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.SavedPlaylist
import com.hh.music.player.data.Song
import com.hh.music.player.data.local.LocalMusic
import com.hh.music.player.data.local.LocalStore
import com.hh.music.player.data.offline.DownloadEntry
import com.hh.music.player.data.offline.DownloadManager
import com.hh.music.player.data.offline.DownloadState
import com.hh.music.player.data.offline.OfflineCache
import com.hh.music.player.playback.PlayerController
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalStoreProvider
import com.hh.music.player.ui.LocalDownloadManager
import com.hh.music.player.ui.components.ArtworkImage
import com.hh.music.player.ui.components.EmptyState
import com.hh.music.player.ui.components.MiniPlayerBar
import com.hh.music.player.ui.components.SongActionsSheet
import com.hh.music.player.ui.components.SongRow
import com.hh.music.player.ui.components.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LibraryTab(val label: String) {
    SONGS("歌曲"),
    RECENT("最近"),
    PLAYLISTS("歌单"),
    LOCAL("本地"),
    DOWNLOADS("下载")
}

/**
 * 音乐库 — 收藏歌曲 + 最近播放 + 收藏歌单 + 本地音乐。
 * 长按收藏歌曲进入批量管理模式（勾选、批量播放/移除）；有移除按钮的长按菜单随时可用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenPlaylist: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenDiscover: () -> Unit = {},
    store: LocalStore = LocalStoreProvider.current,
    vm: LibraryViewModel = viewModel { LibraryViewModel(store) }
) {
    val favorites by vm.favorites.collectAsState()
    val recent by vm.recent.collectAsState()
    val savedPlaylists by vm.savedPlaylists.collectAsState()
    val player = LocalPlayerController.current
    val downloadManager = LocalDownloadManager.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    var tab by remember { mutableStateOf(LibraryTab.SONGS) }
    var showClearRecentDialog by remember { mutableStateOf(false) }
    var actionsSong by remember { mutableStateOf<Song?>(null) }

    // 收藏歌曲批量管理（长按进入、勾选、批量播放/移除）。
    var managing by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    fun exitManage() {
        managing = false
        selectedIds = emptySet()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (managing) "已选 ${selectedIds.size} 首" else "音乐库",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    if (managing) {
                        TextButton(onClick = {
                            selectedIds =
                                if (selectedIds.size == favorites.size) emptySet()
                                else favorites.map { it.id }.toSet()
                        }) { Text(if (selectedIds.size == favorites.size) "取消全选" else "全选") }
                        TextButton(onClick = ::exitManage) { Text("完成") }
                    } else if (tab == LibraryTab.SONGS && favorites.isNotEmpty()) {
                        TextButton(onClick = { player.playQueue(favorites, 0) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("全部播放")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    LibraryTab.entries.forEachIndexed { index, t ->
                        SegmentedButton(
                            selected = tab == t,
                            onClick = {
                                if (managing) exitManage()
                                tab = t
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, LibraryTab.entries.size)
                        ) { Text(t.label) }
                    }
                }
            }
        },
        bottomBar = {
            if (tab == LibraryTab.SONGS && managing && selectedIds.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val selectedSongs = favorites.filter { it.id in selectedIds }
                        FilledTonalButton(
                            onClick = { player.playQueue(selectedSongs, 0) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("播放选中")
                        }
                        FilledTonalButton(
                            onClick = {
                                selectedIds.forEach { vm.removeFavorite(it) }
                                exitManage()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("移除选中")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                LibraryTab.SONGS -> when {
                    managing -> ManagingSongList(
                        songs = favorites,
                        selectedIds = selectedIds,
                        onToggleSelect = { id ->
                            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                        }
                    )
                    favorites.isEmpty() -> EmptyState(
                        hint = "还没有收藏的歌曲，去播放页点 ♥ 吧",
                        icon = Icons.Filled.Favorite,
                        actionText = "去首页发现音乐",
                        onAction = onOpenDiscover
                    )
                    else -> SongListPane(
                        songs = favorites,
                        emptyHint = "",
                        emptyIcon = Icons.Filled.Favorite,
                        currentSongId = currentSong?.id,
                        isPlaying = isPlaying,
                        onPlay = { index -> player.playQueue(favorites, index) },
                        onLong = { id ->
                            managing = true
                            selectedIds = setOf(id)
                        }
                    )
                }
                LibraryTab.RECENT -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "最近播放（${recent.size}）",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(enabled = recent.isNotEmpty(), onClick = { showClearRecentDialog = true }) { Text("清空") }
                    }
                    if (recent.isEmpty()) {
                        EmptyState(
                            hint = "暂无播放记录",
                            icon = Icons.Filled.History,
                            actionText = "去首页发现音乐",
                            onAction = onOpenDiscover
                        )
                    } else {
                        SongListPane(
                            songs = recent,
                            emptyHint = "暂无播放记录",
                            emptyIcon = Icons.Filled.History,
                            currentSongId = currentSong?.id,
                            isPlaying = isPlaying,
                            onPlay = { index -> player.playQueue(recent, index) },
                            onRemove = { song -> vm.removeRecent(song.id) },
                            onLong = { id -> actionsSong = recent.firstOrNull { it.id == id } }
                        )
                    }
                }
                LibraryTab.PLAYLISTS -> PlaylistPane(
                    playlists = savedPlaylists,
                    emptyHint = "还没有收藏的歌单",
                    onOpen = onOpenPlaylist,
                    onRemove = { vm.removeSavedPlaylist(it.id) }
                )
                LibraryTab.LOCAL -> LocalMusicPane(
                    store = store,
                    player = player,
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    onLongPress = { song -> actionsSong = song }
                )
                LibraryTab.DOWNLOADS -> DownloadsPane(
                    downloadManager = downloadManager,
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    onPlaySong = { song -> player.playQueue(listOf(song)) },
                    onLongPress = { song -> actionsSong = song }
                )
            }
            MiniPlayerBar(player = player, onClick = onOpenPlayer, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    SongActionsSheet(song = actionsSong, onDismiss = { actionsSong = null })

    if (showClearRecentDialog) {
        AlertDialog(
            onDismissRequest = { showClearRecentDialog = false },
            title = { Text("清空最近播放？") },
            text = { Text("将删除全部播放记录，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearRecent()
                    showClearRecentDialog = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearRecentDialog = false }) { Text("取消") }
            }
        )
    }
}

/** 收藏/最近播放列表：常规行 + 可选右侧移除按钮；长按进入批量管理。 */
@Composable
private fun SongListPane(
    songs: List<Song>,
    emptyHint: String,
    emptyIcon: ImageVector,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlay: (Int) -> Unit,
    onRemove: ((Song) -> Unit)? = null,
    onLong: ((Long) -> Unit)? = null
) {
    if (songs.isEmpty()) {
        EmptyState(hint = emptyHint, icon = emptyIcon)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(songs) { index, song ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SongRow(
                    song = song, index = index,
                    isActive = song.id == currentSongId,
                    isPlaying = song.id == currentSongId && isPlaying,
                    onClick = { onPlay(index) },
                    onLongClick = onLong?.let { { it(song.id) } },
                    modifier = Modifier.weight(1f)
                )
                if (onRemove != null) {
                    IconButton(onClick = { onRemove(song) }) {
                        Icon(Icons.Filled.Close, contentDescription = "移除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

/**
 * 下载页签：已下载文件（点选即播、删除）、失败记录（原因 + 重试）、
 * 下载中进度、总占用 / 容量上限、清空缓存。
 */
@Composable
private fun DownloadsPane(
    downloadManager: DownloadManager,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlaySong: (Song) -> Unit,
    onLongPress: (Song) -> Unit
) {
    val store = LocalStoreProvider.current
    val entries by downloadManager.entries.collectAsState(initial = emptyList())
    val statuses by downloadManager.statuses.collectAsState(initial = emptyMap())
    val capMb by store.cacheCapMb.collectAsState(initial = OfflineCache.DEFAULT_CAP_MB.toInt())
    val okEntries = entries.filter { !it.isFailed }
    val downloading = statuses.filterValues { it.state == DownloadState.DOWNLOADING }
    val downloadingIds = downloading.keys
    val failed = entries.filter { it.isFailed && it.id !in downloadingIds }
    val totalUsage = downloadManager.totalBytes()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("离线缓存", style = MaterialTheme.typography.titleMedium)
                Text(
                    "已用 ${OfflineCache.formatBytes(totalUsage)} / 上限 ${OfflineCache.formatBytes(OfflineCache.capBytes(capMb.toLong()))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (failed.isNotEmpty()) {
                TextButton(
                    onClick = { downloadManager.clearFailed() }
                ) {
                    Text("清空失败")
                }
            }
            TextButton(
                enabled = entries.isNotEmpty() || downloading.isNotEmpty(),
                onClick = {
                    downloadManager.clear()
                }
            ) {
                Text("清空")
            }
        }

        if (entries.isEmpty() && downloading.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    hint = "还没有下载，播放过的歌曲会自动缓存（可离线播放）",
                    icon = Icons.Filled.Download,
                    actionText = "去首页发现音乐"
                )
            }
            return
        }

        LazyColumn(Modifier.fillMaxSize()) {
            // 下载中的歌曲
            downloading.forEach { (songId, st) ->
                val song = st.song ?: entries.firstOrNull { it.id == songId }?.song
                if (song == null) return@forEach
                item(key = "downloading_$songId") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArtworkImage(
                            url = song.coverUrl,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.medium)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            LinearProgressIndicator(
                                progress = { st.progress / 100f },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("${st.progress}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                }
            }

            // 已下载完成的歌曲
            itemsIndexed(okEntries) { index, entry ->
                val e = entry
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SongRow(
                        song = e.song,
                        index = index,
                        isActive = e.song.id == currentSongId,
                        isPlaying = e.song.id == currentSongId && isPlaying,
                        onClick = { onPlaySong(e.song) },
                        onLongClick = { onLongPress(e.song) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { downloadManager.remove(e.song.id) }) {
                        Icon(Icons.Filled.Close, contentDescription = "删除下载", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            }

            // 失败记录（保留原因，可重试或移除）
            failed.forEach { entry ->
                item(key = "failed_${entry.song.id}") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArtworkImage(
                            url = entry.song.coverUrl,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.medium)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.song.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "下载失败：${entry.error ?: "未知原因"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = { downloadManager.download(entry.song) }) { Text("重试") }
                        IconButton(onClick = { downloadManager.remove(entry.song.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

/** 批量管理模式下的收藏列表：点选勾选，底栏执行批量操作。 */
@Composable
private fun ManagingSongList(
    songs: List<Song>,
    selectedIds: Set<Long>,
    onToggleSelect: (Long) -> Unit
) {
    if (songs.isEmpty()) {
        EmptyState(hint = "还没有收藏的歌曲", icon = Icons.Filled.Favorite)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(songs) { index, song ->
            val selected = song.id in selectedIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { onToggleSelect(song.id) })
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SongRow(
                    song = song, index = index,
                    onClick = { onToggleSelect(song.id) },
                    modifier = Modifier.weight(1f)
                )
                Checkbox(checked = selected, onCheckedChange = { onToggleSelect(song.id) })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

/** 收藏歌单：点按打开，更多菜单支持取消收藏。 */
@Composable
private fun PlaylistPane(
    playlists: List<SavedPlaylist>,
    emptyHint: String,
    onOpen: (Long) -> Unit,
    onRemove: (SavedPlaylist) -> Unit
) {
    if (playlists.isEmpty()) {
        EmptyState(hint = emptyHint, icon = Icons.Filled.QueueMusic)
        return
    }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(playlists) { _, pl ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(pl.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkImage(
                    url = pl.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.medium)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(pl.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (pl.creator.isNotBlank()) pl.creator else "未知创建者", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menuFor = pl.id }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menuFor == pl.id, onDismissRequest = { menuFor = null }) {
                        DropdownMenuItem(
                            text = { Text("打开歌单") },
                            onClick = { menuFor = null; onOpen(pl.id) }
                        )
                        DropdownMenuItem(
                            text = { Text("取消收藏") },
                            onClick = { menuFor = null; onRemove(pl) }
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

/**
 * 本地音乐页签：MediaStore 扫描（需权限）+ SAF 导入文件（免权限）。
 * 无音频权限时 SAF 导入的文件依然展示；失效 URI 自动从导入记录中清理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalMusicPane(
    store: LocalStore,
    player: PlayerController,
    currentSongId: Long?,
    isPlaying: Boolean,
    onLongPress: (Song) -> Unit = {}
) {
    val context = LocalContext.current
    val permission = LocalMusic.audioPermission()
    var permissionGranted by remember { mutableStateOf(LocalMusic.hasAudioPermission(context)) }
    val importedUris by store.importedUris.collectAsState(initial = emptyList())
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var scanTrigger by remember { mutableStateOf(0) }
    var filterQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val filteredSongs = remember(songs, filterQuery) {
        LocalMusic.filterByQuery(songs, filterQuery)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEach { uri ->
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
                store.addImportedUris(uris.map { it.toString() })
            }
        }
    }

    LaunchedEffect(permissionGranted, importedUris, scanTrigger) {
        scanning = true
        songs = withContext(Dispatchers.IO) {
            // SAF 导入的文件无需权限：无论是否授权都展示；有权限时额外扫描 MediaStore。
            val scanned = if (permissionGranted) LocalMusic.scanDeviceMusic(context) else emptyList()
            val entries = importedUris.map { uri -> LocalMusic.songFromUri(context, uri) }
            val staleUris = importedUris.filterIndexed { i, _ -> entries[i] == null }
            if (staleUris.isNotEmpty()) {
                scope.launch { staleUris.forEach { store.removeImportedUri(it) } }
            }
            // 合并 + 按 localUri 去重（MediaStore 与 SAF 的 id 空间不同，不能只按 id 去重）。
            LocalMusic.merge(scanned, entries)
        }
        scanning = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("本地音乐", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                if (!scanning && (permissionGranted || songs.isNotEmpty())) {
                    Text(
                        "共 ${songs.size} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("重新扫描") } },
                state = rememberTooltipState()
            ) {
                IconButton(onClick = { scanTrigger++ }) {
                    Icon(Icons.Filled.Check, contentDescription = "重新扫描")
                }
            }
            FilledTonalButton(onClick = { importLauncher.launch(arrayOf("audio/*")) }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("导入")
            }
        }
        if (songs.isNotEmpty()) {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("筛选标题或歌手") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (filterQuery.isNotEmpty()) {
                        IconButton(onClick = { filterQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空筛选")
                        }
                    }
                },
                singleLine = true
            )
        }
        when {
            scanning && songs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("扫描中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            songs.isEmpty() && !permissionGranted && importedUris.isEmpty() -> LocalPermissionCard(
                onGrant = { permissionLauncher.launch(permission) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                }
            )
            songs.isEmpty() -> EmptyState(
                hint = "没有找到本地音乐，点右上角「导入」选择音频",
                icon = Icons.Filled.LibraryMusic
            )
            else -> if (filteredSongs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        hint = "没有匹配的本地音乐",
                        icon = Icons.Filled.Search
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(filteredSongs) { index, song ->
                        SongRow(
                            song = song,
                            index = index,
                            isActive = song.id == currentSongId,
                            isPlaying = song.id == currentSongId && isPlaying,
                            onClick = {
                                if (filteredSongs.isNotEmpty()) player.playQueue(filteredSongs, index)
                            },
                            onLongClick = { onLongPress(song) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LocalPermissionCard(onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(56.dp).clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.LibraryMusic, contentDescription = null)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "需要音频权限",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "授权后可扫描设备中的本地音乐；也可以直接「导入文件」选择音频，无需权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onGrant) { Text("授予权限") }
                    OutlinedButton(onClick = onOpenSettings) { Text("去设置") }
                }
            }
        }
    }
}
