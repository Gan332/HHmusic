package com.hh.music.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hh.music.player.data.local.LocalStore
import com.hh.music.player.ui.theme.AppThemeColor
import com.hh.music.player.ui.theme.AppThemeMode
import com.hh.music.player.ui.theme.LyricFontScale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val useBackend: Boolean = false,
    val audioQuality: String = "exhigh",
    val aboutVersion: String = "1.6"
)

class SettingsViewModel(private val store: LocalStore) : ViewModel() {

    val useBackend: StateFlow<Boolean> =
        store.useBackend.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val audioQuality: StateFlow<String> =
        store.audioQuality.stateIn(viewModelScope, SharingStarted.Eagerly, "exhigh")

    val progressStyle: StateFlow<String> =
        store.progressStyle.stateIn(viewModelScope, SharingStarted.Eagerly, "slider")

    val themeMode: StateFlow<AppThemeMode> =
        store.themeMode.map { AppThemeMode.from(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, AppThemeMode.SYSTEM)

    val themeColor: StateFlow<AppThemeColor> =
        store.themeColor.map { AppThemeColor.from(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, AppThemeColor.GREEN)

    val dynamicColor: StateFlow<Boolean> =
        store.dynamicColor.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val autoCache: StateFlow<Boolean> =
        store.autoCache.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val cacheCapMb: StateFlow<Int> =
        store.cacheCapMb.stateIn(viewModelScope, SharingStarted.Eagerly, 1024)

    val showLyricTranslation: StateFlow<Boolean> =
        store.showLyricTranslation.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val showLyricRomanization: StateFlow<Boolean> =
        store.showLyricRomanization.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val lyricFontScale: StateFlow<LyricFontScale> =
        store.lyricFontScale.map { LyricFontScale.from(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, LyricFontScale.MEDIUM)

    fun setUseBackend(value: Boolean) { viewModelScope.launch { store.setUseBackend(value) } }
    fun setAudioQuality(value: String) { viewModelScope.launch { store.setAudioQuality(value) } }
    fun setProgressStyle(value: String) { viewModelScope.launch { store.setProgressStyle(value) } }
    fun setThemeMode(value: String) { viewModelScope.launch { store.setThemeMode(value) } }
    fun setThemeColor(value: String) { viewModelScope.launch { store.setThemeColor(value) } }
    fun setDynamicColor(value: Boolean) { viewModelScope.launch { store.setDynamicColor(value) } }
    fun setAutoCache(value: Boolean) { viewModelScope.launch { store.setAutoCache(value) } }
    fun setCacheCapMb(value: Int) { viewModelScope.launch { store.setCacheCapMb(value) } }
    fun setShowLyricTranslation(value: Boolean) { viewModelScope.launch { store.setShowLyricTranslation(value) } }
    fun setShowLyricRomanization(value: Boolean) { viewModelScope.launch { store.setShowLyricRomanization(value) } }
    fun setLyricFontScale(value: String) { viewModelScope.launch { store.setLyricFontScale(value) } }
}
