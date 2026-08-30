package com.hh.music.player.ui.miuix.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hh.music.player.data.Song
import com.hh.music.player.playback.PlayerController

/**
 * Miuix equivalent of [com.hh.music.player.ui.components.SongActionMenu].
 *
 * Miuix 0.9.4-rc01 doesn't expose a DropdownMenu/DropdownMenuItem under
 * `top.yukonga.miuix.kmp.basic` (the public popup API is the heavier
 * ListPopup). For an overflow menu this light, we reuse Material3's menu —
 * MiuixTheme colours the surface underneath it.
 */
@Composable
fun MiuixSongActionMenu(
    player: PlayerController,
    song: Song,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("下一首播放") },
                onClick = { showMenu = false; player.playNext(song) },
                leadingIcon = { Icon(Icons.Filled.SkipNext, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("加入队列") },
                onClick = { showMenu = false; player.addToQueue(song) },
                leadingIcon = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) }
            )
        }
    }
}
