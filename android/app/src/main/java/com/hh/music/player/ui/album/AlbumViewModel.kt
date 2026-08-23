package com.hh.music.player.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hh.music.player.data.AlbumDetail
import com.hh.music.player.data.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AlbumUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val album: AlbumDetail? = null
)

class AlbumViewModel(private val repository: MusicRepository) : ViewModel() {
    private val _state = MutableStateFlow(AlbumUiState())
    val state: StateFlow<AlbumUiState> = _state.asStateFlow()

    fun load(id: Long) {
        _state.value = AlbumUiState(loading = true)
        viewModelScope.launch {
            repository.albumDetail(id)
                .onSuccess { _state.value = AlbumUiState(loading = false, album = it) }
                .onFailure { e -> _state.value = AlbumUiState(loading = false, error = e.message ?: "加载失败") }
        }
    }
}
