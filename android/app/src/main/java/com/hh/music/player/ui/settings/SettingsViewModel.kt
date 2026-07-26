package com.hh.music.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hh.music.player.data.local.LocalStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: LocalStore) : ViewModel() {

    val useBackend: StateFlow<Boolean> =
        store.useBackend.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val audioQuality: StateFlow<String> =
        store.audioQuality.stateIn(viewModelScope, SharingStarted.Eagerly, "exhigh")

    val isDarkTheme: StateFlow<Boolean> =
        store.isDarkTheme.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val backendUrl: StateFlow<String> =
        store.backendUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "http://10.0.2.2:3000/api/")

    fun setUseBackend(value: Boolean) { viewModelScope.launch { store.setUseBackend(value) } }
    fun setAudioQuality(value: String) { viewModelScope.launch { store.setAudioQuality(value) } }
    fun setIsDarkTheme(value: Boolean) { viewModelScope.launch { store.setIsDarkTheme(value) } }
    fun setBackendUrl(value: String) { viewModelScope.launch { store.setBackendUrl(value) } }
}
