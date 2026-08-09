package com.hh.music.player.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Song
import com.hh.music.player.network.RecommendPlaylistItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One recommendation module: independent loading/error, keeps last-good data. */
data class SectionState<T>(
    val data: List<T> = emptyList(),
    val loading: Boolean = false,
    val error: Boolean = false
) {
    val isEmpty: Boolean get() = data.isEmpty()
}

data class DiscoverState(
    val recommend: SectionState<Song> = SectionState(loading = true),
    val newSongs: SectionState<Song> = SectionState(loading = true),
    val playlists: SectionState<RecommendPlaylistItem> = SectionState(loading = true),
    /** True while a manual refresh is in flight (pull-to-refresh spinner). */
    val refreshing: Boolean = false
) {
    /** Nothing on screen at all (all sections empty) — show the full-screen loader/error. */
    val allEmpty: Boolean get() = recommend.isEmpty && newSongs.isEmpty && playlists.isEmpty
    val allFailed: Boolean get() = recommend.error && newSongs.error && playlists.error
}

class DiscoverViewModel(private val repository: MusicRepository) : ViewModel() {
    private val _state = MutableStateFlow(DiscoverState())
    val state: StateFlow<DiscoverState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    /** Monotonic refresh id; a newer refresh cancels publication of an older one. */
    private var refreshSeq = 0

    init { refresh(force = false) }

    /**
     * Reload all three sections. `force=false` (first open / tab return) serves the
     * in-memory cache when it's still fresh, so switching Tabs never re-hits NetEase;
     * `force=true` (pull-to-refresh / retry) always re-fetches. A refresh started while
     * another is running cancels the older one — stale results never overwrite newer data.
     */
    fun refresh(force: Boolean = false) {
        refreshJob?.cancel()
        val seq = ++refreshSeq
        if (!force && cacheIsFresh()) {
            _state.value = cachedState ?: DiscoverState()
            return
        }
        _state.value = _state.value.copy(refreshing = true)
        refreshJob = viewModelScope.launch {
            val prev = _state.value
            // Only show per-section spinners when the section has nothing to show yet.
            if (prev.recommend.isEmpty) _state.value = _state.value.copy(recommend = prev.recommend.copy(loading = true))
            if (prev.newSongs.isEmpty) _state.value = _state.value.copy(newSongs = prev.newSongs.copy(loading = true))
            if (prev.playlists.isEmpty) _state.value = _state.value.copy(playlists = prev.playlists.copy(loading = true))

            val recDef = async { repository.recommendSongs(20) }
            val newDef = async { repository.newSongs(20) }
            val plDef = async { repository.recommendPlaylists(10) }
            val rec = recDef.await()
            val new = newDef.await()
            val pl = plDef.await()

            if (seq != refreshSeq) return@launch // superseded by a newer refresh

            val next = DiscoverState(
                recommend = SectionState(
                    data = rec.getOrElse { prev.recommend.data },
                    loading = false,
                    error = rec.isFailure
                ),
                newSongs = SectionState(
                    data = new.getOrElse { prev.newSongs.data },
                    loading = false,
                    error = new.isFailure
                ),
                playlists = SectionState(
                    data = pl.getOrElse { prev.playlists.data },
                    loading = false,
                    error = pl.isFailure
                ),
                refreshing = false
            )
            _state.value = next
            cachedState = next
            cacheAtMs = System.currentTimeMillis()
        }
    }

    private fun cacheIsFresh(): Boolean =
        cachedState != null && System.currentTimeMillis() - cacheAtMs < CACHE_TTL_MS

    companion object {
        private const val CACHE_TTL_MS = 5 * 60_000L
        /** Process-wide cache: survives tab switches; cleared on process death. */
        @Volatile private var cachedState: DiscoverState? = null
        @Volatile private var cacheAtMs = 0L
    }
}