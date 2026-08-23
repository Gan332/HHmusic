package com.hh.music.player.ui.plaza

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.PlazaCategory
import com.hh.music.player.data.PlazaPlaylist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Playlist plaza state: category chips + paged playlist grid.
 * Categories default to a small static set when the catlist call fails,
 * so the screen is still usable offline / on upstream hiccups.
 */
data class PlazaState(
    val categories: List<PlazaCategory> = DEFAULT_CATEGORIES,
    val selectedCat: String = "全部",
    val order: String = "hot", // hot | new
    val playlists: List<PlazaPlaylist> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: Boolean = false,
    val more: Boolean = false
) {
    companion object {
        val DEFAULT_CATEGORIES = listOf(
            PlazaCategory(-1, "全部"),
            PlazaCategory(-1, "华语"),
            PlazaCategory(-1, "流行"),
            PlazaCategory(-1, "摇滚"),
            PlazaCategory(-1, "民谣"),
            PlazaCategory(-1, "电子"),
            PlazaCategory(-1, "说唱"),
            PlazaCategory(-1, "古风"),
            PlazaCategory(-1, "轻音乐"),
            PlazaCategory(-1, "爵士")
        )
    }
}

class PlazaViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _state = MutableStateFlow(PlazaState())
    val state: StateFlow<PlazaState> = _state.asStateFlow()

    private var pageOffset = 0
    private var loadingJobActive = false

    init {
        refresh()
        loadCategories()
    }

    /** Reload from the first page for the current category/order. */
    fun refresh() {
        if (loadingJobActive) return
        loadingJobActive = true
        pageOffset = 0
        _state.value = _state.value.copy(
            loading = true,
            error = false,
            playlists = emptyList(),
            more = false
        )
        viewModelScope.launch {
            val result = repository.topPlaylists(
                cat = _state.value.selectedCat,
                limit = PAGE_SIZE,
                offset = 0,
                order = _state.value.order
            )
            loadingJobActive = false
            result.fold(
                onSuccess = { page ->
                    pageOffset = page.list.size
                    _state.value = _state.value.copy(
                        playlists = page.list,
                        loading = false,
                        more = page.more && page.list.isNotEmpty()
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(loading = false, error = true)
                }
            )
        }
    }

    /** Append the next page (grid scroll-to-end). */
    fun loadMore() {
        val s = _state.value
        if (loadingJobActive || !s.more || s.loadingMore) return
        loadingJobActive = true
        _state.value = s.copy(loadingMore = true)
        viewModelScope.launch {
            val result = repository.topPlaylists(
                cat = s.selectedCat,
                limit = PAGE_SIZE,
                offset = pageOffset,
                order = s.order
            )
            loadingJobActive = false
            result.fold(
                onSuccess = { page ->
                    pageOffset += page.list.size
                    // Defensive dedup: upstream occasionally repeats boundary rows.
                    val seen = _state.value.playlists.map { it.id }.toHashSet()
                    val fresh = page.list.filter { it.id !in seen }
                    _state.value = _state.value.copy(
                        playlists = _state.value.playlists + fresh,
                        loadingMore = false,
                        more = page.more && fresh.isNotEmpty()
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(loadingMore = false)
                }
            )
        }
    }

    fun selectCategory(name: String) {
        if (_state.value.selectedCat == name) return
        _state.value = _state.value.copy(selectedCat = name)
        refresh()
    }

    fun toggleOrder() {
        _state.value = _state.value.copy(order = if (_state.value.order == "hot") "new" else "hot")
        refresh()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            repository.playlistCategories().onSuccess { cats ->
                if (cats.isNotEmpty()) {
                    // Keep "全部" first; merge upstream names with our defaults.
                    val merged = listOf(PlazaCategory(-1, "全部")) +
                        cats.filter { it.name != "全部" }
                    _state.value = _state.value.copy(categories = merged)
                }
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 30
    }
}
