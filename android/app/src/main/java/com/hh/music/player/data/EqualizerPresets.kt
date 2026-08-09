package com.hh.music.player.data

/**
 * Pure, Android-free equalizer preset logic: canonical per-band millibel curves,
 * resampling to the device band count, custom-band (de)serialization and the
 * "preset by name" alias table used to match device system presets.
 *
 * The Android audio-fx bridge ([EqualizerController]) consumes only these
 * functions, so the whole preset policy is unit-testable on the JVM.
 */
object EqualizerPresets {

    const val DEFAULT = "default"
    const val CUSTOM = "custom"

    /** Stable keys persisted in LocalStore, in display order. */
    val PRESETS = listOf(DEFAULT, "pop", "rock", "jazz", "classical", "vocal", "bass", CUSTOM)

    fun displayName(key: String): String = when (key) {
        DEFAULT -> "默认"
        "pop" -> "流行"
        "rock" -> "摇滚"
        "jazz" -> "爵士"
        "classical" -> "古典"
        "vocal" -> "人声"
        "bass" -> "低音增强"
        CUSTOM -> "自定义"
        else -> key
    }

    /** Aliases (case-insensitive, match-if-contained) for mapping our presets to device presets. */
    private val SYSTEM_ALIASES: Map<String, List<String>> = mapOf(
        DEFAULT to listOf("normal", "flat", "default", "原声", "默认"),
        "pop" to listOf("pop", "流行"),
        "rock" to listOf("rock", "摇滚"),
        "jazz" to listOf("jazz", "爵士"),
        "classical" to listOf("classical", "classic", "古典"),
        "vocal" to listOf("vocal", "voice", "人声"),
        "bass" to listOf("bass", "bass booster", "低音")
    )

    /** Canonical 10-band curves in millibels per preset key; unknown keys → flat. */
    private val CURVES: Map<String, List<Int>> = mapOf(
        "pop" to listOf(80, 100, 120, 150, 100, 0, -30, -30, 0, 40),
        "rock" to listOf(400, 300, 80, -150, -120, 120, 320, 400, 400, 380),
        "jazz" to listOf(250, 220, -40, -80, 0, 80, 200, 240, 280, 320),
        "classical" to listOf(350, 280, 40, -60, -40, -60, -40, 60, 80, 200),
        "vocal" to listOf(-160, -100, -20, 180, 260, 340, 260, 120, 0, -60),
        "bass" to listOf(520, 480, 400, 240, 40, -60, -120, -100, -40, 0)
    )

    /**
     * Resolve the per-band level in millibels for [bandCount] bands.
     *  - "default" / unknown preset → flat (all 0).
     *  - "custom" → [customBands], padded/truncated to [bandCount].
     *  - built-in presets → canonical curve resampled to [bandCount].
     */
    fun resolveBandLevels(
        preset: String,
        bandCount: Int,
        customBands: List<Int> = emptyList()
    ): List<Int> {
        if (bandCount <= 0) return emptyList()
        return when (preset) {
            CUSTOM -> List(bandCount) { i -> customBands.getOrElse(i) { 0 } }
            DEFAULT -> List(bandCount) { 0 }
            else -> {
                val curve = CURVES[preset] ?: return List(bandCount) { 0 }
                if (curve.size == bandCount) curve
                else if (bandCount == 1) listOf(curve[curve.size / 2])
                else List(bandCount) { i ->
                    val src = (i.toLong() * (curve.size - 1) / (bandCount - 1).toLong()).toInt()
                    curve[src.coerceIn(0, curve.lastIndex)]
                }
            }
        }
    }

    /** "120,80,-40" → [120, 80, -40]; blank/garbage → empty (flat fallback). */
    fun parseBands(s: String?): List<Int> =
        s?.trim()?.takeIf { it.isNotEmpty() }
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?: emptyList()

    fun serializeBands(bands: List<Int>): String = bands.joinToString(",")

    fun systemAliases(preset: String): List<String> = SYSTEM_ALIASES[preset] ?: emptyList()
}
