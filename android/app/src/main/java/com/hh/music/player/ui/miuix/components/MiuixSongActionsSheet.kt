package com.hh.music.player.ui.miuix.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hh.music.player.data.Song
import com.hh.music.player.data.offline.DownloadState
import com.hh.music.player.ui.LocalCloudSync
import com.hh.music.player.ui.LocalDownloadManager
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalStoreProvider
import kotlinx.coroutines.launch

/**
 * miuix (HyperOS) 版共享歌曲操作弹层：长按任意歌曲列表行弹出，提供
 * 播放 / 下一首播放 / 加入队列 / 收藏 / 下载（本地歌曲隐藏下载）。
 * 业务逻辑与经典版 [com.hh.music.player.ui.components.SongActionsSheet] 一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiuixSongActionsSheet(
    song: Song?,
    onDismiss: () -> Unit
) {
    if (song == null) return
    val player = LocalPlayerController.current
    val store = LocalStoreProvider.current
    val downloadManager = LocalDownloadManager.current
    val cloudSync = LocalCloudSync.current
    val scope = rememberCoroutineScope()

    val favorites by store.favorites.collectAsState(initial = emptyList())
    val entries by downloadManager.entries.collectAsState(initial = emptyList())
    val statuses by downloadManager.statuses.collectAsState(initial = emptyMap())
    val currentQueue by player.queue.collectAsState()

    val isFav = favorites.any { it.id == song.id }
    val entry = entries.firstOrNull { it.id == song.id }
    val isDownloaded = entry != null && !entry.isFailed
    val isDownloading = statuses[song.id]?.state == DownloadState.DOWNLOADING

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiuixArtworkImage(
                    url = song.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artistText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(12.dp))


            SheetAction(
                icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                label = "播放",
                onClick = {
                    val idx = currentQueue.indexOfFirst { it.id == song.id }
                    if (idx >= 0) player.playAt(idx) else player.playQueue(listOf(song))
                    onDismiss()
                }
            )
            SheetAction(
                icon = { Icon(Icons.Filled.SkipNext, contentDescription = null) },
                label = "下一首播放",
                onClick = {
                    player.playNextInQueue(song)
                    onDismiss()
                }
            )
            SheetAction(
                icon = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
                label = "加入队列",
                onClick = {
                    player.addToQueue(song)
                    onDismiss()
                }
            )
            SheetAction(
                icon = {
                    Icon(
                        if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFav) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = if (isFav) "取消收藏" else "收藏",
                onClick = {
                    val willLike = !isFav
                    scope.launch {
                        store.toggleFavorite(song)
                        cloudSync.pushLike(song.id, willLike)
                    }
                    onDismiss()
                }
            )
            if (!song.isLocal) {
                when {
                    isDownloading -> SheetAction(
                        icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        label = "下载中…",
                        enabled = false
                    )
                    isDownloaded -> SheetAction(
                        icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        label = "删除下载",
                        onClick = {
                            downloadManager.remove(song.id)
                            onDismiss()
                        }
                    )
                    else -> SheetAction(
                        icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        label = "下载",
                        onClick = {
                            downloadManager.download(song)
                            onDismiss()
                        }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SheetAction(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    icon()
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
