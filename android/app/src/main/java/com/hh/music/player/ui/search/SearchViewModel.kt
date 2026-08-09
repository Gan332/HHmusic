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
    val results: List<Song> = emptyList(),
    val total: Int = 0,
    val loadingMore: Boolean = false,
    val loadMoreError: String? = null
) {
    val hasMore: Boolean get() = results.isNotEmpty() && results.size < total
}

class SearchViewModel(
    private val repository: MusicRepository,
    private val local: LocalStore? = null
) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchJob: Job? = null

    /** Monotonic request id; only the newest search may update the state. */
    private var searchSeq = 0

    companion object {
        private const val PAGE_SIZE = 30
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        if (q.isBlank()) {
            _state.update { it.copy(results = emptyList(), error = null, loading = false, total = 0) }
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
        val seq = ++searchSeq
        _state.update {
            it.copy(loading = true, error = null, loadingMore = false, loadMoreError = null, total = 0)
        }
        repository.search(q, limit = PAGE_SIZE, offset = 0)
            .onSuccess { page ->
                if (seq != searchSeq) return@onSuccess
                _state.update {
                    it.copy(loading = false, results = page.songs, total = page.total)
                }
                local?.addSearchHistory(q)
            }
            .onFailure { e ->
                if (seq != searchSeq) return@onFailure
                _state.update { it.copy(loading = false, error = e.message ?: "搜索失败") }
            }
    }

    /** Fetch the next page (offset = current result count) and append it. */
    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasMore || s.query.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true, loadMoreError = null) }
            repository.search(s.query, limit = PAGE_SIZE, offset = s.results.size)
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            results = (it.results + page.songs).distinctBy(Song::id),
                            total = page.total
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loadingMore = false, loadMoreError = e.message ?: "加载更多失败") }
                }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { local?.clearSearchHistory() }
    }
}
