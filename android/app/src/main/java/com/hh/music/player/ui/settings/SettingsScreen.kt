@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.hh.music.player.ui.settings

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.hh.music.player.BuildConfig
import com.hh.music.player.data.local.LocalStore
import com.hh.music.player.ui.ProgressStyle
import com.hh.music.player.ui.components.ArtworkImage
import com.hh.music.player.ui.theme.AppThemeColor
import com.hh.music.player.ui.theme.AppThemeMode
import com.hh.music.player.ui.theme.LyricFontScale

private val CACHE_OPTIONS = listOf(
    256 to "256MB",
    512 to "512MB",
    1024 to "1GB",
    2048 to "2GB",
    0 to "不限"
)

private val QUALITY_OPTIONS = listOf(
    "standard" to "标准 (128k)",
    "higher" to "较高 (192k)",
    "exhigh" to "极高 (320k)",
    "lossless" to "无损 (FLAC)",
    "hires" to "Hi-Res"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    store: LocalStore,
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel { SettingsViewModel(store) }
) {
    val useBackend by vm.useBackend.collectAsState()
    val quality by vm.audioQuality.collectAsState()
    val progressStyle by vm.progressStyle.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val themeColor by vm.themeColor.collectAsState()
    val dynamicColor by vm.dynamicColor.collectAsState()
    val autoCache by vm.autoCache.collectAsState()
    val cacheCapMb by vm.cacheCapMb.collectAsState()
    val showLyricTranslation by vm.showLyricTranslation.collectAsState()
    val showLyricRomanization by vm.showLyricRomanization.collectAsState()
    val lyricFontScale by vm.lyricFontScale.collectAsState()
    val userId by vm.userId.collectAsState()
    val nickname by vm.nickname.collectAsState()
    val avatarUrl by vm.avatarUrl.collectAsState()
    val loginState by vm.loginState.collectAsState()
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicColorActive = dynamicColorSupported && dynamicColor
    var showLoginDialog by remember { mutableStateOf(false) }

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
            // ---- 账号 ----
            SectionLabel("账号")
            ElevatedCard(Modifier.fillMaxWidth()) {
                if (userId > 0) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (avatarUrl.isNotBlank()) {
                            ArtworkImage(
                                url = avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp).clip(CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(nickname.ifBlank { "已登录" }, fontWeight = FontWeight.Medium)
                            Text(
                                "收藏操作将同步到云端",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { vm.logout() }) { Text("退出登录") }
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showLoginDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("登录网易云音乐", fontWeight = FontWeight.Medium)
                            Text(
                                "扫码登录后可云收藏同步、解锁会员音源",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

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

            // ---- 外观 ----
            Spacer(Modifier.height(8.dp))
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

            // ---- 歌词显示 ----
            Spacer(Modifier.height(8.dp))
            SectionLabel("歌词显示")
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Subtitles,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("歌词内容", fontWeight = FontWeight.Medium)
                            Text(
                                "翻译与罗马音开关，以及歌词字号",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "显示翻译",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = showLyricTranslation,
                            onCheckedChange = { vm.setShowLyricTranslation(it) }
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Subtitles,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "显示罗马音",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = showLyricRomanization,
                            onCheckedChange = { vm.setShowLyricRomanization(it) }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "歌词字号",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        LyricFontScale.entries.forEachIndexed { index, scale ->
                            SegmentedButton(
                                selected = lyricFontScale == scale,
                                onClick = { vm.setLyricFontScale(scale.key) },
                                shape = SegmentedButtonDefaults.itemShape(index, LyricFontScale.entries.size)
                            ) { Text(scale.label) }
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

            // ---- 离线缓存 ----
            Spacer(Modifier.height(8.dp))
            SectionLabel("离线缓存")
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("自动缓存", fontWeight = FontWeight.Medium)
                            Text(
                                "播放过的歌曲会自动保存到离线缓存",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoCache,
                            onCheckedChange = { vm.setAutoCache(it) }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "缓存上限",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CACHE_OPTIONS.forEach { (mb, label) ->
                            FilterChip(
                                selected = cacheCapMb == mb,
                                onClick = { vm.setCacheCapMb(mb) },
                                label = { Text(label) }
                            )
                        }
                    }
                    if (cacheCapMb !in CACHE_OPTIONS.map { it.first }) {
                        Text(
                            "当前上限 ${cacheCapMb}MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

    if (showLoginDialog) {
        LoginDialog(
            vm = vm,
            loginState = loginState,
            useBackend = useBackend,
            onDismiss = {
                vm.cancelLogin()
                showLoginDialog = false
            },
            onSuccess = { showLoginDialog = false }
        )
    }
}

@Composable
private fun LoginDialog(
    vm: SettingsViewModel,
    loginState: LoginUiState,
    useBackend: Boolean,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    // Kick off the QR generation once when the dialog opens.
    LaunchedEffect(Unit) { vm.startQrLogin() }
    // Close automatically on success; keep the last QR visible through AwaitingConfirm.
    var shownKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(loginState) {
        when (loginState) {
            is LoginUiState.Success -> onSuccess()
            is LoginUiState.AwaitingScan -> shownKey = loginState.qrKey
            else -> Unit
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        title = { Text("登录网易云音乐") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (useBackend) {
                    Text(
                        "当前为本地后端模式，登录仅支持直连模式；请先在「数据源」切回直连。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    when (loginState) {
                        is LoginUiState.Generating -> {
                            Spacer(Modifier.height(8.dp))
                            LoadingIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("正在生成二维码…", style = MaterialTheme.typography.bodySmall)
                        }
                        is LoginUiState.AwaitingScan, is LoginUiState.AwaitingConfirm, is LoginUiState.Error -> {
                            shownKey?.let { key ->
                                QrCodeImage(
                                    content = com.hh.music.player.network.LoginClient.qrContent(key),
                                    modifier = Modifier.size(220.dp)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            val hint = when (loginState) {
                                is LoginUiState.AwaitingConfirm -> "已扫码，请在手机上确认登录"
                                is LoginUiState.Error -> loginState.message
                                else -> "使用网易云音乐 APP 扫码登录"
                            }
                            Text(
                                hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (loginState is LoginUiState.Error) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { vm.retryFromError() }) { Text("刷新二维码") }
                        }
                        LoginUiState.Success, LoginUiState.Idle -> Unit
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    Text(
                        "无法扫码？粘贴浏览器 Cookie 中的 MUSIC_U",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    var cookieText by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = cookieText,
                        onValueChange = { cookieText = it },
                        enabled = loginState !is LoginUiState.Success,
                        placeholder = { Text("MUSIC_U=… 或纯令牌", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { vm.loginWithCookie(cookieText) },
                        enabled = cookieText.isNotBlank()
                    ) { Text("使用 Cookie 登录") }
                }
            }
        }
    )
}

/** Renders [content] as a QR code entirely on-device via ZXing. */
@Composable
private fun QrCodeImage(content: String, modifier: Modifier = Modifier) {
    val sizePx = 512
    val image = remember(content) {
        runCatching {
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val pixels = IntArray(sizePx * sizePx)
            for (y in 0 until sizePx) {
                for (x in 0 until sizePx) {
                    pixels[y * sizePx + x] = if (matrix[x, y]) Color.Black.toArgb() else Color.White.toArgb()
                }
            }
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
            }.asImageBitmap()
        }.getOrNull()
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(bitmap = image, contentDescription = "登录二维码", modifier = Modifier.fillMaxSize())
        } else {
            LoadingIndicator()
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
