package com.hh.music.player.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * An animated wave‑style progress bar that acts as a seekable playback slider.
 *
 * Renders [barCount] vertical bars whose heights animate in a phase‑shifted
 * wave pattern.  Bars before the current [progress] point are drawn in the
 * accent colour; bars after it are drawn in a dimmed surface‑variant colour.
 * The user can tap or drag anywhere on the bar to seek.
 *
 * @param progress  Current playback position as 0‑1 fraction.
 * @param onSeek    Called with the new progress (0‑1) when the user seeks.
 * @param barCount  Number of vertical bars in the visualizer.
 * @param height    Overall height of the visualizer.
 * @param modifier  Modifier applied to the root element.
 */
@Composable
fun WaveProgressBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    barCount: Int = 48,
    height: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    // Infinite animation for the wave phase
    val infiniteTransition = rememberInfiniteTransition(label = "wavePhase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (kotlin.math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhaseAnim",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {},
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                )
            }
    ) {
        val barWidth = size.width / barCount
        val maxBarHeight = size.height * 0.85f
        // Gap between bars as a fraction of barWidth
        val gapFraction = 0.35f
        val drawWidth = barWidth * (1f - gapFraction)
        val cornerR = CornerRadius(drawWidth / 2f)

        for (i in 0 until barCount) {
            // Normalised position along the bar (0..1)
            val pos = i.toFloat() / (barCount - 1).coerceAtLeast(1)
            // Wave amplitude: sin(phase + i * frequency) mapped to 0.3..1.0
            val wave = (sin(phase.toDouble() + i * 0.4) * 0.5 + 0.5).toFloat()
            val barHeight = (wave * 0.55f + 0.45f) * maxBarHeight

            val centerX = (i + 0.5f) * barWidth
            val isFilled = pos <= progress

            drawRoundRect(
                color = if (isFilled) primary else surfaceVariant,
                topLeft = Offset(centerX - drawWidth / 2f, size.height - barHeight),
                size = Size(drawWidth, barHeight),
                cornerRadius = cornerR,
            )
        }
    }
}
