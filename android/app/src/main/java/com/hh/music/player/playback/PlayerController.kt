package com.hh.music.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Song
import com.hh.music.player.data.local.LocalStore
import com.hh.music.player.data.offline.DownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Logical playback modes shown in the UI. */
enum class PlayMode(val key: String) {
    SEQUENCE("sequence"), REPEAT_ONE("repeat_one"), SHUFFLE("shuffle");
    companion object {
        fun from(key: String?): PlayMode = entries.firstOrNull { it.key == key } ?: SEQUENCE
    }
}

/** How the sleep timer counts down: wall-clock minutes, or until the current track ends. */
enum class SleepTimerMode { MINUTES, END_OF_TRACK }

/** Why a track failed to play — drives the message, the retry affordance and the auto-skip policy. */
enum class FailureCategory { NETWORK, NO_URL, CONTENT, UNKNOWN }

/** A user-visible playback failure (VIP / regional restriction / network error). */
data class PlaybackError(
    val songId: Long,
    val songName: String,
    val message: String,
    val category: FailureCategory = FailureCategory.UNKNOWN,
    /** Whether "retry this song" could plausibly help. */
    val retryable: Boolean = true
)

/**
 * Client-side bridge to the PlaybackService MediaSession.
 *
 * Performance notes (v1.3):
 *  - Position progress is throttled to ~1s and only emitted when it actually changes;
 *    we never write the StateFlow while paused (the slider stays put).
 *  - Resolved playback URLs are cached per song id and reused, so switching tracks back
 *    doesn't re-fetch. resolveUrlFor updates only the URI via replaceMediaItem and never
 *    re-prepares mid-playback, avoiding audio restarts and buffer hiccups.
 *  - currentLineIndex for lyrics is derived in the UI from position, not stored here,
 *    and the UI throttles its recomposition scope.
 */
class PlayerController(
    private val context: Context,
    private val repository: MusicRepository,
    private val local: LocalStore? = null,
    private val downloadManager: DownloadManager? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController?
        get() = controllerFuture?.takeIf { it.isDone }?.let {
            runCatching { it.get() }.getOrNull()
        }

    /** Queue waiting for the MediaController to finish connecting (played once connected). */
    private var pendingQueue: Pair<List<Song>, Int>? = null
    private var connectRetries = 0
    private var retryJob: Job? = null

    /** Once released the controller must never reconnect or start new work. */
    @Volatile private var released = false

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _playMode = MutableStateFlow(PlayMode.SEQUENCE)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val _playbackError = MutableStateFlow<PlaybackError?>(null)
    val playbackError: StateFlow<PlaybackError?> = _playbackError.asStateFlow()

    // ---- v1.5: playback speed (persisted, default 1.0) ----
    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    // ---- v1.5: sleep timer (in-memory only; not restored across restarts) ----
    private val _sleepTimerRemaining = MutableStateFlow<Long?>(null)
    /** Remaining ms until the timer fires, or null when inactive. */
    val sleepTimerRemaining: StateFlow<Long?> = _sleepTimerRemaining.asStateFlow()
    private var sleepTimerMode = SleepTimerMode.MINUTES
    private var sleepTimerJob: Job? = null

    private var listener: Player.Listener? = null

    /** id -> already-resolved playable url, so we don't re-fetch on every track revisit. */
    private val resolvedUrls = mutableMapOf<Long, String>()

    /** ids whose URL resolution is still in flight (used to avoid auto-skipping them). */
    private val resolvingIds = mutableSetOf<Long>()

    /** Consecutive playback failures per song id — bounds runaway auto-skipping. */
    private val failureOf = mutableMapOf<Long, Int>()

    /** True while we auto-advanced after an error; the resulting transition must not be recorded as user choice. */
    private var autoAdvancing = false

    /** True while re-attaching a persisted queue after a cold start; no "recent" writes for restores. */
    private var restoringAfterBoot = false

    /** Playback position restored from a persisted queue, applied once the controller connects. */
    private var restoredPositionMs = 0L

    /** Last position persisted to DataStore; position writes are throttled to ~10s while playing. */
    private var lastPersistedPositionMs = 0L

    private val _resolvingCurrent = MutableStateFlow(false)
    /** True while the CURRENT track's playable URL is still being fetched. */
    val resolvingCurrent: StateFlow<Boolean> = _resolvingCurrent.asStateFlow()

    init {
        connect()
        startPositionPolling()
        observePersistedPlayMode()
        observePersistedSpeed()
        restoreQueue()
    }

    /**
     * Connects to the playback service. On failure (e.g. service not yet started or
     * temporarily unreachable) retries with an exponential backoff capped at ~32s
     * instead of busy-looping every 300ms like v1.3 did.
     */
    private fun connect() {
        if (released) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture?.addListener({
            val c = controller
            if (c != null) {
                connectRetries = 0
                wireListener(c)
                applySpeedToPlayer()
                pendingQueue?.let { (songs, idx) ->
                    pendingQueue = null
                    playQueue(songs, idx)
                } ?: attachRestoredQueue(c)
            } else {
                val backoffMs = 1000L shl connectRetries.coerceAtMost(5)
                connectRetries++
                retryJob?.cancel()
                retryJob = scope.launch { delay(backoffMs); connect() }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** On first connect, hand the persisted queue to the session (paused, not autoplayed). */
    private fun attachRestoredQueue(c: MediaController) {
        val q = _queue.value
        if (q.isEmpty() || c.mediaItemCount > 0) return
        val idx = _currentIndex.value.coerceIn(0, q.lastIndex)
        restoringAfterBoot = true // restored playback must not touch "recently played"
        c.setMediaItems(
            q.map { it.toMediaItem(it.resolvedOrPlaceholder()) },
            idx,
            restoredPositionMs.coerceAtLeast(0L)
        )
        applyPlayModeToPlayer()
        applySpeedToPlayer()
        _currentSong.value?.let { resolveUrlFor(it) }
        prefetchNext()
    }

    private fun wireListener(c: MediaController) {
        if (listener != null) return
        listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = c.currentMediaItemIndex
                if (idx == C.INDEX_UNSET || idx >= _queue.value.size) {
                    _currentIndex.value = -1
                    _currentSong.value = null
                    return
                }
                _currentIndex.value = idx
                val song = _queue.value[idx]
                _currentSong.value = song
                // Only record "recently played" for user-initiated changes: never for
                // silent restores after reboot, nor for error auto-skips.
                when {
                    autoAdvancing -> autoAdvancing = false
                    restoringAfterBoot -> restoringAfterBoot = false
                    else -> recordRecent(song)
                }
                song.let { resolveUrlFor(it) }
                downloadManager?.markPlaying(setOfNotNull(song.id))
                song.let { downloadManager?.touchPlayed(it.id) }
                // Persist with the controller's actual position (a restored queue would
                // otherwise be wiped back to 0 by this transition callback).
                persistQueue(_queue.value, idx, c.currentPosition.coerceAtLeast(0L))
                prefetchNext()
                // v1.5 sleep timer "end of track": the player just auto-advanced
                // because the current track finished (sequence/shuffle) → fade + pause.
                if (sleepTimerMode == SleepTimerMode.END_OF_TRACK &&
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && !autoAdvancing
                ) {
                    fadeOutAndPause()
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    // Keep the offline cache's "currently playing" protection + LRU
                    // timestamp pointing at the track that is actually audible.
                    val idx = c.currentMediaItemIndex
                    _queue.value.getOrNull(idx)?.let { song ->
                        downloadManager?.markPlaying(setOf(song.id))
                        downloadManager?.touchPlayed(song.id)
                    }
                    // Successful playback resets the consecutive-failure journal.
                    failureOf.clear()
                } else {
                    // Persist the exact position on pause/stop so we can resume it later.
                    val idx = c.currentMediaItemIndex
                    if (idx in _queue.value.indices && c.mediaItemCount > 0) {
                        val pos = c.currentPosition.coerceAtLeast(0)
                        if (pos != lastPersistedPositionMs) {
                            lastPersistedPositionMs = pos
                            persistQueue(_queue.value, idx, pos)
                        }
                    }
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                // Reflect true duration only when known.
                if (c.duration > 0) _durationMs.value = c.duration
                // Sleep timer "end of track": queue finished by itself (repeat off) —
                // nothing more to wait for, disarm the countdown quietly.
                if (state == Player.STATE_ENDED && sleepTimerMode == SleepTimerMode.END_OF_TRACK) {
                    stopSleepTimer()
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val song = _queue.value.getOrNull(c.currentMediaItemIndex)
                // Failure safety net: if the current item's URL is still being resolved
                // (placeholder not swapped yet), give it one more chance.
                if (song != null && resolvingIds.remove(song.id)) {
                    updateResolvingCurrent()
                    c.prepare()
                    return
                }
                val category = classify(error.errorCode)
                val failures = (song?.id?.let { failureOf[it] } ?: 0) + 1
                song?.let { failureOf[it.id] = failures }
                // Auto-skip, but bounded: the same song skipping itself more than
                // MAX_CONSECUTIVE_AUTO_SKIPS times stops advancing to avoid a
                // fast skip-loop between two broken tracks.
                if (song != null && PlaybackEngine.shouldAutoSkip(failures, c.mediaItemCount)) {
                    resolvedUrls.remove(song.id) // drop possibly-dead cache; retry must re-resolve
                    autoAdvancing = true
                    emitPlaybackError(
                        song,
                        message = failureMessage(category, autoSkipped = true),
                        category = category
                    )
                    c.seekToNextMediaItem()
                } else {
                    emitPlaybackError(
                        song,
                        message = failureMessage(category, autoSkipped = false),
                        category = category
                    )
                }
            }
        }.also { c.addListener(it) }
        applyPlayModeToPlayer()
        applySpeedToPlayer()
    }

    private fun failureMessage(category: FailureCategory, autoSkipped: Boolean): String {
        val base = when (category) {
            FailureCategory.NETWORK -> "网络异常，播放中断"
            FailureCategory.NO_URL -> "无法获取播放地址（可能为会员或版权受限）"
            FailureCategory.CONTENT -> "音频无法解析（文件损坏或格式不支持）"
            FailureCategory.UNKNOWN -> "播放失败"
        }
        return if (autoSkipped) "$base，已自动跳到下一首" else "$base，请重试或切换歌曲"
    }

    private fun classify(errorCode: Int): FailureCategory = when (errorCode) {
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_TIMEOUT,
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        -> FailureCategory.NETWORK

        androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_DATA,
        androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_OTHER,
        androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_OTHER,
        -> FailureCategory.CONTENT

        else -> FailureCategory.UNKNOWN
    }

    private fun emitPlaybackError(
        song: Song?,
        message: String,
        category: FailureCategory = FailureCategory.UNKNOWN
    ) {
        _playbackError.value = PlaybackError(
            songId = song?.id ?: -1L,
            songName = song?.name ?: "未知歌曲",
            message = message,
            category = category,
            retryable = category != FailureCategory.CONTENT
        )
    }

    fun clearPlaybackError() {
        _playbackError.value = null
    }

/** Throttled, diff-based progress refresh — the #1 cause of UI jank before v1.3. */
    private fun startPositionPolling() {
        scope.launch {
            while (true) {
                delay(500)
                val c = controller ?: continue
                // Only advance our StateFlow while actually playing; when paused, the
                // slider must freeze, so we avoid pushing identical 500ms-bound values.
                if (!c.isPlaying) continue
                val pos = c.currentPosition.coerceAtLeast(0)
                // Coarse-grain to whole seconds for slider/labels; sub-second jitter was
                // recomposing the whole player screen hundreds of times a minute.
                val coarse = (pos / 1000L) * 1000L
                if (coarse != _positionMs.value) _positionMs.value = coarse
                if (c.duration > 0 && c.duration != _durationMs.value) _durationMs.value = c.duration
                // Throttled position persistence (~10s) so a killed app resumes mid-song.
                if (coarse - lastPersistedPositionMs >= 10_000L) {
                    lastPersistedPositionMs = coarse
                    val idx = c.currentMediaItemIndex
                    if (idx in _queue.value.indices) persistQueue(_queue.value, idx, coarse)
                }
            }
        }
    }

    private fun observePersistedPlayMode() {
        scope.launch {
            local?.playMode?.collectLatest { key ->
                _playMode.value = PlayMode.from(key)
                applyPlayModeToPlayer()
            }
        }
    }

    private fun observePersistedSpeed() {
        scope.launch {
            local?.speed?.collectLatest { s ->
                _speed.value = s
                applySpeedToPlayer()
            }
        }
    }

    /** Speed levels offered by the UI. */
    fun speedOptions(): List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    /** Apply the current speed to the connected player without touching play state. */
    private fun applySpeedToPlayer() {
        controller?.setPlaybackSpeed(_speed.value)
    }

    fun setSpeed(speed: Float) {
        val s = speed.coerceIn(0.5f, 2f)
        _speed.value = s
        applySpeedToPlayer()
        scope.launch { local?.setSpeed(s) }
    }

    fun cyclePlayMode() {
        val next = when (_playMode.value) {
            PlayMode.SEQUENCE -> PlayMode.REPEAT_ONE
            PlayMode.REPEAT_ONE -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.SEQUENCE
        }
        _playMode.value = next
        scope.launch { local?.setPlayMode(next.key) }
        applyPlayModeToPlayer()
    }

    private fun applyPlayModeToPlayer() {
        val c = controller ?: return
        when (_playMode.value) {
            PlayMode.SEQUENCE -> { c.repeatMode = Player.REPEAT_MODE_OFF; c.shuffleModeEnabled = false }
            PlayMode.REPEAT_ONE -> { c.repeatMode = Player.REPEAT_MODE_ONE; c.shuffleModeEnabled = false }
            PlayMode.SHUFFLE -> { c.repeatMode = Player.REPEAT_MODE_ALL; c.shuffleModeEnabled = true }
        }
    }

private fun recordRecent(song: Song) {
        scope.launch { local?.addRecent(song) }
    }

/** Replace the queue and start playing at [startIndex]. */
    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        _queue.value = songs
        _currentIndex.value = startIndex
        _currentSong.value = songs[startIndex]
        recordRecent(songs[startIndex])
        persistQueue(songs, startIndex, 0L)
        autoAdvancing = false
        restoringAfterBoot = false
        failureOf.clear()
        resetPositionJournal()
        val c = controller
        if (c == null) {
            // Controller not connected yet — queue the request and let connect() replay
            // it once, instead of retrying in a tight loop every 300ms.
            pendingQueue = songs to startIndex
            return
        }
        pendingQueue = null
        c.setMediaItems(songs.map { it.toMediaItem(it.resolvedOrPlaceholder()) }, startIndex, 0L)
        applyPlayModeToPlayer()
        applySpeedToPlayer()
        c.prepare()
        c.playWhenReady = true
        resolveUrlFor(songs[startIndex])
        prefetchNext()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    // ---- v1.5 sleep timer ----

    /** Start a wall-clock sleep timer that fades out and pauses [minutes] from now. */
    fun startSleepTimer(minutes: Int) {
        if (minutes <= 0) return
        sleepTimerMode = SleepTimerMode.MINUTES
        sleepTimerJob?.cancel()
        _sleepTimerRemaining.value = minutes * 60_000L
        sleepTimerJob = scope.launch {
            while (sleepTimerMode == SleepTimerMode.MINUTES) {
                delay(1000)
                val remaining = (_sleepTimerRemaining.value ?: 0L) - 1000L
                if (remaining <= 0L) {
                    fadeOutAndPause()
                    return@launch
                }
                _sleepTimerRemaining.value = remaining
            }
        }
    }

    /**
     * Start a "pause when the current track finishes" timer.
     * - SEQUENCE / SHUFFLE: the player auto-advance callback pauses right at the
     *   end of the track (before the next one really starts).
     * - REPEAT_ONE: polls for the loop wrap (position ≥ duration-0.5s) and pauses
     *   — the track gets one full extra loop, then stops.
     */
    fun startSleepTimerToEndOfTrack() {
        sleepTimerMode = SleepTimerMode.END_OF_TRACK
        sleepTimerJob?.cancel()
        _sleepTimerRemaining.value = durationMs.value.takeIf { it > 0 }
        sleepTimerJob = scope.launch {
            while (isActive) {
                if (sleepTimerMode != SleepTimerMode.END_OF_TRACK) return@launch
                delay(250)
                val c = controller ?: continue
                if (c.duration > 0 && c.isPlaying) {
                    _sleepTimerRemaining.value = (c.duration - c.currentPosition).coerceAtLeast(0L)
                }
                if (_playMode.value == PlayMode.REPEAT_ONE &&
                    c.isPlaying && c.duration > 0 && c.currentPosition >= c.duration - 500L
                ) {
                    fadeOutAndPause()
                    return@launch
                }
            }
        }
    }

    fun cancelSleepTimer() {
        stopSleepTimer()
    }

    private fun stopSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerMode = SleepTimerMode.MINUTES
        _sleepTimerRemaining.value = null
    }

    /** 1.5s linear volume fade to zero, then pause; volume restored for next play. */
    private fun fadeOutAndPause() {
        stopSleepTimer()
        val c = controller ?: return
        scope.launch {
            val steps = 12
            val start = runCatching { c.volume }.getOrDefault(1f).coerceIn(0f, 1f)
            for (i in 1..steps) {
                if (!isActive) return@launch
                c.setVolume(start * (1f - i.toFloat() / steps))
                delay(125)
            }
            c.setVolume(0f)
            runCatching { c.pause() }
            c.setVolume(1f)
        }
    }

    // ---- v1.5 queue operations ----

    /** Insert [song] right after the current track without interrupting playback. */
    fun playNextInQueue(song: Song) {
        val q = _queue.value
        if (q.isEmpty()) {
            playQueue(listOf(song))
            return
        }
        val c = controller
        val idx = (c?.currentMediaItemIndex ?: _currentIndex.value).coerceIn(0, q.lastIndex)
        if (c == null || c.mediaItemCount == 0) {
            // Session not ready: mutate the persisted queue; it is fed on connect.
            _queue.value = q.toMutableList().also { it.add(idx + 1, song) }
            resolveUrlFor(song)
            return
        }
        val at = (idx + 1).coerceAtMost(q.size)
        val updated = q.toMutableList().also { it.add(at, song) }
        _queue.value = updated
        c.addMediaItem(at, song.toMediaItem(song.resolvedOrPlaceholder()))
        persistQueue(updated, idx, c.currentPosition.coerceAtLeast(0L))
        resolveUrlFor(song)
    }

    /** Append [song] to the tail of the current queue without interrupting playback. */
    fun addToQueue(song: Song) {
        val q = _queue.value
        if (q.isEmpty()) {
            playQueue(listOf(song))
            return
        }
        val c = controller
        if (c == null || c.mediaItemCount == 0) {
            _queue.value = q + song
            resolveUrlFor(song)
            return
        }
        val updated = q + song
        _queue.value = updated
        c.addMediaItem(updated.lastIndex, song.toMediaItem(song.resolvedOrPlaceholder()))
        persistQueue(updated, _currentIndex.value.coerceIn(0, q.lastIndex), c.currentPosition.coerceAtLeast(0L))
        resolveUrlFor(song)
    }

    fun playNext() {
        failureOf.clear()
        autoAdvancing = false
        controller?.seekToNextMediaItem()
    }

    fun playPrevious() {
        failureOf.clear()
        autoAdvancing = false
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _positionMs.value = positionMs // immediate feedback while player catches up
    }

    fun playAt(index: Int) {
        val c = controller ?: return
        if (index in _queue.value.indices) {
            failureOf.clear()
            autoAdvancing = false
            resetPositionJournal()
            c.seekToDefaultPosition(index)
            c.prepare()
            c.playWhenReady = true
            persistQueue(_queue.value, index, 0L)
        }
    }

    /**
     * Retry the current track: drop the cached URL (if any) so it re-resolves,
     * restart from its start and resume playing. Safe on any failure type.
     */
    fun retryCurrentSong() {
        clearPlaybackError()
        val c = controller ?: return
        val idx = c.currentMediaItemIndex
        if (idx !in _queue.value.indices) return
        val song = _queue.value[idx]
        resolvedUrls.remove(song.id)
        failureOf.remove(song.id)
        autoAdvancing = false
        resetPositionJournal()
        recordRecent(song) // explicit user intent: retry counts as listening
        c.seekToDefaultPosition(idx)
        c.prepare()
        c.playWhenReady = true
        resolveUrlFor(song)
    }

    /** Remove a track from the queue; if it was playing, playback follows to the next item. */
    fun removeFromQueue(index: Int) {
        val q = _queue.value
        if (index !in q.indices) return
        val updated = q.toMutableList().also { it.removeAt(index) }
        if (updated.isEmpty()) {
            clearQueue()
            return
        }
        controller?.removeMediaItem(index)
        _queue.value = updated
        _currentIndex.value = PlaybackEngine.indexAfterRemove(_currentIndex.value, index, updated.size)
        _currentSong.value = updated.getOrNull(_currentIndex.value)
        persistQueue(updated, _currentIndex.value, _positionMs.value)
    }

    /** Move a queue item from [from] to [to] (drag-less reorder helper). */
    fun moveQueueItem(from: Int, to: Int) {
        val q = _queue.value
        if (from !in q.indices || to !in q.indices || from == to) return
        val updated = q.toMutableList().apply { add(to, removeAt(from)) }
        controller?.moveMediaItem(from, to)
        _queue.value = updated
        _currentIndex.value = PlaybackEngine.indexAfterMove(_currentIndex.value, from, to)
        _currentSong.value = updated.getOrNull(_currentIndex.value)
        persistQueue(updated, _currentIndex.value, _positionMs.value)
    }

    /** Move a queue item to the first position without restarting playback. */
    fun moveToQueueTop(index: Int) {
        if (index in 1 until _queue.value.size) moveQueueItem(index, 0)
    }

    /** Move a queue item to the last position without restarting playback. */
    fun moveToQueueBottom(index: Int) {
        val last = _queue.value.lastIndex
        if (index in 0 until last) moveQueueItem(index, last)
    }

    /** Stop everything and reset the whole playback state. */
    fun clearQueue() {
        controller?.clearMediaItems()
        pendingQueue = null
        downloadManager?.markPlaying(emptySet())
        _queue.value = emptyList()
        _currentIndex.value = -1
        _currentSong.value = null
        _isPlaying.value = false
        _positionMs.value = 0L
        _durationMs.value = 0L
        _playbackError.value = null
        failureOf.clear()
        autoAdvancing = false
        restoringAfterBoot = false
        resetPositionJournal()
        scope.launch { local?.clearQueue() }
    }

/** Re-hydrate the persisted queue (and current song) so the app reopens where it left off. */
    private fun restoreQueue() {
        scope.launch {
            val saved = local?.savedQueue?.first() ?: return@launch
            if (saved.songs.isEmpty() || pendingQueue != null) return@launch // user already queued something
            val idx = saved.index.coerceIn(0, saved.songs.lastIndex)
            restoredPositionMs = saved.positionMs.coerceAtLeast(0L)
            _queue.value = saved.songs
            _currentIndex.value = idx
            _currentSong.value = saved.songs[idx]
            // If connect() already succeeded, hand the restored queue to the session now.
            controller?.let { c -> if (c.mediaItemCount == 0) attachRestoredQueue(c) }
        }
    }

    private fun persistQueue(songs: List<Song>, index: Int, positionMs: Long) {
        lastPersistedPositionMs = positionMs
        scope.launch { local?.setQueue(songs, index, positionMs) }
    }

    private fun resetPositionJournal() {
        lastPersistedPositionMs = 0L
    }

    private fun updateResolvingCurrent() {
        val idx = controller?.currentMediaItemIndex ?: _currentIndex.value
        val id = _queue.value.getOrNull(idx)?.id
        _resolvingCurrent.value = id != null && id in resolvingIds
    }

    private fun resolveUrlFor(song: Song) {
        // Local files play directly from their content:// uri; no network resolution needed.
        if (song.isLocal) return
        // An on-disk cached copy exists → play from disk, never touch the network.
        if (offlineUriFor(song) != null) return
        // Reuse a cached url so re-visiting a track is instant and buffer-free.
        resolvedUrls[song.id]?.let { return }
        scope.launch {
            resolvingIds += song.id
            updateResolvingCurrent()
            val result = try {
                repository.songUrl(song.id)
            } finally {
                resolvingIds -= song.id
                updateResolvingCurrent()
            }
            val url = result.getOrNull()?.url
            if (url.isNullOrBlank()) {
                // Failed resolution is never cached — a manual retry must re-fetch.
                // Only surface the failure for the CURRENT track; prefetch misses stay silent.
                val c = controller
                val currentIdx = c?.currentMediaItemIndex ?: _currentIndex.value
                if (_queue.value.getOrNull(currentIdx)?.id == song.id) {
                    emitPlaybackError(
                        song,
                        message = result.exceptionOrNull()?.message
                            ?.takeIf { it.isNotBlank() }
                            ?: "无法获取播放地址（可能为会员或版权受限）",
                        category = FailureCategory.NO_URL
                    )
                }
                return@launch
            }
            val c = controller ?: return@launch
            resolvedUrls[song.id] = url
            // Piggyback the just-resolved URL: auto-cache it for offline playback.
            downloadManager?.maybeAutoCache(song, url)
            val idx = c.currentMediaItemIndex
            // Only hot-swap the current item's URI; do NOT re-prepare or touch
            // playWhenReady — that was re-buffering audio and causing stalls.
            if (idx in 0 until c.mediaItemCount && _queue.value.getOrNull(idx)?.id == song.id) {
                val updated = song.toMediaItem(url)
                c.replaceMediaItem(idx, updated)
            }
        }
    }

    /**
     * Pre-resolve the next track's URL so switching is buffer-free. Respects the
     * current play mode: sequence → next index, shuffle → random (but never a
     * track whose URL is already cached, so we fan out), repeat-one → none.
     */
    private fun prefetchNext() {
        val queue = _queue.value
        if (queue.size < 2) return
        val resolvedIndices = queue.indices
            .filter { resolvedUrls.containsKey(queue[it].id) }
            .toSet()
        val nextIndex = PlaybackEngine.nextPrefetchIndex(
            queueSize = queue.size,
            currentIndex = _currentIndex.value,
            mode = _playMode.value,
            resolvedIndices = resolvedIndices
        ) ?: return
        queue.getOrNull(nextIndex)?.let { resolveUrlFor(it) }
    }

    private fun Song.resolvedOrPlaceholder(): String =
        localUri ?: offlineUriFor(this) ?: resolvedUrls[id] ?: "placeholder://$id"

    /** A playable file:// uri when the song is downloaded and the file exists. */
    private fun offlineUriFor(song: Song): String? {
        if (song.isLocal) return null
        val file = downloadManager?.fileFor(song.id) ?: return null
        if (!file.exists() || file.length() <= 0L) return null
        return Uri.fromFile(file).toString()
    }

    private fun Song.toMediaItem(uri: String = resolvedOrPlaceholder()): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist(artistText)
                    .setAlbumTitle(album.name)
                    .setArtworkUri(coverUrl.takeIf { it.startsWith("http") }?.let(Uri::parse))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
)
            .build()

    fun release() {
        if (released) return
        released = true
        retryJob?.cancel()
        retryJob = null
        listener?.let { controller?.removeListener(it) }
        listener = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        pendingQueue = null
        scope.cancel()
    }
}
