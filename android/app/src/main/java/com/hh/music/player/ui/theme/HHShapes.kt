package com.hh.music.player.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp

/**
 * HH Music shape system — full Material Design 3 5‑tier shape scale.
 *
 * Mirrors the M3 shape specification (extraSmall → extraLarge) so every
 * Surface / Card / Sheet / Button gets a consistent rounded corner.
 *
 * @see <a href="https://m3.material.io/styles/shape/shape-scale">M3 shape scale</a>
 */
@Stable
class HHShapes(
    /** Extra small — chips, small badges, inner elements (e.g. 4dp). */
    extraSmall: CornerBasedShape,
    /** Small — buttons, text fields, search bars (e.g. 8dp). */
    small: CornerBasedShape,
    /** Medium — cards, dialogs, bottom sheets (e.g. 12dp). */
    medium: CornerBasedShape,
    /** Large — modal sheets, side sheets, navigation drawers (e.g. 16dp). */
    large: CornerBasedShape,
    /** Extra large — FAB, player cover, hero images (e.g. 24dp). */
    extraLarge: CornerBasedShape,
) {
    val extraSmall by mutableStateOf(extraSmall)
    val small by mutableStateOf(small)
    val medium by mutableStateOf(medium)
    val large by mutableStateOf(large)
    val extraLarge by mutableStateOf(extraLarge)

    /** Build a M3 [Shapes] instance for [MaterialTheme]. */
    fun toMaterialShapes() = Shapes(
        extraSmall = extraSmall,
        small = small,
        medium = medium,
        large = large,
        extraLarge = extraLarge,
    )

    companion object {
        /** Default M3 shape values. */
        fun default(
            extraSmall: CornerBasedShape = HHShapesDefaults.extraSmall,
            small: CornerBasedShape = HHShapesDefaults.small,
            medium: CornerBasedShape = HHShapesDefaults.medium,
            large: CornerBasedShape = HHShapesDefaults.large,
            extraLarge: CornerBasedShape = HHShapesDefaults.extraLarge,
        ): HHShapes = HHShapes(
            extraSmall = extraSmall,
            small = small,
            medium = medium,
            large = large,
            extraLarge = extraLarge,
        )
    }
}

object HHShapesDefaults {
    val extraSmall: CornerBasedShape = RoundedCornerShape(4.dp)
    val small: CornerBasedShape = RoundedCornerShape(8.dp)
    val medium: CornerBasedShape = RoundedCornerShape(12.dp)
    val large: CornerBasedShape = RoundedCornerShape(16.dp)
    val extraLarge: CornerBasedShape = RoundedCornerShape(24.dp)
}
