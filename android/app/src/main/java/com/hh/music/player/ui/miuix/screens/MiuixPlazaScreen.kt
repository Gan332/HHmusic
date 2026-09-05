package com.hh.music.player.ui.miuix.screens

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
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.PlazaPlaylist
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.miuix.components.MiuixArtworkImage
import com.hh.music.player.ui.miuix.components.MiuixErrorState
import com.hh.music.player.ui.miuix.components.MiuixLoadingState
import com.hh.music.player.ui.miuix.components.MiuixMiniPlayerBar
import com.hh.music.player.ui.plaza.PlazaViewModel
import com.hh.music.player.ui.plaza.formatPlaycount

/**
 * miuix (HyperOS) 版歌单广场：分类 chips + 热门/最新切换 + 双列歌单网格，
 * 滚动到底自动翻页。业务逻辑复用 [PlazaViewModel]。
 */
@Composable
fun MiuixPlazaScreen(
    repository: MusicRepository,
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
    vm: PlazaViewModel = viewModel { PlazaViewModel(repository) }
) {
    val state by vm.state.collectAsState()
    val player = LocalPlayerController.current
    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.more && !state.loadingMore && lastVisible >= state.playlists.size - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.playlists.isNotEmpty()) vm.loadMore()
    }


    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "歌单广场",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        if (state.order == "hot") "热门" else "最新",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = vm::toggleOrder) {
                    Icon(Icons.Filled.SwapVert, contentDescription = "切换热门/最新")
                }
            }
        },
        bottomBar = {
            MiuixMiniPlayerBar(player = player, onClick = onOpenPlayer)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
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

            when {
                state.playlists.isEmpty() && state.loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MiuixLoadingState()
                    }
                state.playlists.isEmpty() && state.error ->
                    MiuixErrorState("歌单加载失败，请检查网络") { vm.refresh() }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(state.playlists, key = { _, p -> p.id }) { _, pl ->
                        MiuixPlazaCard(pl, onClick = { onOpenPlaylist(pl.id) })
                    }
                    if (state.loadingMore) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                            Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixPlazaCard(pl: PlazaPlaylist, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        MiuixArtworkImage(
            url = pl.picUrl.orEmpty(),
            contentDescription = pl.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            pl.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
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
