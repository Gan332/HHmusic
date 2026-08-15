package com.hh.music.player.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.components.ArtworkImage
import com.hh.music.player.ui.components.ErrorState
import com.hh.music.player.ui.components.MiniPlayerBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToplistScreen(
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
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = vm::refresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.toplists.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = vm::refresh,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    if (state.toplists.isNotEmpty()) {
                        item {
                            SectionTitle("巅峰榜", "官方权威榜单")
                            ToplistCarousel(state.toplists.take(5), onPlaylistClick)
                        }
                        item {
                            Spacer(Modifier.height(14.dp))
                            SectionTitle("全部榜单", "${state.toplists.size} 个")
                        }
                    }
                    items(state.toplists, key = { it.id }) { item ->
                        ToplistRow(item.name, item.coverImgUrl, item.updateFrequency) {
                            onPlaylistClick(item.id)
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
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
private fun ToplistCarousel(
    toplists: List<com.hh.music.player.data.ToplistItem>,
    onPlaylistClick: (Long) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(toplists, key = { it.id }) { item ->
            ElevatedCard(
                onClick = { onPlaylistClick(item.id) },
                modifier = Modifier.width(180.dp).clip(MaterialTheme.shapes.extraLarge)
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    ArtworkImage(
                        url = item.coverImgUrl.orEmpty(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                    FilledIconButton(
                        onClick = { onPlaylistClick(item.id) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(40.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "查看")
                    }
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
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                MaterialTheme.shapes.extraLarge
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
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
private fun ToplistRow(
    name: String,
    coverUrl: String?,
    updateFrequency: String?,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkImage(
                    url = coverUrl.orEmpty(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(MaterialTheme.shapes.medium)
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
