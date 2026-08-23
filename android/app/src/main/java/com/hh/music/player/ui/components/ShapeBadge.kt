package com.hh.music.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 项目统一的 M3E 徽章形状别名，集中管理避免具体形状名散落各处。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object BadgeShapes {
    val Clover: RoundedPolygon get() = MaterialShapes.Clover4Leaf
    val Burst: RoundedPolygon get() = MaterialShapes.SoftBurst
    val Sunny: RoundedPolygon get() = MaterialShapes.Sunny
    val Cookie: RoundedPolygon get() = MaterialShapes.Cookie9Sided
    val Flower: RoundedPolygon get() = MaterialShapes.Flower
}

/**
 * M3E 形状徽章：MaterialShapes 底座 + 居中图标。
 * 用于空态/错误态图标容器、首页快捷入口等场景。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapeBadge(
    icon: ImageVector,
    shape: RoundedPolygon,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 72.dp,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconSize: Dp = badgeSize * 0.44f
) {
    Box(
        modifier
            .size(badgeSize)
            .clip(shape.toShape())
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize), tint = tint)
    }
}
