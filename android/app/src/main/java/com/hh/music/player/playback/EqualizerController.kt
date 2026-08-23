package com.hh.music.player.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import com.hh.music.player.data.EqualizerPresets
import com.hh.music.player.data.local.LocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Bridge between the persisted audio-effect settings (LocalStore) and the
 * platform effects attached to the fixed audio session created by [PlaybackService]:
 * the band equalizer, plus a bass boost and a virtualizer (v1.7).
 *
 * - Listens to enabled / preset / custom-bands / strength flows and applies them instantly.
 * - Presets are matched to device system presets by name alias; unknown devices
 *   fall back to our canonical curves (flat for "默认") so nothing breaks.
 * - Every platform call is wrapped in runCatching: unsupported devices simply
 *   report the corresponding availability flow as false and the UI disables the
 *   controls instead of crashing.
 */
class EqualizerController(
    private val store: LocalStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var eq: Equalizer? = null

    @Volatile
    private var bass: BassBoost? = null

    @Volatile
    private var virt: Virtualizer? = null

    @Volatile
    private var sessionId: Int = 0

    private val _isAvailable = MutableStateFlow(false)
    /** Whether an Equalizer object is currently attached and usable. */
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _bassAvailable = MutableStateFlow(false)
    /** Whether a BassBoost object is currently attached and supports strength. */
    val bassAvailable: StateFlow<Boolean> = _bassAvailable.asStateFlow()

    private val _virtualizerAvailable = MutableStateFlow(false)
    /** Whether a Virtualizer object is currently attached and supports strength. */
    val virtualizerAvailable: StateFlow<Boolean> = _virtualizerAvailable.asStateFlow()

    private val _bandCount = MutableStateFlow(0)
    val bandCount: StateFlow<Int> = _bandCount.asStateFlow()

    private val _bandFreqs = MutableStateFlow<List<Int>>(emptyList())
    /** Device center frequencies (Hz) per band, for the custom-band UI. */
    val bandFreqs: StateFlow<List<Int>> = _bandFreqs.asStateFlow()

    init {
        scope.launch {
            combine(
                store.equalizerEnabled,
                store.equalizerPreset,
                store.equalizerBands
            ) { enabled, presetKey, bandsString ->
                Triple(enabled, presetKey, EqualizerPresets.parseBands(bandsString))
            }.collectLatest { (enabled, presetKey, custom) ->
                apply(enabled, presetKey, custom)
            }
        }
        scope.launch {
            combine(
                store.bassBoostEnabled,
                store.bassBoostStrength
            ) { enabled, strength -> enabled to strength }
                .collectLatest { (enabled, strength) -> applyBass(enabled, strength) }
        }
        scope.launch {
            combine(
                store.virtualizerEnabled,
                store.virtualizerStrength
            ) { enabled, strength -> enabled to strength }
                .collectLatest { (enabled, strength) -> applyVirtualizer(enabled, strength) }
        }
    }

    /** Attach all supported effects to the audio session owned by ExoPlayer. */
    @Synchronized
    fun attachTo(audioSessionId: Int) {
        sessionId = audioSessionId
        detach()
        val create: Equalizer? = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        if (create == null) {
            _isAvailable.value = false
        } else {
            eq = create
            _isAvailable.value = true
            _bandCount.value = runCatching { create.numberOfBands.toInt() }.getOrDefault(0)
            _bandFreqs.value = runCatching {
                List(create.numberOfBands.toInt()) { i -> create.getCenterFreq(i.toShort()) }
            }.getOrDefault(emptyList())
        }

        // Strength-capable check happens at creation; unsupported devices stay null.
        bass = runCatching {
            val b = BassBoost(0, audioSessionId)
            if (b.strengthSupported) b else { runCatching { b.release() }; null }
        }.getOrNull()
        _bassAvailable.value = bass != null

        virt = runCatching {
            val v = Virtualizer(0, audioSessionId)
            if (v.strengthSupported) v else { runCatching { v.release() }; null }
        }.getOrNull()
        _virtualizerAvailable.value = virt != null

        applyFromStore()
    }

    /** Release every platform effect and mark the features unavailable. */
    @Synchronized
    fun detach() {
        eq?.release()
        eq = null
        bass?.release()
        bass = null
        virt?.release()
        virt = null
        _isAvailable.value = false
        _bassAvailable.value = false
        _virtualizerAvailable.value = false
    }

    @Synchronized
    fun isSession(sessionId: Int): Boolean = sessionId != 0 && this.sessionId == sessionId

    private fun applyFromStore() {
        scope.launch {
            val enabled = store.equalizerEnabled.first()
            val preset = store.equalizerPreset.first()
            val custom = EqualizerPresets.parseBands(store.equalizerBands.first())
            apply(enabled, preset, custom)

            val bassOn = store.bassBoostEnabled.first()
            val bassLevel = store.bassBoostStrength.first()
            applyBass(bassOn, bassLevel)

            val virtOn = store.virtualizerEnabled.first()
            val virtLevel = store.virtualizerStrength.first()
            applyVirtualizer(virtOn, virtLevel)
        }
    }

    private fun apply(enabled: Boolean, presetKey: String, customBands: List<Int>) {
        val e = eq ?: return
        runCatching {
            if (!enabled) {
                e.enabled = false
                return@runCatching
            }
            e.enabled = true
            val systemIndex = systemPresetIndex(e, presetKey)
            if (systemIndex >= 0) {
                e.usePreset(systemIndex.toShort())
            } else {
                applyBandLevels(e, EqualizerPresets.resolveBandLevels(presetKey, e.numberOfBands.toInt(), customBands))
            }
        }
    }

    /** Best-effort match of our preset to a system preset by name alias; -1 when none. */
    private fun systemPresetIndex(e: Equalizer, presetKey: String): Int {
        val aliases = EqualizerPresets.systemAliases(presetKey)
        if (aliases.isEmpty()) return -1
        val count = runCatching { e.numberOfPresets.toInt() }.getOrDefault(0)
        for (i in 0 until count) {
            val name = runCatching { e.getPresetName(i.toShort()) }
                .getOrNull()?.lowercase()?.trim() ?: continue
            if (aliases.any { name.contains(it.lowercase()) }) return i
        }
        return -1
    }

    private fun applyBandLevels(e: Equalizer, levels: List<Int>) {
        val range = runCatching { e.bandLevelRange }.getOrNull() ?: shortArrayOf(-1500, 1500)
        val min = range.getOrElse(0) { -1500 }.toInt()
        val max = range.getOrElse(1) { 1500 }.toInt()
        for ((i, level) in levels.withIndex()) {
            if (i >= e.numberOfBands) break
            val clamped = level.coerceIn(min, max)
            e.setBandLevel(i.toShort(), clamped.toShort())
        }
    }

    private fun applyBass(enabled: Boolean, strength: Int) {
        val b = bass ?: return
        runCatching {
            b.enabled = enabled
            if (enabled && b.strengthSupported) {
                b.setStrength(strength.coerceIn(0, 1000).toShort())
            }
        }
    }

    private fun applyVirtualizer(enabled: Boolean, strength: Int) {
        val v = virt ?: return
        runCatching {
            v.enabled = enabled
            if (enabled && v.strengthSupported) {
                v.setStrength(strength.coerceIn(0, 1000).toShort())
            }
        }
    }
}
