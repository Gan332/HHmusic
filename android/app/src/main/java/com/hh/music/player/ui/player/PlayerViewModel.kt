package com.hh.music.player.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hh.music.player.data.Lyric
import com.hh.music.player.data.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LyricState(
    val loading: Boolean = true,
    val lyric: Lyric = Lyric(),
    val lines: List<LyricLine> = emptyList(),
    val translations: Map<Long, String> = emptyMap()
)

class PlayerViewModel(private val repository: MusicRepository) : ViewModel() {
    private val _state = MutableStateFlow(LyricState())
    val state: StateFlow<LyricState> = _state.asStateFlow()

    fun loadLyric(songId: Long) {
        _state.value = LyricState(loading = true)
        viewModelScope.launch {
            repository.lyric(songId)
                .onSuccess { lrc ->
                    _state.value = LyricState(
                        loading = false,
                        lyric = lrc,
                        lines = LyricParser.parse(lrc.lrc),
                        translations = LyricParser.parse(lrc.tlyric).associate { it.timeMs to it.text }
                    )
                }
                .onFailure {
                    _state.value = LyricState(loading = false, lines = emptyList())
                }
        }
    }
}
