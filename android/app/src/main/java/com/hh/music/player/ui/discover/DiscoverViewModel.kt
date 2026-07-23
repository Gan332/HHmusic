package com.hh.music.player.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Song
import com.hh.music.player.network.RecommendPlaylistItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiscoverState(
    val loading: Boolean = true,
    val recommend: List<Song> = emptyList(),
    val newSongs: List<Song> = emptyList(),
    val playlists: List<RecommendPlaylistItem> = emptyList(),
    val error: String? = null
)

class DiscoverViewModel(private val repository: MusicRepository) : ViewModel() {
    private val _state = MutableStateFlow(DiscoverState())
    val state: StateFlow<DiscoverState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val recDef = async { repository.recommendSongs(20) }
            val newDef = async { repository.newSongs(20) }
            val plDef = async { repository.recommendPlaylists(10) }
            val rec = recDef.await()
            val new = newDef.await()
            val pl = plDef.await()
            _state.value = DiscoverState(
                loading = false,
                recommend = rec.getOrElse { emptyList() },
                newSongs = new.getOrElse { emptyList() },
                playlists = pl.getOrElse { emptyList() },
                error = if (rec.isFailure && new.isFailure && pl.isFailure) "加载失败，请检查后端" else null
            )
        }
    }
}
