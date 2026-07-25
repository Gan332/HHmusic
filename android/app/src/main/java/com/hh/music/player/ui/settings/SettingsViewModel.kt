package com.hh.music.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hh.music.player.data.local.LocalStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val useBackend: Boolean = false,
    val audioQuality: String = "exhigh",
    val aboutVersion: String = "1.3"
)

class SettingsViewModel(private val store: LocalStore) : ViewModel() {

    val useBackend: StateFlow<Boolean> =
        store.useBackend.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val audioQuality: StateFlow<String> =
        store.audioQuality.stateIn(viewModelScope, SharingStarted.Eagerly, "exhigh")

    fun setUseBackend(value: Boolean) { viewModelScope.launch { store.setUseBackend(value) } }
    fun setAudioQuality(value: String) { viewModelScope.launch { store.setAudioQuality(value) } }
}
