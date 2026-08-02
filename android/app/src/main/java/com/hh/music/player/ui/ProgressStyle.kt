package com.hh.music.player.ui

/** 播放页进度条的可选样式（M3E Progress indicators + SPICa 波形）。 */
enum class ProgressStyle(val key: String, val label: String) {
    SLIDER("slider", "滑块"),
    LINEAR("linear", "线性"),
    CIRCULAR("circular", "环形"),
    WAVEFORM("waveform", "波形");

    companion object {
        fun fromKey(key: String): ProgressStyle =
            entries.firstOrNull { it.key == key } ?: SLIDER
    }
}
