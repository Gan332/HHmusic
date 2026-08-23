@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.hh.music.player.ui.plaza

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.PlazaPlaylist
import com.hh.music.player.network.RecommendPlaylistItem
import com.hh.music.player.ui.components.ArtworkImage
import com.hh.music.player.ui.components.ErrorState
import com.hh.music.player.ui.components.LoadingState

/**
 * 歌单广场（v1.7）：分类 chips + 热门/最新切换 + 双列歌单网格，滚动到底自动翻页。
 * 点击歌单进入既有的 PlaylistScreen。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlazaScreen(
    repository: com.hh.music.player.data.MusicRepository,
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    vm: PlazaViewModel = viewModel { PlazaViewModel(repository) }
) {
    val state by vm.state.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val gridState = rememberLazyGridState()

    // Infinite scroll: one page before the end triggers loadMore.
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4 && state.more && !state.loadingMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) vm.loadMore()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("歌单广场", fontWeight = FontWeight.SemiBold) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(if (state.order == "hot") "当前：热门" else "当前：最新") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = { vm.toggleOrder() }) {
                            Icon(Icons.Filled.SwapVert, contentDescription = "热门/最新")
                        }
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Category chips row.
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.categories, key = { it.name }) { cat ->
                    FilterChip(
                        selected = state.selectedCat == cat.name,
                        onClick = { vm.selectCategory(cat.name) },
                        label = { Text(cat.name) }
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    state.playlists.isEmpty() && state.loading -> LoadingState()
                    state.playlists.isEmpty() && state.error ->
                        ErrorState("歌单加载失败，请检查网络", { vm.refresh() })
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(state.playlists, key = { _, p -> p.id }) { _, pl ->
                            PlazaCard(pl, onClick = { onOpenPlaylist(pl.id) })
                        }
                        if (state.loadingMore) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                                Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) {
                                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.height(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlazaCard(pl: PlazaPlaylist, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        ArtworkImage(
            url = pl.picUrl.orEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            pl.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Headphones,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                formatPlaycount(pl.playcount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 12345 -> "1.2万", 123456789 -> "1.2亿". */
internal fun formatPlaycount(count: Long): String = when {
    count >= 100_000_000 -> String.format("%.1f亿", count / 100_000_000.0)
    count >= 10_000 -> String.format("%.1f万", count / 10_000.0)
    else -> count.toString()
}
