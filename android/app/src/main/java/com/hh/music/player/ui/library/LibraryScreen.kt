package com.hh.music.player.ui.library

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hh.music.player.data.SavedPlaylist
import com.hh.music.player.data.Song
import com.hh.music.player.data.local.LocalMusic
import com.hh.music.player.data.local.LocalStore
import com.hh.music.player.playback.PlayerController
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalStoreProvider
import com.hh.music.player.ui.components.MiniPlayerBar
import com.hh.music.player.ui.components.SongRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LibraryTab(val label: String) {
    SONGS("歌曲"),
    RECENT("最近"),
    PLAYLISTS("歌单"),
    LOCAL("本地")
}

/**
 * 音乐库 — 本地个人内容的统一浏览入口：
 * 收藏歌曲 + 最近播放 + 收藏歌单，M3E 分段按钮切换 + 点选即播/即开。
 */
@Composable
fun LibraryScreen(
    onOpenPlaylist: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
    store: LocalStore = LocalStoreProvider.current,
    vm: LibraryViewModel = viewModel { LibraryViewModel(store) }
) {
    val favorites by vm.favorites.collectAsState()
    val recent by vm.recent.collectAsState()
    val savedPlaylists by vm.savedPlaylists.collectAsState()
    val player = LocalPlayerController.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    var tab by remember { mutableStateOf(LibraryTab.SONGS) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 8.dp)) {
                Text(
                    "音乐库",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    LibraryTab.entries.forEachIndexed { index, t ->
                        SegmentedButton(
                            selected = tab == t,
                            onClick = { tab = t },
                            shape = SegmentedButtonDefaults.itemShape(index, LibraryTab.entries.size)
                        ) {
                            Text(t.label)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                LibraryTab.SONGS -> SongListPane(
                    songs = favorites,
                    emptyHint = "还没有收藏的歌曲，去播放页点 ♥ 吧",
                    emptyIcon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    onPlay = { index -> if (favorites.isNotEmpty()) player.playQueue(favorites, index) }
                )
                LibraryTab.RECENT -> SongListPane(
                    songs = recent,
                    emptyHint = "暂无播放记录",
                    emptyIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    onPlay = { index -> if (recent.isNotEmpty()) player.playQueue(recent, index) }
                )
                LibraryTab.PLAYLISTS -> PlaylistPane(
                    playlists = savedPlaylists,
                    emptyHint = "还没有收藏的歌单",
                    onOpen = onOpenPlaylist
                )
                LibraryTab.LOCAL -> LocalMusicPane(
                    store = store,
                    player = player,
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying
                )
            }
            MiniPlayerBar(player = player, onClick = onOpenPlayer, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun SongListPane(
    songs: List<Song>,
    emptyHint: String,
    emptyIcon: @Composable () -> Unit,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlay: (Int) -> Unit
) {
    if (songs.isEmpty()) {
        EmptyPane(emptyHint, emptyIcon)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(songs) { index, song ->
            SongRow(
                song = song, index = index,
                isActive = song.id == currentSongId,
                isPlaying = song.id == currentSongId && isPlaying,
                onClick = { onPlay(index) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun PlaylistPane(
    playlists: List<SavedPlaylist>,
    emptyHint: String,
    onOpen: (Long) -> Unit
) {
    if (playlists.isEmpty()) {
        EmptyPane(emptyHint) { Icon(Icons.Filled.QueueMusic, contentDescription = null) }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(playlists) { _, pl ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(pl.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(56.dp).clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    if (pl.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = pl.coverUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        pl.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (pl.creator.isNotBlank()) pl.creator else "未知创建者",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun EmptyPane(hint: String, icon: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(72.dp).clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(32.dp)) { icon() }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/**
 * 本地音乐页签：MediaStore 扫描（需权限）+ SAF 导入文件（免权限），
 * 参考 SPICaMusic_Android 的扫描/导入交互。
 */
@Composable
private fun LocalMusicPane(
    store: LocalStore,
    player: PlayerController,
    currentSongId: Long?,
    isPlaying: Boolean
) {
    val context = LocalContext.current
    val permission = LocalMusic.audioPermission()
    var permissionGranted by remember { mutableStateOf(LocalMusic.hasAudioPermission(context)) }
    val importedUris by store.importedUris.collectAsState(initial = emptyList())
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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

    LaunchedEffect(permissionGranted, importedUris) {
        if (!permissionGranted) return@LaunchedEffect
        scanning = true
        songs = withContext(Dispatchers.IO) {
            val scanned = LocalMusic.scanDeviceMusic(context)
            val imported = importedUris.mapNotNull { LocalMusic.songFromUri(context, it) }
            (scanned + imported).distinctBy { it.id }
        }
        scanning = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "本地音乐",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(onClick = { importLauncher.launch(arrayOf("audio/*")) }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("导入文件")
            }
        }
        when {
            !permissionGranted -> LocalPermissionCard(
                onGrant = { permissionLauncher.launch(permission) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                }
            )
            scanning && songs.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            songs.isEmpty() -> EmptyPane(
                "没有找到本地音乐，点右上角「导入文件」选择音频",
                { Icon(Icons.Filled.LibraryMusic, contentDescription = null) }
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(songs) { index, song ->
                    SongRow(
                        song = song,
                        index = index,
                        isActive = song.id == currentSongId,
                        isPlaying = song.id == currentSongId && isPlaying,
                        onClick = { if (songs.isNotEmpty()) player.playQueue(songs, index) }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
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
