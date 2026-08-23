package com.hh.music.player.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hh.music.player.data.Artist
import com.hh.music.player.data.ArtistSearchPage
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Song
import com.hh.music.player.data.local.LocalStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val loadMoreError: String? = null,
    val artists: List<Artist> = emptyList(),
    val hotSearches: List<String> = SearchViewModel.DEFAULT_HOT_SEARCHES
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

    init {
        loadHotSearches()
    }

    /** Replace the built-in fallback list with real NetEase hot keywords when available. */
    private fun loadHotSearches() {
        viewModelScope.launch {
            repository.hotSearches()
                .onSuccess { words ->
                    if (words.isNotEmpty()) _state.update { it.copy(hotSearches = words) }
                }
        }
    }

    companion object {
        private const val PAGE_SIZE = 30

        /** Offline/static fallback shown until (or if) real hot keywords arrive. */
        val DEFAULT_HOT_SEARCHES = listOf("周杰伦", "林俊杰", "陈奕迅", "许嵩", "毛不易", "邓紫棋")
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        if (q.isBlank()) {
            _state.update {
                it.copy(
                    results = emptyList(),
                    artists = emptyList(),
                    error = null,
                    loading = false,
                    total = 0
                )
            }
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
            it.copy(
                loading = true,
                error = null,
                loadingMore = false,
                loadMoreError = null,
                total = 0
            )
        }
        coroutineScope {
            val songs = async { repository.search(q, limit = PAGE_SIZE, offset = 0) }
            val artists = async { repository.searchArtists(q, limit = 12).getOrDefault(ArtistSearchPage()) }
            val songResult = songs.await()
            val artistPage = artists.await()
            if (seq != searchSeq) return@coroutineScope
            songResult.onSuccess { page ->
                _state.update {
                    it.copy(
                        loading = false,
                        results = page.songs,
                        total = page.total,
                        artists = artistPage.artists
                    )
                }
                local?.addSearchHistory(q)
            }
            .onFailure { e ->
                if (seq != searchSeq) return@onFailure
                _state.update {
                    it.copy(
                        loading = false,
                        artists = artistPage.artists,
                        error = if (artistPage.artists.isEmpty()) e.message ?: "搜索失败" else null
                    )
                }
            }
        }
    }

    /** Fetch the next page (offset = current result count) and append it. */
    fun retry() {
        val q = _state.value.query.trim()
        if (q.isNotEmpty()) submitSearch(q)
    }

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
