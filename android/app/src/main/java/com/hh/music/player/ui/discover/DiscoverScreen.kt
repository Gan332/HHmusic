package com.hh.music.player.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Song
import com.hh.music.player.network.RecommendPlaylistItem
import com.hh.music.player.ui.LocalPlayerController
import com.hh.music.player.ui.components.ArtworkImage
import com.hh.music.player.ui.components.BadgeShapes
import com.hh.music.player.ui.components.ErrorState
import com.hh.music.player.ui.components.LoadingState
import com.hh.music.player.ui.components.MiniPlayerBar
import com.hh.music.player.ui.components.ShapeBadge
import com.hh.music.player.ui.components.SongRow

private enum class MoreSection { RECOMMEND, NEW, PLAYLISTS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DiscoverScreen(
    repository: MusicRepository,
    onOpenToplist: () -> Unit,
    onOpenPlaza: () -> Unit = {},
    onPersonalFm: () -> Unit = {},
    onSearch: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
    vm: DiscoverViewModel = viewModel { DiscoverViewModel(repository) }
) {
    val state by vm.state.collectAsState()
    val player = LocalPlayerController.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    var moreSection by remember { mutableStateOf<MoreSection?>(null) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // M3E 大标题栏：折叠为紧凑标题，展开时展示副标题。
            LargeFlexibleTopAppBar(
                title = { Text("HH Music", fontWeight = FontWeight.SemiBold) },
                subtitle = { Text("听见此刻") },
                actions = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("刷新推荐") } },
                        state = rememberTooltipState()
                    ) {
                        FilledTonalIconButton(onClick = { vm.refresh(force = true) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        // M3E 下拉刷新：复用原有强制刷新逻辑。
        PullToRefreshBox(
            isRefreshing = state.allEmpty && state.recommend.loading,
            onRefresh = { vm.refresh(force = true) },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            when {
                state.allEmpty && state.recommend.loading -> LoadingState()
                state.allEmpty && state.allFailed -> ErrorState("推荐加载失败，请检查网络", { vm.refresh(force = true) })
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        // 搜索入口（自 v1.6 起从顶栏迁移至内容区首项）
                        Surface(
                            shape = SearchBarDefaults.dockedShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = SearchBarDefaults.TonalElevation,
                            shadowElevation = SearchBarDefaults.ShadowElevation,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(56.dp)
                                .clickable { onSearch("") }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Text("搜索歌曲、歌手或歌单", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item {
                        QuickEntries(
                            state,
                            onOpenToplist,
                            onOpenPlaza,
                            onPersonalFm,
                            { songs -> player.playQueue(songs, 0) }
                        )
                    }
                    item {
                        Spacer(Modifier.height(10.dp))
                        SectionTitle(
                            title = "每日推荐",
                            subtitle = "为你精选",
                            onMore = { moreSection = MoreSection.RECOMMEND }
                        )
                        SectionContent(state.recommend, onRetry = { vm.refresh(force = true) }) {
                            SongCarousel(
                                songs = it.take(10),
                                activeId = currentSong?.id,
                                isPlaying = isPlaying,
                                onPlay = { index -> player.playQueue(state.recommend.data, index) }
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(18.dp))
                        SectionTitle(title = "推荐歌单", subtitle = "更多好音乐", onMore = { moreSection = MoreSection.PLAYLISTS })
                        SectionContent(state.playlists, onRetry = { vm.refresh(force = true) }) {
                            PlaylistCarousel(it, onOpenPlaylist)
                        }
                    }
                    item {
                        Spacer(Modifier.height(18.dp))
                        SectionTitle(title = "新歌速递", subtitle = "最近上新", onMore = { moreSection = MoreSection.NEW })
                        SectionContent(state.newSongs, onRetry = { vm.refresh(force = true) }) {
                            SongCarousel(
                                songs = it.take(10),
                                activeId = currentSong?.id,
                                isPlaying = isPlaying,
                                onPlay = { index -> player.playQueue(state.newSongs.data, index) }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
            MiniPlayerBar(player, onOpenPlayer, Modifier.align(Alignment.BottomCenter))
        }
    }

    // 「查看更多」：整块列表底部弹层。
    val moreData = when (moreSection) {
        MoreSection.RECOMMEND -> state.recommend.data
        MoreSection.NEW -> state.newSongs.data
        MoreSection.PLAYLISTS -> state.playlists.data
        null -> emptyList()
    }
    if (moreSection != null) {
        ModalBottomSheet(onDismissRequest = { moreSection = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                val title = when (moreSection) {
                    MoreSection.RECOMMEND -> "每日推荐"
                    MoreSection.NEW -> "新歌速递"
                    MoreSection.PLAYLISTS -> "推荐歌单"
                    null -> ""
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (moreData.isNotEmpty() && moreSection != MoreSection.PLAYLISTS) {
                        TextButton(onClick = {
                            player.playQueue(moreData as List<Song>, 0)
                            moreSection = null
                        }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("播放全部")
                        }
                    }
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                    when (moreSection) {
                        MoreSection.PLAYLISTS -> itemsIndexed(moreData as List<RecommendPlaylistItem>) { _, pl ->
                            Row(Modifier.fillMaxWidth().clickable { moreSection = null; onOpenPlaylist(pl.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                ArtworkImage(url = pl.coverUrl, contentDescription = null, modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.medium))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(pl.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(pl.creatorName.ifBlank { "精选歌单" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        }
                        else -> itemsIndexed(moreData as List<Song>) { index, s ->
                            SongRow(
                                song = s, index = index,
                                isActive = s.id == currentSong?.id,
                                isPlaying = s.id == currentSong?.id && isPlaying,
                                onClick = {
                                    player.playQueue(moreData as List<Song>, index)
                                    moreSection = null
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}

/** 模块级内容区：有数据 → 渲染；失败 → 行内错误 + 重试；加载中（无数据） → 行内 M3E 加载。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> SectionContent(
    section: SectionState<T>,
    onRetry: () -> Unit,
    content: @Composable (List<T>) -> Unit
) {
    when {
        section.data.isNotEmpty() -> content(section.data)
        section.error -> Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("该模块加载失败", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRetry) { Text("重试") }
        }
        else -> Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
    }
}

@Composable
private fun QuickEntries(
    state: DiscoverState,
    onOpenToplist: () -> Unit,
    onOpenPlaza: () -> Unit,
    onPersonalFm: () -> Unit,
    onPlay: (List<Song>) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickEntry("排行榜", Icons.Filled.Leaderboard, BadgeShapes.Sunny, onOpenToplist, Modifier.weight(1f))
        QuickEntry("歌单广场", Icons.Filled.QueueMusic, BadgeShapes.Cookie, onOpenPlaza, Modifier.weight(1f))
        QuickEntry(
            "私人FM",
            Icons.Filled.Radio,
            BadgeShapes.Burst,
            {
                android.widget.Toast.makeText(context, "私人FM加载中…", android.widget.Toast.LENGTH_SHORT).show()
                onPersonalFm()
            },
            Modifier.weight(1f)
        )
        QuickEntry(
            "每日推荐",
            Icons.Filled.AutoAwesome,
            BadgeShapes.Clover,
            { if (state.recommend.data.isNotEmpty()) onPlay(state.recommend.data) },
            Modifier.weight(1f)
        )
        QuickEntry(
            "新歌速递",
            Icons.Filled.Whatshot,
            BadgeShapes.Flower,
            { if (state.newSongs.data.isNotEmpty()) onPlay(state.newSongs.data) },
            Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickEntry(
    label: String,
    icon: ImageVector,
    shape: RoundedPolygon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(onClick = onClick, modifier = modifier) {
        Column(
            Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ShapeBadge(
                icon = icon,
                shape = shape,
                badgeSize = 44.dp,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onMore) { Text("查看更多") }
    }
}

@Composable
private fun SongCarousel(
    songs: List<Song>,
    activeId: Long?,
    isPlaying: Boolean,
    onPlay: (Int) -> Unit
) {
    if (songs.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(songs) { itemIndex, song ->
            MediaTile(
                title = song.name,
                subtitle = song.artistText,
                imageUrl = song.coverUrl,
                active = song.id == activeId && isPlaying,
                onClick = { onPlay(itemIndex) },
                modifier = Modifier.width(200.dp).clip(MaterialTheme.shapes.extraLarge)
            )
        }
    }
}

@Composable
private fun PlaylistCarousel(
    playlists: List<RecommendPlaylistItem>,
    onClick: (Long) -> Unit
) {
    if (playlists.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(playlists) { itemIndex, playlist ->
            MediaTile(
                title = playlist.name,
                subtitle = playlist.creatorName.ifBlank { "精选歌单" },
                imageUrl = playlist.coverUrl,
                active = false,
                onClick = { onClick(playlists[itemIndex].id) },
                modifier = Modifier.width(156.dp).clip(MaterialTheme.shapes.extraLarge)
            )
        }
    }
}

@Composable
private fun MediaTile(
    title: String,
    subtitle: String,
    imageUrl: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onClick)
        ) {
            ArtworkImage(
                url = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(40.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "播放")
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
