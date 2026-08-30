package com.hh.music.player.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.hh.music.player.data.Song
import com.hh.music.player.playback.PlayerController

@Composable
fun SongActionMenu(
    player: PlayerController,
    song: Song,
    modifier: Modifier = Modifier
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
