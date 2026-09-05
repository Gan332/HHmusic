package com.hh.music.player.ui.miuix.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.ToplistItem
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.miuix.components.MiuixArtworkImage
import com.hh.music.player.ui.miuix.components.MiuixErrorState
import com.hh.music.player.ui.miuix.components.MiuixLoadingState
import com.hh.music.player.ui.miuix.components.MiuixMiniPlayerBar
import com.hh.music.player.ui.playlist.ToplistViewModel

/** miuix (HyperOS) 版排行榜页：巅峰榜横滑 + 全部榜单列表。 */
@Composable
fun MiuixToplistScreen(
    repository: MusicRepository,
    onPlaylistClick: (Long) -> Unit,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    vm: ToplistViewModel = viewModel { ToplistViewModel(repository) }
) {
    val state by vm.state.collectAsState()
    val player = LocalPlayerController.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    "排行榜",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = vm::refresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            }
        },
        bottomBar = {
            MiuixMiniPlayerBar(player = player, onClick = onOpenPlayer)
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.toplists.isEmpty() -> MiuixLoadingState()
                state.error != null && state.toplists.isEmpty() -> MiuixErrorState(
                    message = state.error.orEmpty(),
                    onRetry = vm::refresh
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    if (state.toplists.isNotEmpty()) {
                        item {
                            MiuixSectionTitle("巅峰榜", "官方权威榜单")
                            MiuixToplistCarousel(state.toplists.take(5), onPlaylistClick)
                        }
                        item {
                            Spacer(Modifier.height(14.dp))
                            MiuixSectionTitle("全部榜单", "${state.toplists.size} 个")
                        }
                    }
                    items(state.toplists, key = { it.id }) { item ->
                        MiuixToplistRow(
                            name = item.name,
                            coverUrl = item.coverImgUrl,
                            updateFrequency = item.updateFrequency,
                            onClick = { onPlaylistClick(item.id) }
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun MiuixSectionTitle(title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MiuixToplistCarousel(
    toplists: List<ToplistItem>,
    onPlaylistClick: (Long) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(toplists) { _, item ->
            Column(
                Modifier.width(140.dp).clickable { onPlaylistClick(item.id) }
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    MiuixArtworkImage(
                        url = item.coverImgUrl.orEmpty(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiuixToplistRow(
    name: String,
    coverUrl: String?,
    updateFrequency: String?,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiuixArtworkImage(
                url = coverUrl.orEmpty(),
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!updateFrequency.isNullOrBlank()) {
                    Text(
                        updateFrequency,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
