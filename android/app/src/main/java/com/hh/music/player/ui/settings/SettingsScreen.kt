package com.hh.music.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.data.local.LocalStore
import com.hh.music.player.ui.theme.UiStyle

private val QUALITY_OPTIONS = listOf(
    "standard" to "标准 (128k)",
    "higher" to "较高 (192k)",
    "exhigh" to "极高 (320k)",
    "lossless" to "无损 (FLAC)",
    "hires" to "Hi-Res"
)

/** Predefined theme seed colors for the color picker. */
private val THEME_COLORS = listOf(
    "#1DB954" to "网易绿",
    "#FF6B6B" to "热情红",
    "#4A90D9" to "天空蓝",
    "#9B59B6" to "典雅紫",
    "#2ECC71" to "翡翠绿",
    "#F39C12" to "暖阳橙",
    "#E74C3C" to "朱红",
    "#1ABC9C" to "青碧",
    "#3498DB" to "海洋蓝",
    "#E91E63" to "玫瑰粉",
    "#00BCD4" to "青色",
    "#FF9800" to "琥珀橙",
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
    val isDarkTheme by vm.isDarkTheme.collectAsState()
    val backendUrl by vm.backendUrl.collectAsState()
    val dynamicColor by vm.dynamicColor.collectAsState()
    val themeColor by vm.themeColor.collectAsState()
    val waveProgress by vm.waveProgress.collectAsState()
    val uiStyle by vm.uiStyle.collectAsState()
    var qualityMenuOpen by remember { mutableStateOf(false) }
    var styleMenuOpen by remember { mutableStateOf(false) }
    var editingUrl by remember { mutableStateOf(false) }
    var draftUrl by remember(backendUrl) { mutableStateOf(backendUrl) }

    Scaffold(
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
            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("使用本地后端代理", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                if (useBackend) "已启用 — 需运行 server/ 且 BASE_URL 可达"
                                else "关闭时直连网易云 (eapi 加密，推荐)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (useBackend) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = useBackend, onCheckedChange = { vm.setUseBackend(it) })
                    }
                    if (useBackend) {
                        Spacer(Modifier.height(12.dp))
                        if (editingUrl) {
                            OutlinedTextField(
                                value = draftUrl,
                                onValueChange = { draftUrl = it },
                                label = { Text("后端地址") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    draftUrl = backendUrl
                                    editingUrl = false
                                }) { Text("取消") }
                                Button(onClick = {
                                    vm.setBackendUrl(draftUrl)
                                    editingUrl = false
                                }) { Text("保存") }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(backendUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                TextButton(onClick = { editingUrl = true }) { Text("修改") }
                            }
                        }
                    }
                }
            }

            // ---- 播放 ----
            Spacer(Modifier.height(8.dp))
            SectionLabel("播放")
            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("默认音质", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                    Box {
                        TextButton(onClick = { qualityMenuOpen = true }) {
                            Text(QUALITY_OPTIONS.firstOrNull { it.first == quality }?.second ?: quality)
                        }
                        DropdownMenu(expanded = qualityMenuOpen, onDismissRequest = { qualityMenuOpen = false }) {
                            QUALITY_OPTIONS.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { vm.setAudioQuality(key); qualityMenuOpen = false }
                                )
                            }
                        }
                    }
                }
            }

            // ---- 主题 ----
            Spacer(Modifier.height(8.dp))
            SectionLabel("主题")

            // UI style switcher (Material3 ↔ Miuix). Persisted; the app reads
            // it at the root and rebuilds the entire NavHost when it changes.
            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("界面风格", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "切换 Material 3 与 MIUI 风格界面，切换后重启页面",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        TextButton(onClick = { styleMenuOpen = true }) {
                            Text(UiStyle.from(uiStyle).displayName)
                        }
                        DropdownMenu(expanded = styleMenuOpen, onDismissRequest = { styleMenuOpen = false }) {
                            UiStyle.entries.forEach { style ->
                                DropdownMenuItem(
                                    text = { Text(style.displayName) },
                                    onClick = { vm.setUiStyle(style.key); styleMenuOpen = false }
                                )
                            }
                        }
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp)) {
                    // Dark mode toggle
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("深色模式", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text("切换深色/浅色外观", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isDarkTheme, onCheckedChange = { vm.setIsDarkTheme(it) })
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    // Dynamic color toggle
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("动态取色 (Material You)", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text("基于壁纸自动生成主题色 (Android 12+)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = dynamicColor, onCheckedChange = { vm.setDynamicColor(it) })
                    }

                    // Color picker (visible when dynamic color is off or pre-Android 12)
                    if (!dynamicColor) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("主题色", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        // Color grid
                        val columns = 4
                        val rows = (THEME_COLORS.size + columns - 1) / columns
                        for (row in 0 until rows) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (col in 0 until columns) {
                                    val index = row * columns + col
                                    if (index < THEME_COLORS.size) {
                                        val (hex, label) = THEME_COLORS[index]
                                        val color = try {
                                            Color(android.graphics.Color.parseColor(hex))
                                        } catch (_: Exception) { MaterialTheme.colorScheme.primary }
                                        val selected = themeColor.equals(hex, ignoreCase = true)
                                        ColorSwatch(
                                            color = color,
                                            label = label,
                                            selected = selected,
                                            onClick = { vm.setThemeColor(hex) }
                                        )
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- 播放界面 ----
            Spacer(Modifier.height(8.dp))
            SectionLabel("播放界面")
            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("波浪进度条", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text("将进度条替换为动态波浪可视化效果", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = waveProgress, onCheckedChange = { vm.setWaveProgress(it) })
                }
            }

            // ---- 关于 ----
            Spacer(Modifier.height(8.dp))
            SectionLabel("关于")
            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AboutRow("应用", "HH音乐")
                    AboutRow("版本", "v1.3")
                    AboutRow("数据接口", "网易云音乐 (eapi 直连)")
                    AboutRow("参考", "GuitaristRin/Ncrust")
                    AboutRow("技术栈", "Kotlin · Compose · Media3")
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
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}

@Composable
private fun AboutRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(v, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    else Modifier
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(60.dp).padding(top = 4.dp),
        )
    }
}
