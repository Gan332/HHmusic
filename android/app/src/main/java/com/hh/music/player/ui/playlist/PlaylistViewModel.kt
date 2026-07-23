package com.hh.music.player.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaylistState(
    val loading: Boolean = true,
    val error: String? = null,
    val playlist: Playlist? = null
)

class PlaylistViewModel(private val repository: MusicRepository) : ViewModel() {
    private val _state = MutableStateFlow(PlaylistState())
    val state: StateFlow<PlaylistState> = _state.asStateFlow()

    fun load(id: Long) {
        _state.value = PlaylistState(loading = true)
        viewModelScope.launch {
            repository.playlistDetail(id)
                .onSuccess { _state.value = PlaylistState(loading = false, playlist = it) }
                .onFailure { e -> _state.value = PlaylistState(loading = false, error = e.message ?: "加载失败") }
        }
    }
}
