package com.hh.music.player.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.ToplistItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ToplistState(
    val loading: Boolean = true,
    val error: String? = null,
    val toplists: List<ToplistItem> = emptyList()
)

class ToplistViewModel(private val repository: MusicRepository) : ViewModel() {
    private val _state = MutableStateFlow(ToplistState())
    val state: StateFlow<ToplistState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.toplists()
                .onSuccess { _state.value = ToplistState(loading = false, toplists = it) }
                .onFailure { e -> _state.value = ToplistState(loading = false, error = e.message ?: "加载失败") }
        }
    }
}
