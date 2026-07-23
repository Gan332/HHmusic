package com.hh.music.player.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalStoreProvider
import com.hh.music.player.ui.components.MiniPlayerBar
import com.hh.music.player.ui.components.SongRow

@Composable
fun SearchScreen(
    repository: MusicRepository,
    onOpenPlayer: () -> Unit,
    vm: SearchViewModel? = null
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

    Scaffold(
        topBar = {
            Column {
                Text(
                    "搜索",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
                OutlinedTextField(
                    value = state.query,
                    onValueChange = actualVm::onQueryChange,
                    placeholder = { Text("搜索歌曲、歌手") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { actualVm.onQueryChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "清空")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    "出错啦: ${state.error}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                state.query.isBlank() -> {
                    // Search history section
                    SearchHistorySection(
                        history = history,
                        onPick = { kw -> actualVm.onQueryChange(kw); actualVm.submitSearch(kw) },
                        onClear = { actualVm.clearHistory() }
                    )
                }
                state.results.isEmpty() -> Text(
                    "没有找到结果",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(state.results) { index, song ->
                            SongRow(
                                song = song,
                                index = index,
                                isActive = song.id == currentSong?.id,
                                isPlaying = song.id == currentSong?.id && isPlaying,
                                onClick = { playFrom(index) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        }
                        item { Spacer(Modifier.height(72.dp)) }
                    }
                }
            }
            MiniPlayerBar(
                player = player,
                onClick = onOpenPlayer,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun SearchHistorySection(
    history: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit
) {
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("输入关键词开始搜索", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("搜索历史", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("清空") }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            history.forEach { kw ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(kw) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(kw, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
