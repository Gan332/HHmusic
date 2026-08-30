package com.hh.music.player.ui.miuix.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalStoreProvider
import com.hh.music.player.ui.library.LibraryViewModel
import com.hh.music.player.ui.miuix.components.MiuixSongActionMenu
import com.hh.music.player.ui.miuix.components.MiuixSongRow
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixLibraryScreen(
    onOpenPlaylist: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val store = LocalStoreProvider.current
    val vm: LibraryViewModel = viewModel { LibraryViewModel(store) }
    val favorites by vm.favorites.collectAsState()
    val recent by vm.recent.collectAsState()
    val savedPlaylists by vm.savedPlaylists.collectAsState()
    val player = LocalPlayerController.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val colors = MiuixTheme.colorScheme

    Scaffold(
        topBar = { TopAppBar(title = "音乐库") }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                item { SmallTitle("我收藏的歌曲（${favorites.size}）") }
                if (favorites.isEmpty()) {
                    item { EmptyRow("还没有收藏的歌曲，去播放页点 ♥ 吧") }
                } else {
                    itemsIndexed(favorites.take(20)) { index, song ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            MiuixSongRow(
                                song = song, index = index,
                                isActive = song.id == currentSong?.id,
                                isPlaying = song.id == currentSong?.id && isPlaying,
                                onClick = { player.playQueue(favorites, index) },
                                trailing = { MiuixSongActionMenu(player = player, song = song) }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)); SmallTitle("最近播放（${recent.size}）") }
                if (recent.isEmpty()) {
                    item { EmptyRow("暂无播放记录") }
                } else {
                    itemsIndexed(recent.take(20)) { index, song ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            MiuixSongRow(
                                song = song, index = index,
                                isActive = song.id == currentSong?.id,
                                isPlaying = song.id == currentSong?.id && isPlaying,
                                onClick = { player.playQueue(recent, index) },
                                trailing = { MiuixSongActionMenu(player = player, song = song) }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)); SmallTitle("收藏的歌单（${savedPlaylists.size}）") }
                if (savedPlaylists.isEmpty()) {
                    item { EmptyRow("还没有收藏的歌单") }
                } else {
                    itemsIndexed(savedPlaylists) { _, pl ->
                        // Reuse the textual list row for now; the Material3
                        // skin can keep its richer card with cover image.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(pl.name, color = colors.onSurface)
                            Text(
                                if (pl.creator.isNotBlank()) pl.creator else "未知创建者",
                                style = MiuixTheme.textStyles.footnote1,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRow(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp)
    )
}
