package com.hh.music.player.ui.miuix.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalStoreProvider
import com.hh.music.player.ui.miuix.components.MiuixSongActionMenu
import com.hh.music.player.ui.miuix.components.MiuixSongRow
import com.hh.music.player.ui.search.SearchViewModel
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixSearchScreen(
    repository: MusicRepository,
    onOpenPlayer: () -> Unit,
    vm: SearchViewModel? = null,
) {
    val store = LocalStoreProvider.current
    val actualVm = vm ?: viewModel { SearchViewModel(repository, store) }
    val state by actualVm.state.collectAsState()
    val player = LocalPlayerController.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val history by store.searchHistory.collectAsState(initial = emptyList())

    fun playFrom(index: Int) {
        val list = state.results
        if (list.isNotEmpty()) player.playQueue(list, index)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Text(
                "搜索",
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )
            SearchBar(
                inputField = {
                    top.yukonga.miuix.kmp.basic.InputField(
                        query = state.query,
                        onQueryChange = actualVm::onQueryChange,
                        onSearch = { kw -> actualVm.submitSearch(kw) },
                        label = "搜索歌曲、歌手"
                    )
                },
                onExpandedChange = { /* inline search; no expand popup */ },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {}
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    state.error != null -> Text(
                        "出错啦: ${state.error}",
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                    state.query.isBlank() -> HistorySection(
                        history = history,
                        onPick = { kw -> actualVm.submitSearch(kw) },
                        onClear = { actualVm.clearHistory() }
                    )
                    state.results.isEmpty() -> Text(
                        "没有找到结果",
                        color = MiuixTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        itemsIndexed(state.results) { index, song ->
                            MiuixSongRow(
                                song = song, index = index,
                                isActive = song.id == currentSong?.id,
                                isPlaying = song.id == currentSong?.id && isPlaying,
                                onClick = { playFrom(index) },
                                trailing = { MiuixSongActionMenu(player = player, song = song) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySection(
    history: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("输入关键词开始搜索", color = MiuixTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.History, contentDescription = null, tint = MiuixTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text("搜索历史", style = MiuixTheme.textStyles.title4, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("清空") }
        }
        history.forEach { kw ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(kw) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(kw, color = MiuixTheme.colorScheme.onSurface)
            }
        }
    }
}
