package com.hh.music.player.ui.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hh.music.player.data.AlbumItem
import com.hh.music.player.data.ArtistAlbumsPage
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
    val artistName: String = "",
    val selectedTab: String = TAB_SONGS,
    val albums: List<AlbumItem> = emptyList(),
    val albumsLoading: Boolean = false,
    val albumsError: String? = null,
    val albumsMore: Boolean = false
) {
    val hasMore: Boolean get() = songs.isNotEmpty() && songs.size < total

    companion object {
        const val TAB_SONGS = "songs"
        const val TAB_ALBUMS = "albums"
    }
}

class ArtistViewModel internal constructor(
    private val pageLoader: suspend (Long, Int, Int, String) -> Result<ArtistSongsPage>,
    private val artistId: Long,
    private val artistName: String = "",
    private val albumLoader: suspend (Long, Int, Int) -> Result<ArtistAlbumsPage> =
        { _, _, _ -> Result.success(ArtistAlbumsPage()) }
) : ViewModel() {
    constructor(repository: MusicRepository, artistId: Long, artistName: String = "") : this(
        { id, limit, offset, order -> repository.artistSongsPage(id, limit, offset, order) },
        artistId,
        artistName,
        { id, limit, offset -> repository.artistAlbumsPage(id, limit, offset) }
    )

    private val _state = MutableStateFlow(ArtistUiState(artistName = artistName))
    val state: StateFlow<ArtistUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun setOrder(order: String) {
        if (order == _state.value.order) return
        // Reset only the song-list part; albums/tab selection survive the reload.
        _state.update { it.copy(songs = emptyList(), total = 0, loading = true, error = null, order = order) }
        load()
    }

    fun setTab(tab: String) {
        if (tab == _state.value.selectedTab) return
        _state.update { it.copy(selectedTab = tab) }
        if (tab == ArtistUiState.TAB_ALBUMS && _state.value.albums.isEmpty()) loadAlbums()
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

    fun loadMoreAlbums() {
        val s = _state.value
        if (s.albumsLoading || !s.albumsMore) return
        viewModelScope.launch {
            fetchAlbums(s.albums.size)
        }
    }

    fun retry() = load()

    fun retryAlbums() {
        if (_state.value.albumsLoading) return
        viewModelScope.launch { fetchAlbums(_state.value.albums.size) }
    }

    private fun loadAlbums() {
        if (artistId <= 0) {
            _state.update { it.copy(albumsError = "歌手不存在") }
            return
        }
        viewModelScope.launch { fetchAlbums(0) }
    }

    private suspend fun fetchAlbums(offset: Int) {
        _state.update { it.copy(albumsLoading = true, albumsError = null) }
        albumLoader(artistId, ALBUM_PAGE_SIZE, offset)
            .onSuccess { page ->
                _state.update {
                    it.copy(
                        albumsLoading = false,
                        albums = (it.albums + page.albums).distinctBy(AlbumItem::id),
                        albumsMore = page.more
                    )
                }
            }
            .onFailure { e ->
                _state.update { it.copy(albumsLoading = false, albumsError = e.message ?: "专辑加载失败") }
            }
    }

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
        private const val ALBUM_PAGE_SIZE = 50
    }
}
