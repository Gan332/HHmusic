package com.hh.music.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.BuildConfig
import com.hh.music.player.data.local.LocalStore
import com.hh.music.player.ui.ProgressStyle

private val QUALITY_OPTIONS = listOf(
    "standard" to "标准 (128k)",
    "higher" to "较高 (192k)",
    "exhigh" to "极高 (320k)",
    "lossless" to "无损 (FLAC)",
    "hires" to "Hi-Res"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    store: LocalStore,
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel { SettingsViewModel(store) }
) {
    val useBackend by vm.useBackend.collectAsState()
    val quality by vm.audioQuality.collectAsState()
    val progressStyle by vm.progressStyle.collectAsState()

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
            // ---- 数据源 ----
            SectionLabel("数据源")
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                            Text("数据通道", fontWeight = FontWeight.Medium)
                            Text(
                                "直连无需后端；代理需本地运行 server/",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !useBackend,
                            onClick = { vm.setUseBackend(false) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) {
                            Text("直连网易云")
                        }
                        SegmentedButton(
                            selected = useBackend,
                            onClick = { vm.setUseBackend(true) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) {
                            Text("本地后端")
                        }
                    }
                    if (useBackend) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "已切换到后端模式，请确保 server/ 在运行且 NetworkModule.BASE_URL 可达。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // ---- 播放 ----
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
                        Column {
                            Text("默认音质", fontWeight = FontWeight.Medium)
                            Text(
                                QUALITY_OPTIONS.firstOrNull { it.first == quality }?.second ?: quality,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    // M3E 筛选片选择音质
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QUALITY_OPTIONS.forEach { (key, label) ->
                            FilterChip(
                                selected = quality == key,
                                onClick = { vm.setAudioQuality(key) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // ---- 进度条样式 ----
            Spacer(Modifier.height(8.dp))
            SectionLabel("播放进度条")
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.ShowChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("进度条样式", fontWeight = FontWeight.Medium)
                            Text(
                                "滑块可拖动，线性/环形为 M3E 进度指示器",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ProgressStyle.entries.forEachIndexed { index, style ->
                            SegmentedButton(
                                selected = progressStyle == style.key,
                                onClick = { vm.setProgressStyle(style.key) },
                                shape = SegmentedButtonDefaults.itemShape(index, ProgressStyle.entries.size)
                            ) {
                                Text(style.label)
                            }
                        }
                    }
                }
            }

            // ---- 关于 ----
            Spacer(Modifier.height(8.dp))
            SectionLabel("关于")
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AboutRow("应用", "HH音乐")
                    AboutRow("版本", "v${BuildConfig.VERSION_NAME}")
                    AboutRow("数据接口", "网易云音乐 (eapi 直连)")
                    AboutRow("参考", "GuitaristRin/Ncrust")
                    AboutRow("技术栈", "Kotlin · Compose M3E · Media3")
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "仅供学习交流，接口与资源版权归网易云音乐所有。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun AboutRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(v, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
    }
}
