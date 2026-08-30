package com.hh.music.player.ui.miuix.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hh.music.player.ui.settings.SettingsViewModel
import com.hh.music.player.ui.theme.UiStyle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SwitchPreference
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixSettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel { SettingsViewModel(com.hh.music.player.ui.LocalStoreProvider.current) }
) {
    val useBackend by vm.useBackend.collectAsState()
    val isDarkTheme by vm.isDarkTheme.collectAsState()
    val dynamicColor by vm.dynamicColor.collectAsState()
    val waveProgress by vm.waveProgress.collectAsState()
    val uiStyle by vm.uiStyle.collectAsState()
    val quality by vm.audioQuality.collectAsState()
    val backendUrl by vm.backendUrl.collectAsState()
    var styleMenuOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var qualityMenuOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "设置",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
        ) {
            item { SmallTitle("数据源") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                    SwitchPreference(
                        checked = useBackend,
                        onCheckedChange = { vm.setUseBackend(it) },
                        title = "使用本地后端代理",
                        summary = if (useBackend) "已启用 — 需运行 server/ 且 BASE_URL 可达"
                                  else "关闭时直连网易云 (eapi 加密，推荐)"
                    )
                    if (useBackend) {
                        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(16.dp)) {
                            Text("后端地址", style = MiuixTheme.textStyles.body2)
                            Spacer(Modifier.height(4.dp))
                            Text(backendUrl, style = MiuixTheme.textStyles.footnote1)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)); SmallTitle("播放") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("默认音质", modifier = Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { qualityMenuOpen = true }) {
                                Text(qualityLabel(quality))
                            }
                            DropdownMenu(expanded = qualityMenuOpen, onDismissRequest = { qualityMenuOpen = false }) {
                                listOf(
                                    "standard" to "标准 (128k)",
                                    "higher" to "较高 (192k)",
                                    "exhigh" to "极高 (320k)",
                                    "lossless" to "无损 (FLAC)",
                                    "hires" to "Hi-Res"
                                ).forEach { (key, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = { vm.setAudioQuality(key); qualityMenuOpen = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)); SmallTitle("主题") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                    SwitchPreference(
                        checked = isDarkTheme,
                        onCheckedChange = { vm.setIsDarkTheme(it) },
                        title = "深色模式",
                        summary = "切换深色/浅色外观"
                    )
                    SwitchPreference(
                        checked = dynamicColor,
                        onCheckedChange = { vm.setDynamicColor(it) },
                        title = "动态取色",
                        summary = "Miuix 主题始终按种子色生成调色板"
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)); SmallTitle("界面风格") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                            Text("界面风格", style = MiuixTheme.textStyles.body1)
                            Text("切换 Material 3 与 MIUI 风格界面", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariant)
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
            }
            item { Spacer(Modifier.height(8.dp)); SmallTitle("播放界面") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                    SwitchPreference(
                        checked = waveProgress,
                        onCheckedChange = { vm.setWaveProgress(it) },
                        title = "波浪进度条",
                        summary = "将进度条替换为动态波浪可视化效果"
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)); SmallTitle("关于") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                    androidx.compose.foundation.layout.Column(modifier = Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                        InfoRow("应用", "HH音乐")
                        InfoRow("版本", "v1.3")
                        InfoRow("界面", "Miuix (HyperOS) — 另可选 Material 3")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
        Text(k, color = MiuixTheme.colorScheme.onSurfaceVariant)
        Text(v, color = MiuixTheme.colorScheme.onSurface)
    }
}

private fun qualityLabel(key: String): String = when (key) {
    "standard" -> "标准 (128k)"
    "higher" -> "较高 (192k)"
    "exhigh" -> "极高 (320k)"
    "lossless" -> "无损 (FLAC)"
    "hires" -> "Hi-Res"
    else -> key
}
