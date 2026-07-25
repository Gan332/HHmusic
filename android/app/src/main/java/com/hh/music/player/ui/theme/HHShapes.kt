package com.hh.music.player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Shape

/**
 * HH Music shape system — consistent rounded corners inspired by SaltUI.
 *
 * Used across cards, sheets, dialogs, and list items to create a cohesive,
 * friendly feel. SaltUI's signature is generous rounding on containers
 * while keeping inner elements tighter.
 */
@Stable
class HHShapes(
    /** Small — inner elements, chips, small cards (e.g. 6dp). */
    small: Shape,
    /** Medium — cards, rounded columns, list sections (e.g. 12dp). */
    medium: Shape,
    /** Large — dialogs, bottom sheets, full-width containers (e.g. 20dp). */
    large: Shape,
    /** Extra large — player cover, hero images (e.g. 28dp). */
    extraLarge: Shape,
) {
    val small by mutableStateOf(small)
    val medium by mutableStateOf(medium)
    val large by mutableStateOf(large)
    val extraLarge by mutableStateOf(extraLarge)

    companion object {
        fun default(
            small: Shape = HHShapesDefaults.small,
            medium: Shape = HHShapesDefaults.medium,
            large: Shape = HHShapesDefaults.large,
            extraLarge: Shape = HHShapesDefaults.extraLarge,
        ): HHShapes = HHShapes(
            small = small,
            medium = medium,
            large = large,
            extraLarge = extraLarge,
        )
    }
}

object HHShapesDefaults {
    val small: Shape = RoundedCornerShape(6.dp)
    val medium: Shape = RoundedCornerShape(12.dp)
    val large: Shape = RoundedCornerShape(20.dp)
    val extraLarge: Shape = RoundedCornerShape(28.dp)
}
