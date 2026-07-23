package com.hh.music.player.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Song
import com.hh.music.player.data.local.LocalStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val results: List<Song> = emptyList()
)

class SearchViewModel(
    private val repository: MusicRepository,
    private val local: LocalStore? = null
) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        if (q.isBlank()) {
            _state.update { it.copy(results = emptyList(), error = null, loading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // debounce
            doSearch(q)
        }
    }

    /// Triggered when a user picks a term from history.
    fun submitSearch(keyword: String) {
        if (keyword.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { doSearch(keyword) }
    }

    private suspend fun doSearch(q: String) {
        _state.update { it.copy(loading = true, error = null) }
        repository.search(q)
            .onSuccess { songs ->
                _state.update { it.copy(loading = false, results = songs) }
                local?.addSearchHistory(q)
            }
            .onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "搜索失败") }
            }
    }

    fun clearHistory() {
        viewModelScope.launch { local?.clearSearchHistory() }
    }
}
