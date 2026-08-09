package com.hh.music.player.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hh.music.player.data.Lyric
import com.hh.music.player.data.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LyricState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val lyric: Lyric = Lyric(),
    val lines: List<LyricLine> = emptyList(),
    val translations: Map<Long, String> = emptyMap(),
    val romanizations: Map<Long, String> = emptyMap()
)

class PlayerViewModel(private val repository: MusicRepository) : ViewModel() {
    private val _state = MutableStateFlow(LyricState())
    val state: StateFlow<LyricState> = _state.asStateFlow()

    private var lyricJob: Job? = null

    /** Monotonic request id; only the request for the latest song may update state. */
    private var lyricSeq = 0

    fun loadLyric(songId: Long) {
        lyricJob?.cancel()
        val seq = ++lyricSeq
        _state.value = LyricState(loading = true)
        lyricJob = viewModelScope.launch {
            repository.lyric(songId)
                .onSuccess { lrc ->
                    if (seq != lyricSeq) return@onSuccess
                    _state.value = LyricState(
                        loading = false,
                        lyric = lrc,
                        lines = LyricParser.parse(lrc.lrc),
                        translations = LyricParser.translations(lrc.tlyric),
                        romanizations = LyricParser.romanizations(lrc.romalrc)
                    )
                }
                .onFailure {
                    if (seq != lyricSeq) return@onFailure
                    _state.value = LyricState(loading = false, error = true)
                }
        }
    }
}
