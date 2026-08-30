package com.hh.music.player.ui.miuix.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.BuildConfig
import com.hh.music.player.data.local.LocalStore
import com.hh.music.player.ui.theme.AppThemeColor
import com.hh.music.player.ui.theme.AppThemeMode
import com.hh.music.player.ui.theme.LyricFontScale
import com.hh.music.player.ui.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiuixSettingsScreen(
    store: LocalStore,
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel { SettingsViewModel(store) }
) {
    val useBackend by vm.useBackend.collectAsState()
    val quality by vm.audioQuality.collectAsState()
    val progressStyle by vm.progressStyle.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val themeColor by vm.themeColor.collectAsState()
    val uiStyle by vm.uiStyle.collectAsState()
    val dynamicColor by vm.dynamicColor.collectAsState()
    val showLyricTranslation by vm.showLyricTranslation.collectAsState()
    val showLyricRomanization by vm.showLyricRomanization.collectAsState()
    val lyricFontScale by vm.lyricFontScale.collectAsState()
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicColorActive = dynamicColorSupported && dynamicColor

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 外观
            SectionLabel("外观")
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("主题外观", fontWeight = FontWeight.Medium)
                            Text(
                                "浅色 / 深色可手动选择，也可以跟随系统",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "主题模式",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        AppThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { vm.setThemeMode(mode.key) },
                                shape = SegmentedButtonDefaults.itemShape(index, AppThemeMode.entries.size)
                            ) { Text(mode.label) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // 界面风格
                    Text(
                        "界面风格",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = uiStyle == "classic",
                            onClick = { vm.setUiStyle("classic") },
                            shape = SegmentedButtonDefaults.itemShape(0, 3)
                        ) { Text("经典") }
                        SegmentedButton(
                            selected = uiStyle == "kanade",
                            onClick = { vm.setUiStyle("kanade") },
                            shape = SegmentedButtonDefaults.itemShape(1, 3)
                        ) { Text("Kanade") }
                        SegmentedButton(
                            selected = uiStyle == "miuix",
                            onClick = { vm.setUiStyle("miuix") },
                            shape = SegmentedButtonDefaults.itemShape(2, 3)
                        ) { Text("Miuix") }
                    }
                    Text(
                        when (uiStyle) {
                            "kanade" -> "页面切换使用连贯的淡入滑动动画"
                            "miuix" -> "使用小米 HyperOS 设计风格界面"
                            else -> "使用系统默认页面切换"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("动态取色", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "跟随系统壁纸主题色"
                                else "需要 Android 12 及以上",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = dynamicColorActive,
                            onCheckedChange = { vm.setDynamicColor(it) },
                            enabled = dynamicColorSupported
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "主题色",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppThemeColor.entries.forEach { color ->
                            FilterChip(
                                selected = themeColor == color,
                                enabled = !dynamicColorActive,
                                onClick = { vm.setThemeColor(color.key) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier
                                                .size(14.dp)
                                                .clip(MaterialTheme.shapes.extraSmall)
                                                .background(color.swatch)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(color.label)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 播放
            Spacer(Modifier.height(8.dp))
            SectionLabel("播放")
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("播放设置", fontWeight = FontWeight.Medium)
                            Text(
                                "音质、进度条样式等",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "音质",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val qualities = listOf(
                            "standard" to "标准",
                            "higher" to "较高",
                            "exhigh" to "极高"
                        )
                        qualities.forEachIndexed { index, (key, label) ->
                            SegmentedButton(
                                selected = quality == key,
                                onClick = { vm.setAudioQuality(key) },
                                shape = SegmentedButtonDefaults.itemShape(index, qualities.size)
                            ) { Text(label) }
                        }
                    }
                }
            }

            // 数据源
            Spacer(Modifier.height(8.dp))
            SectionLabel("数据源")
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (useBackend) Icons.Filled.Cloud else Icons.Filled.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("使用后端代理", fontWeight = FontWeight.Medium)
                            Text(
                                "关闭时直连网易云 eapi，开启时通过后端代理",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useBackend,
                            onCheckedChange = { vm.setUseBackend(it) }
                        )
                    }
                }
            }

            // 关于
            Spacer(Modifier.height(8.dp))
            SectionLabel("关于")
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("HH Music", fontWeight = FontWeight.Medium)
                    Text(
                        "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Jetpack Compose + Material 3",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}
