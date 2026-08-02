package com.hh.music.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * 由歌曲 id 决定的一组确定性的伪波形柱（0f..1f），无需真正解码音频。
 * 灵感来自 SPICaMusic_Android 的 AudioWaveSlider。
 */
@Composable
fun rememberWaveformAmplitudes(seed: Long, barCount: Int = 72): List<Float> =
    remember(seed) {
        val random = Random(seed)
        val raw = List(barCount) { random.nextInt(45, 255).toFloat() }
        val max = raw.maxOrNull() ?: 1f
        raw.map { it / max }
    }

/**
 * SPICaMusic 风格波形进度条：可拖动的 Slider，轨道绘制为波形柱，
 * 已播放部分用主题色覆盖（BlendMode.SrcAtop）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveformSlider(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    amplitudes: List<Float>,
    spikeWidth: Dp = 3.dp,
    spikePadding: Dp = 2.dp,
    spikeRadius: Dp = 1.5.dp,
) {
    val animatedProgress =
        animateFloatAsState(
            progress.coerceIn(0f, 1f),
            tween(durationMillis = 120),
            label = "waveform_progress"
        ).value
    val waveformColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)
    val progressColor = MaterialTheme.colorScheme.primary

    Slider(
        value = animatedProgress,
        onValueChange = onProgressChange,
        onValueChangeFinished = onProgressChangeFinished,
        modifier = modifier,
        thumb = {},
        track = {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            ) {
                val spikeWidthPx = spikeWidth.toPx()
                val spikeTotalPx = (spikeWidth + spikePadding).toPx()
                val spikeCount = (size.width / spikeTotalPx).toInt().coerceAtLeast(1)
                val minHeight = 3.dp.toPx().coerceAtMost(size.height)
                repeat(spikeCount) { index ->
                    val sample =
                        if (amplitudes.isEmpty()) 0.35f
                        else amplitudes[
                            (index.toLong() * amplitudes.size / spikeCount)
                                .toInt()
                                .coerceIn(0, amplitudes.lastIndex)
                        ]
                    val amplitude =
                        (sample * size.height * 0.9f).coerceIn(minHeight, size.height)
                    drawRoundRect(
                        color = waveformColor,
                        topLeft = Offset(index * spikeTotalPx, size.height / 2f - amplitude / 2f),
                        size = Size(spikeWidthPx, amplitude),
                        cornerRadius = CornerRadius(spikeRadius.toPx(), spikeRadius.toPx())
                    )
                }
                drawRect(
                    color = progressColor,
                    size = Size(animatedProgress * size.width, size.height),
                    blendMode = BlendMode.SrcAtop
                )
            }
        },
        valueRange = 0f..1f
    )
}
