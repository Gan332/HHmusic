package com.hh.music.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Logical playback modes shown in the UI. */
enum class PlayMode(val key: String) {
    SEQUENCE("sequence"),  // play through, then stop
    REPEAT_ONE("repeat_one"),
    SHUFFLE("shuffle");

    companion object {
        fun from(key: String?): PlayMode = entries.firstOrNull { it.key == key } ?: SEQUENCE
    }
}

/**
 * Client-side bridge to the PlaybackService MediaSession.
 *
 * Holds the logical play queue as [Song]s, keeps ExoPlayer in sync, lazily
 * resolves playable URLs, applies the persisted [PlayMode], and records
 * recently-played songs into [LocalStore].
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

    private var listener: Player.Listener? = null

    init {
        connect()
        startPositionPolling()
        observePersistedPlayMode()
    }

    private fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture?.addListener({
            controller?.let(::wireListener)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun wireListener(c: MediaController) {
        if (listener != null) return
        listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = c.currentMediaItemIndex
                _currentIndex.value = idx
                _currentSong.value = _queue.value.getOrNull(idx)
                _queue.value.getOrNull(idx)?.let { song ->
                    resolveUrlFor(song)
                    recordRecent(song)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                // When a track finishes, advance behavior is handled by ExoPlayer's repeat mode.
            }
        }.also { c.addListener(it) }
        applyPlayModeToPlayer()
    }

    private fun startPositionPolling() {
        scope.launch {
            while (true) {
                delay(500)
                val c = controller ?: continue
                _positionMs.value = c.currentPosition.coerceAtLeast(0)
                _durationMs.value = if (c.duration > 0) c.duration else 0
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

    /** Cycle to the next play mode and persist it. */
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
            PlayMode.SEQUENCE -> {
                c.repeatMode = Player.REPEAT_MODE_OFF
                c.shuffleModeEnabled = false
            }
            PlayMode.REPEAT_ONE -> {
                c.repeatMode = Player.REPEAT_MODE_ONE
                c.shuffleModeEnabled = false
            }
            PlayMode.SHUFFLE -> {
                c.repeatMode = Player.REPEAT_MODE_ALL
                c.shuffleModeEnabled = true
            }
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
        val c = controller
        if (c == null) {
            scope.launch { delay(300); playQueue(songs, startIndex) }
            return
        }
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
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
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    fun playAt(index: Int) {
        val c = controller ?: return
        if (index in _queue.value.indices) {
            c.seekToDefaultPosition(index)
            c.prepare()
            c.playWhenReady = true
        }
    }

    private fun resolveUrlFor(song: Song) {
        scope.launch {
            val result = repository.songUrl(song.id)
            val url = result.getOrNull()?.url
            if (url.isNullOrBlank()) return@launch
            val c = controller ?: return@launch
            val idx = c.currentMediaItemIndex
            if (idx == -1) return@launch
            val updated = song.toMediaItem(url)
            c.replaceMediaItem(idx, updated)
            applyPlayModeToPlayer()
            c.prepare()
            c.playWhenReady = true
        }
    }

    private fun Song.toMediaItem(uri: String = "placeholder://$id"): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist(artistText)
                    .setAlbumTitle(album.name)
                    .setArtworkUri(
                        coverUrl.takeIf { it.startsWith("http") }?.let(Uri::parse)
                    )
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()

    fun release() {
        listener?.let { controller?.removeListener(it) }
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
