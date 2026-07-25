package com.hh.music.player.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hh.music.player.data.Song
import com.hh.music.player.data.SavedPlaylist
import com.hh.music.player.data.local.LocalStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LibraryViewModel(store: LocalStore) : ViewModel() {

    val favorites: StateFlow<List<Song>> =
        store.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recent: StateFlow<List<Song>> =
        store.recent.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val savedPlaylists: StateFlow<List<SavedPlaylist>> =
        store.savedPlaylists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
