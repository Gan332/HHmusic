package com.hh.music.player.ui.miuix.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.Artist
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Song
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalStoreProvider
import com.hh.music.player.ui.miuix.components.MiuixEmptyState
import com.hh.music.player.ui.miuix.components.MiuixErrorState
import com.hh.music.player.ui.miuix.components.MiuixLoadingState
import com.hh.music.player.ui.miuix.components.MiuixMiniPlayerBar
import com.hh.music.player.ui.miuix.components.MiuixSongActionsSheet
import com.hh.music.player.ui.miuix.components.MiuixSongRow
import com.hh.music.player.ui.search.SearchViewModel

@Composable
fun MiuixSearchScreen(
    repository: MusicRepository,
    onOpenPlayer: () -> Unit,
    initialQuery: String = "",
    onOpenArtist: (Artist) -> Unit = {},
    vm: SearchViewModel? = null
) {
    val store = LocalStoreProvider.current
    val actualVm = vm ?: viewModel { SearchViewModel(repository, store) }
    val state by actualVm.state.collectAsState()
    val player = LocalPlayerController.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val history by store.searchHistory.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf(initialQuery) }
    var actionsSong by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            query = initialQuery
            actualVm.onQueryChange(initialQuery)
            actualVm.submitSearch(initialQuery)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                Text(
                    "搜索",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
                // 搜索框
                OutlinedTextField(
                    value = query,
                    onValueChange = { newValue ->
                        query = newValue
                        actualVm.onQueryChange(newValue)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        actualVm.submitSearch(query)
                    }),
                    placeholder = { Text("搜索歌曲、歌手") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                query = ""
                                actualVm.onQueryChange("")
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "清空")
                            }
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        },
        bottomBar = {
            MiuixMiniPlayerBar(player = player, onClick = onOpenPlayer)
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> MiuixLoadingState()
                state.error != null -> MiuixErrorState(
                    message = state.error.orEmpty(),
                    onRetry = actualVm::retry
                )
                query.isBlank() -> SearchSuggestionsSection(
                    history = history,
                    hotSearches = state.hotSearches,
                    onPick = { kw ->
                        query = kw
                        actualVm.onQueryChange(kw)
                        actualVm.submitSearch(kw)
                    },
                    onClear = { actualVm.clearHistory() }
                )
                state.results.isEmpty() && state.artists.isEmpty() -> MiuixEmptyState(
                    hint = "没有找到结果",
                    icon = Icons.Filled.Search
                )
                else -> SearchResultsList(
                    state = state,
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    onPlay = { index ->
                        val list = state.results
                        if (list.isNotEmpty()) player.playQueue(list, index)
                    },
                    onLoadMore = actualVm::loadMore,
                    artists = state.artists,
                    onOpenArtist = onOpenArtist,
                    onLongPressSong = { actionsSong = it }
                )
            }
        }
    }

    MiuixSongActionsSheet(song = actionsSong, onDismiss = { actionsSong = null })
}

@Composable
private fun SearchSuggestionsSection(
    history: List<String>,
    hotSearches: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (history.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("搜索历史", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClear) { Text("清空") }
                }
            }
            itemsIndexed(history) { _, keyword ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(keyword) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(keyword, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (hotSearches.isNotEmpty()) {
            item {
                Text(
                    "热门搜索",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                )
            }
            itemsIndexed(hotSearches) { index, keyword ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(keyword) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(32.dp)
                    )
                    Text(keyword, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    state: com.hh.music.player.ui.search.SearchState,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlay: (Int) -> Unit,
    onLoadMore: () -> Unit,
    artists: List<Artist> = emptyList(),
    onOpenArtist: (Artist) -> Unit = {},
    onLongPressSong: (Song) -> Unit = {}
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.hasMore && !state.loadingMore && lastVisible >= state.results.size - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.results.isNotEmpty()) onLoadMore()
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        if (artists.isNotEmpty()) {
            item {
                Text(
                    "歌手",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                )
            }
            item {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(artists) { _, artist ->
                        Column(
                            modifier = Modifier.width(80.dp).clickable { onOpenArtist(artist) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = RoundedCornerShape(40.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(64.dp)
                            ) {
                                com.hh.music.player.ui.miuix.components.MiuixArtworkImage(
                                    url = artist.picUrl.orEmpty(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                artist.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                "歌曲",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )
        }
        itemsIndexed(state.results) { index, song ->
            MiuixSongRow(
                song = song,
                index = index,
                isActive = song.id == currentSongId,
                isPlaying = song.id == currentSongId && isPlaying,
                onClick = { onPlay(index) },
                onLongClick = { onLongPressSong(song) }
            )
        }
        if (state.loadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
