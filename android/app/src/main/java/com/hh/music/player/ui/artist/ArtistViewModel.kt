package com.hh.music.player.ui.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hh.music.player.data.ArtistSongsPage
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArtistUiState(
    val songs: List<Song> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val order: String = "hot",
    val artistName: String = ""
) {
    val hasMore: Boolean get() = songs.isNotEmpty() && songs.size < total
}

class ArtistViewModel internal constructor(
    private val pageLoader: suspend (Long, Int, Int, String) -> Result<ArtistSongsPage>,
    private val artistId: Long,
    private val artistName: String = ""
) : ViewModel() {
    constructor(repository: MusicRepository, artistId: Long, artistName: String = "") : this(
        { id, limit, offset, order -> repository.artistSongsPage(id, limit, offset, order) },
        artistId,
        artistName
    )

    private val _state = MutableStateFlow(ArtistUiState(artistName = artistName))
    val state: StateFlow<ArtistUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun setOrder(order: String) {
        if (order == _state.value.order) return
        _state.value = ArtistUiState(order = order, artistName = artistName)
        load()
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasMore) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            pageLoader(
                artistId,
                PAGE_SIZE,
                s.songs.size,
                s.order
            )
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            total = page.total,
                            songs = (it.songs + page.songs).distinctBy(Song::id)
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loadingMore = false, error = e.message ?: "加载更多失败") }
                }
        }
    }

    fun retry() = load()

    private fun load() {
        if (artistId <= 0) {
            _state.value = ArtistUiState(loading = false, error = "歌手不存在", artistName = artistName)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            pageLoader(
                artistId,
                PAGE_SIZE,
                0,
                _state.value.order
            )
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            loading = false,
                            songs = page.songs,
                            total = page.total
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, error = e.message ?: "歌手歌曲加载失败")
                    }
                }
        }
    }

    companion object {
        private const val PAGE_SIZE = 50
    }
}
