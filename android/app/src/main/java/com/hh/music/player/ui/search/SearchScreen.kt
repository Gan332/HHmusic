package com.hh.music.player.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.LocalStoreProvider
import com.hh.music.player.ui.components.EmptyState
import com.hh.music.player.ui.components.LoadingState
import com.hh.music.player.ui.components.MiniPlayerBar
import com.hh.music.player.ui.components.SongRow
import kotlinx.coroutines.launch

private val HOT_SEARCHES = listOf("周杰伦", "林俊杰", "陈奕迅", "许嵩", "毛不易", "邓紫棋")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repository: MusicRepository,
    onOpenPlayer: () -> Unit,
    initialQuery: String = "",
    vm: SearchViewModel? = null
) {
    val store = LocalStoreProvider.current
    val actualVm = vm ?: viewModel { SearchViewModel(repository, store) }
    val state by actualVm.state.collectAsState()
    val player = LocalPlayerController.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val history by store.searchHistory.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val searchBarState = rememberSearchBarState()

    fun playFrom(index: Int) {
        val list = state.results
        if (list.isNotEmpty()) player.playQueue(list, index)
    }

    // 从首页热搜跳转过来时，自动填充并搜索关键词。
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            actualVm.onQueryChange(initialQuery)
            actualVm.submitSearch(initialQuery)
        }
    }

    val inputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            query = state.query,
            onQueryChange = actualVm::onQueryChange,
            onSearch = actualVm::submitSearch,
            expanded = searchBarState.currentValue == SearchBarValue.Expanded,
            onExpandedChange = { expanded ->
                scope.launch {
                    if (expanded) searchBarState.animateToExpanded()
                    else searchBarState.animateToCollapsed()
                }
            },
            placeholder = { Text("搜索歌曲、歌手") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { actualVm.onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "清空")
                    }
                }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column {
                    Text(
                        "搜索",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    SearchBar(
                        state = searchBarState,
                        inputField = inputField,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when {
                    state.loading -> LoadingState()
                    state.error != null -> EmptyState(
                        hint = "出错啦：${state.error}",
                        icon = Icons.Filled.Search
                    )
                    state.query.isBlank() -> SearchSuggestionsSection(
                        history = history,
                        hotSearches = HOT_SEARCHES,
                        onPick = { kw ->
                            actualVm.onQueryChange(kw)
                            actualVm.submitSearch(kw)
                        },
                        onClear = { actualVm.clearHistory() }
                    )
                    state.results.isEmpty() -> EmptyState(
                        hint = "没有找到结果",
                        icon = Icons.Filled.Search
                    )
                    else -> SearchResultsList(
                        state = state,
                        currentSongId = currentSong?.id,
                        isPlaying = isPlaying,
                        onPlay = ::playFrom,
                        onLoadMore = actualVm::loadMore
                    )
                }
                MiniPlayerBar(
                    player = player,
                    onClick = onOpenPlayer,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // 全屏展开的 M3E 搜索结果面板
        ExpandedFullScreenSearchBar(
            state = searchBarState,
            inputField = inputField,
            modifier = Modifier.fillMaxWidth()
        ) {
            when {
                state.loading -> LoadingState()
                state.query.isBlank() -> SearchSuggestionsSection(
                    history = history,
                    hotSearches = HOT_SEARCHES,
                    onPick = { kw ->
                        actualVm.onQueryChange(kw)
                        actualVm.submitSearch(kw)
                    },
                    onClear = { actualVm.clearHistory() }
                )
                state.results.isEmpty() && state.error == null -> EmptyState(
                    hint = "没有找到结果",
                    icon = Icons.Filled.Search
                )
                state.error != null -> EmptyState(
                    hint = "出错啦：${state.error}",
                    icon = Icons.Filled.Search
                )
                else -> SearchResultsList(
                    state = state,
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    onPlay = ::playFrom,
                    onLoadMore = actualVm::loadMore
                )
            }
        }
    }
}

/** Search result list with an auto-triggered "load more" footer while pages remain. */
@Composable
private fun SearchResultsList(
    state: SearchState,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlay: (Int) -> Unit,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()
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
        itemsIndexed(state.results) { index, song ->
            SongRow(
                song = song,
                index = index,
                isActive = song.id == currentSongId,
                isPlaying = song.id == currentSongId && isPlaying,
                onClick = { onPlay(index) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        }
        if (state.hasMore) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (state.loadingMore) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp))
                    } else {
                        TextButton(onClick = onLoadMore) { Text("加载更多") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchSuggestionsSection(
    history: List<String>,
    hotSearches: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // M3E 热搜词
        Text(
            "热门搜索",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            hotSearches.forEach { kw ->
                FilterChip(
                    selected = false,
                    onClick = { onPick(kw) },
                    label = { Text(kw) }
                )
            }
        }
        if (history.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "搜索历史",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClear) { Text("清空") }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                history.forEach { kw ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onPick(kw) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(kw, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        } else {
            Box(
                Modifier.fillMaxWidth().padding(top = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("输入关键词开始搜索", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
