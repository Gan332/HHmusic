package com.hh.music.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Song
import com.hh.music.player.data.local.LocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Logical playback modes shown in the UI. */
enum class PlayMode(val key: String) {
    SEQUENCE("sequence"), REPEAT_ONE("repeat_one"), SHUFFLE("shuffle");
    companion object {
        fun from(key: String?): PlayMode = entries.firstOrNull { it.key == key } ?: SEQUENCE
    }
}

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
    private val local: LocalStore? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController?
        get() = controllerFuture?.takeIf { it.isDone }?.let {
            runCatching { it.get() }.getOrNull()
        }

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

    /**
     * One-shot user-facing playback error, consumed by the UI to show a Snackbar.
     * Set on [Player.Listener.onPlayerError]; cleared after a short delay.
     */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Songs whose URL we failed to resolve — skipped on transition. */
    private val unresolvable: MutableSet<Long> = HashSet()

    private var listener: Player.Listener? = null
    private var wiredController: MediaController? = null

    /** id -> already-resolved playable url, so we don't re-fetch on every track revisit.
     * LRU-evicting cache — keeps the most recent [resolvedUrlsMaxSize] entries. */
    private val resolvedUrls = object : LinkedHashMap<Long, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, String>): Boolean =
            size > resolvedUrlsMaxSize
    }
    private val resolvedUrlsMaxSize = 200

    /** Generation counter to invalidate stale playQueue retries. */
    private var playQueueGen = 0

    private var seekLockUntil = 0L

    init {
        connect()
        startPositionPolling()
        observePersistedPlayMode()
        autoClearError()
    }

    /** Clear [error] after a short delay so the same message doesn't re-show. */
    private fun autoClearError() {
        scope.launch {
            _error.collectLatest { msg ->
                if (msg == null) return@collectLatest
                delay(3000)
                if (_error.value == msg) _error.value = null
            }
        }
    }

    private fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture?.addListener({
            controller?.let(::wireListener)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun wireListener(c: MediaController) {
        if (wiredController === c) return
        listener?.let { wiredController?.removeListener(it) }
        wiredController = c
        listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = c.currentMediaItemIndex
                _currentIndex.value = idx
                val song = _queue.value.getOrNull(idx)
                _currentSong.value = song
                song?.let {
                    // Skip dead tracks the user already saw fail, so we don't loop
                    // on a poisoned item.
                    if (unresolvable.contains(it.id) || mediaItemIsPlaceholder(mediaItem)) {
                        skipUnresolvable(c, it)
                        return
                    }
                    resolveUrlFor(it)
                    recordRecent(it)
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }
            override fun onPlaybackStateChanged(state: Int) {
                // Reflect true duration only when known.
                if (c.duration > 0) _durationMs.value = c.duration
            }
            override fun onPlayerError(error: PlaybackException) {
                val idx = c.currentMediaItemIndex
                val song = _queue.value.getOrNull(idx)
                if (song != null) unresolvable.add(song.id)
                _error.value = song?.name?.let { "「$it」无法播放" } ?: "播放出错"
                // Auto-skip to keep the queue moving; UI surfaces the message.
                if (c.mediaItemCount > 1 && c.hasNextMediaItem()) {
                    c.seekToNextMediaItem()
                }
            }
        }.also { c.addListener(it) }
        applyPlayModeToPlayer()
    }

    /** Throttled, diff-based progress refresh — the #1 cause of UI jank before v1.3. */
    private fun startPositionPolling() {
        scope.launch {
            while (true) {
                delay(500)
                val c = controller ?: continue
                if (!c.isPlaying) continue
                if (System.currentTimeMillis() < seekLockUntil) continue
                val pos = c.currentPosition.coerceAtLeast(0)
                // Coarse-grain to whole seconds for slider/labels; sub-second jitter was
                // recomposing the whole player screen hundreds of times a minute.
                val coarse = (pos / 1000L) * 1000L
                if (coarse != _positionMs.value) _positionMs.value = coarse
                if (c.duration > 0 && c.duration != _durationMs.value) _durationMs.value = c.duration
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
    fun playQueue(songs: List<Song>, startIndex: Int = 0, attempt: Int = 0) {
        if (songs.isEmpty()) return
        if (startIndex !in songs.indices) return
        val gen = ++playQueueGen
        val c = controller
        if (c == null) {
            if (attempt < 10) {
                scope.launch {
                    delay(300)
                    if (playQueueGen == gen) playQueue(songs, startIndex, attempt + 1)
                }
            }
            return
        }
        _queue.value = songs
        _currentIndex.value = startIndex
        _currentSong.value = songs[startIndex]
        recordRecent(songs[startIndex])
        c.setMediaItems(songs.map { it.toMediaItem(it.resolvedOrPlaceholder()) }, startIndex, 0L)
        applyPlayModeToPlayer()
        c.prepare()
        c.playWhenReady = true
        resolveUrlFor(songs[startIndex])
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun playNext() { controller?.seekToNextMediaItem() }
    fun playPrevious() { controller?.seekToPreviousMediaItem() }

    /** Remove a song from the queue by index. */
    fun removeFromQueue(index: Int) {
        val current = _queue.value
        if (index !in current.indices) return
        val c = controller ?: run {
            val q = current.toMutableList()
            q.removeAt(index)
            _queue.value = q
            return
        }
        val wasCurrent = index == _currentIndex.value
        val removingBeforeCurrent = index < _currentIndex.value
        c.removeMediaItem(index)
        val q = _queue.value.toMutableList()
        q.removeAt(index)
        _queue.value = q
        // Media3 auto-shifts currentMediaItemIndex left when items before the
        // current one are removed; mirror that locally and clear stale state
        // when the current item itself was removed (let the next transition
        // listener rehydrate).
        when {
            wasCurrent -> {
                _currentIndex.value = -1
                _currentSong.value = null
            }
            removingBeforeCurrent -> _currentIndex.value = (_currentIndex.value - 1).coerceAtLeast(0)
        }
    }

    /** Insert [song] immediately after the currently playing track. */
    fun playNext(song: Song) {
        val current = _queue.value
        val c = controller
        if (current.isEmpty() || c == null) {
            playQueue(listOf(song), 0)
            return
        }
        val ci = _currentIndex.value
        val insertIndex = if (ci in current.indices) ci + 1 else current.size
        val q = current.toMutableList()
        q.add(insertIndex, song)
        _queue.value = q
        c.addMediaItem(insertIndex, song.toMediaItem("placeholder://${song.id}"))
        resolveUrlFor(song)
    }

    /** Append [song] to the end of the queue. */
    fun addToQueue(song: Song) {
        val current = _queue.value
        val c = controller
        if (current.isEmpty() || c == null) {
            playQueue(listOf(song), 0)
            return
        }
        val q = current.toMutableList()
        q.add(song)
        _queue.value = q
        c.addMediaItem(song.toMediaItem("placeholder://${song.id}"))
        resolveUrlFor(song)
    }

    /** Append multiple songs to the end of the queue. */
    fun addToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        val current = _queue.value
        val c = controller
        if (current.isEmpty() || c == null) {
            playQueue(songs, 0)
            return
        }
        val q = current.toMutableList()
        q.addAll(songs)
        _queue.value = q
        c.addMediaItems(
            /* index = */ q.size - songs.size,
            /* mediaItems = */ songs.map { it.toMediaItem("placeholder://${it.id}") }
        )
        songs.forEach { resolveUrlFor(it) }
    }
    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _positionMs.value = positionMs
        seekLockUntil = System.currentTimeMillis() + 800
    }

    fun playAt(index: Int) {
        val c = controller ?: return
        if (index in _queue.value.indices) {
            c.seekToDefaultPosition(index)
            if (c.playbackState != Player.STATE_READY && c.playbackState != Player.STATE_BUFFERING) {
                c.prepare()
            }
            c.playWhenReady = true
        }
    }

    private fun resolveUrlFor(song: Song) {
        // Even on a cache hit, the MediaItem at non-current positions may still
        // be holding a placeholder:// URI that we need to back-fill. So the
        // early-return only applies once the controller is also free of
        // placeholders for this id.
        val cached = resolvedUrls[song.id]
        if (cached != null) {
            backfillPlaceholder(cached, song)
            return
        }
        scope.launch {
            val result = repository.songUrl(song.id)
            val url = result.getOrNull()?.url
            if (url.isNullOrBlank()) {
                // Mark unresolvable so the transition listener will skip it
                // instead of looping on the same error.
                unresolvable.add(song.id)
                _error.value = song.name.let { "「$it」无法获取播放地址" }
                return@launch
            }
            val c = controller ?: return@launch
            resolvedUrls[song.id] = url
            backfillPlaceholder(url, song)
        }
    }

    /**
     * Replace any MediaItem in the queue that still has the placeholder URI for
     * [song] with one pointing at the real [url]. For the currently playing
     * item, only swap the URI — don't re-prepare or touch playWhenReady, which
     * would cause an audio restart.
     *
     * Crucially we read the existing item's metadata first and preserve any
     * `artworkData` the PlaybackService may have already attached, so URL
     * hot-swap doesn't blow away the cover (and vice versa).
     */
    private fun backfillPlaceholder(url: String, song: Song) {
        val c = controller ?: return
        for (idx in 0 until c.mediaItemCount) {
            val existing = c.getMediaItemAt(idx)
            val isPlaceholder = existing.localConfiguration?.uri?.toString()?.startsWith("placeholder://") == true
            if (!isPlaceholder) continue
            if (_queue.value.getOrNull(idx)?.id != song.id) continue
            // Always preserve artworkData the Service may have attached, so
            // this hot-swap doesn't blow away the cover.
            c.replaceMediaItem(idx, song.toMediaItem(url, preserveArtworkFrom = existing))
        }
    }

    private fun mediaItemIsPlaceholder(item: MediaItem?): Boolean =
        item?.localConfiguration?.uri?.toString()?.startsWith("placeholder://") == true

    private fun skipUnresolvable(c: MediaController, song: Song) {
        // Remove the dead entry and try the next one; the transition listener
        // will re-evaluate the new current item.
        val idx = c.currentMediaItemIndex
        if (idx in 0 until c.mediaItemCount) {
            c.removeMediaItem(idx)
        }
        val q = _queue.value.toMutableList()
        if (idx in q.indices) q.removeAt(idx)
        _queue.value = q
        if (c.mediaItemCount > 0) {
            c.prepare()
            c.playWhenReady = true
        } else {
            _currentSong.value = null
            _currentIndex.value = -1
        }
    }

    private fun Song.resolvedOrPlaceholder(): String = resolvedUrls[id] ?: "placeholder://$id"

    private fun Song.toMediaItem(uri: String = "placeholder://$id", preserveArtworkFrom: MediaItem? = null): MediaItem {
        val metaBuilder = MediaMetadata.Builder()
            .setTitle(name)
            .setDisplayTitle(name)
            .setArtist(artistText)
            .setSubtitle(artistText)
            .setAlbumTitle(album.name)
            .setArtworkUri(coverUrl.takeIf { it.startsWith("http") }?.let(Uri::parse))
            .setIsBrowsable(false)
            .setIsPlayable(true)
        // Carry over any artworkData the Service has already attached so
        // replaceMediaItem for the URI doesn't clobber the cover.
        preserveArtworkFrom?.mediaMetadata?.artworkData?.let {
            metaBuilder.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(metaBuilder.build())
            .build()
    }

    fun release() {
        listener?.let { wiredController?.removeListener(it) }
        listener = null
        wiredController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        resolvedUrls.clear()
        scope.cancel()
    }
}
