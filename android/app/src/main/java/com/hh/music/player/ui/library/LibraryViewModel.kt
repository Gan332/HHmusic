package com.hh.music.player.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hh.music.player.data.CloudSync
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Song
import com.hh.music.player.data.SavedPlaylist
import com.hh.music.player.data.UserPlaylist
import com.hh.music.player.data.local.LocalStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** v1.8: lifecycle of the "我的网易云歌单" section on the playlists tab. */
sealed interface CloudPlaylistsState {
    /** Not logged in / never requested. */
    data object Idle : CloudPlaylistsState

    data object Loading : CloudPlaylistsState

    data class Done(val playlists: List<UserPlaylist>) : CloudPlaylistsState

    data class Error(val message: String) : CloudPlaylistsState
}

class LibraryViewModel(
    private val store: LocalStore,
    private val repository: MusicRepository? = null,
    private val cloudSync: CloudSync? = null
) : ViewModel() {
    val favorites: StateFlow<List<Song>> =
        store.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recent: StateFlow<List<Song>> =
        store.recent.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val savedPlaylists: StateFlow<List<SavedPlaylist>> =
        store.savedPlaylists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- v1.8: my NetEase cloud playlists ----

    val isLoggedIn: StateFlow<Boolean> =
        store.loginCookie.map { it.isNotBlank() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val userId: StateFlow<Long> =
        store.userId.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val _cloudPlaylists = MutableStateFlow<CloudPlaylistsState>(CloudPlaylistsState.Idle)
    val cloudPlaylists: StateFlow<CloudPlaylistsState> = _cloudPlaylists.asStateFlow()

    /** Load the signed-in user's cloud playlists (no-op when logged out). */
    fun refreshCloudPlaylists() {
        val repo = repository ?: return
        val uid = userId.value
        if (!isLoggedIn.value || uid <= 0) {
            _cloudPlaylists.value = CloudPlaylistsState.Idle
            return
        }
        viewModelScope.launch {
            _cloudPlaylists.value = CloudPlaylistsState.Loading
            repo.userPlaylists(uid)
                .onSuccess { rows ->
                    _cloudPlaylists.value =
                        if (rows.isEmpty()) CloudPlaylistsState.Error("暂无云端歌单")
                        else CloudPlaylistsState.Done(rows)
                }
                .onFailure { e ->
                    _cloudPlaylists.value =
                        CloudPlaylistsState.Error(e.message ?: "加载云端歌单失败，请重试")
                }
        }
    }

    fun removeFavorite(songId: Long) {
        viewModelScope.launch { store.removeFavorite(songId) }
    }

    fun removeRecent(songId: Long) {
        viewModelScope.launch { store.removeRecent(songId) }
    }

    fun clearRecent() {
        viewModelScope.launch { store.clearRecent() }
    }

    fun removeSavedPlaylist(id: Long) {
        viewModelScope.launch { store.toggleSavedPlaylist(SavedPlaylist(id = id, name = "")) }
    }
}
