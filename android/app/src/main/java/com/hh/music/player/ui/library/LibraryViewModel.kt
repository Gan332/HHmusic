package com.hh.music.player.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hh.music.player.data.Song
import com.hh.music.player.data.SavedPlaylist
import com.hh.music.player.data.local.LocalStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val store: LocalStore) : ViewModel() {
    val favorites: StateFlow<List<Song>> =
        store.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recent: StateFlow<List<Song>> =
        store.recent.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val savedPlaylists: StateFlow<List<SavedPlaylist>> =
        store.savedPlaylists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
