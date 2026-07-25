package com.hh.music.player.ui.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * HH Music dimension/spacing system, inspired by SaltUI's structured layout.
 *
 * Provides consistent spacing, item sizing, and padding values so every
 * screen feels proportional.
 *
 * Layout reference:
 *   ╭──────────────────────────────────────────────╮
 *   │ ───── padding * 0.5 (outer gap)              │
 *   │     ╭──────────────────────────╮              │
 *   │     │ ──── subPadding          │              │
 *   │     │ [padding] Content        │              │
 *   │     │ ──── subPadding          │              │
 *   │     ╰──────────────────────────╯              │
 *   │ ───── padding * 0.5                           │
 *   ╰──────────────────────────────────────────────╯
 */
@Stable
class HHDimens(
    /** Minimum item height (rows, list items). */
    item: Dp,
    /** Icon size inside items. */
    itemIcon: Dp,
    /** Content padding (outer horizontal, container inset). */
    padding: Dp,
    /** Sub-padding — space between elements inside a container. */
    subPadding: Dp,
    /** Corner radius for small inner elements (overridden by [HHShapes]). */
    corner: Dp,
    /** Corner radius for dialogs. */
    dialogCorner: Dp,
) {
    val item by mutableStateOf(item, structuralEqualityPolicy())
    val itemIcon by mutableStateOf(itemIcon, structuralEqualityPolicy())
    val padding by mutableStateOf(padding, structuralEqualityPolicy())
    val subPadding by mutableStateOf(subPadding, structuralEqualityPolicy())
    val corner by mutableStateOf(corner, structuralEqualityPolicy())
    val dialogCorner by mutableStateOf(dialogCorner, structuralEqualityPolicy())

    companion object {
        fun default(
            item: Dp = 48.dp,
            itemIcon: Dp = 24.dp,
            padding: Dp = 16.dp,
            subPadding: Dp = 12.dp,
            corner: Dp = 12.dp,
            dialogCorner: Dp = 20.dp,
        ): HHDimens = HHDimens(
            item = item,
            itemIcon = itemIcon,
            padding = padding,
            subPadding = subPadding,
            corner = corner,
            dialogCorner = dialogCorner,
        )
    }
}
