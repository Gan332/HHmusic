package com.hh.music.player.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hh.music.player.data.SavedPlaylist
import com.hh.music.player.data.Song
import com.hh.music.player.data.ToplistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/** DataStore for user-local collections: favorites, recently played, search history. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hhmusic_store")

class LocalStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ---- Favorites (songs) ----
    private val favoritesKey = stringPreferencesKey("favorites")
    val favorites: Flow<List<Song>> = context.dataStore.data.map { p ->
        p[favoritesKey]?.let { runCatching { json.decodeFromString(ListSerializer(Song.serializer()), it) }.getOrNull() } ?: emptyList()
    }

    // ---- Recently played (songs) ----
    private val recentKey = stringPreferencesKey("recent")
    val recent: Flow<List<Song>> = context.dataStore.data.map { p ->
        p[recentKey]?.let { runCatching { json.decodeFromString(ListSerializer(Song.serializer()), it) }.getOrNull() } ?: emptyList()
    }

    // ---- Search history (strings) ----
    private val historyKey = stringPreferencesKey("search_history")
    val searchHistory: Flow<List<String>> = context.dataStore.data.map { p ->
        p[historyKey]?.let { runCatching { json.decodeFromString(ListSerializer(serializer<String>()), it) }.getOrNull() } ?: emptyList()
    }

    // ---- Saved playlists (as ToplistItem-like entries) ----
    private val savedPlaylistsKey = stringPreferencesKey("saved_playlists")
    val savedPlaylists: Flow<List<SavedPlaylist>> = context.dataStore.data.map { p ->
        p[savedPlaylistsKey]?.let { runCatching { json.decodeFromString(ListSerializer(SavedPlaylist.serializer()), it) }.getOrNull() } ?: emptyList()
    }

    // ---- Play mode (string persisted) ----
    private val playModeKey = stringPreferencesKey("play_mode")
    // ---- Settings: direct vs backend, default audio quality ----
    private val useBackendKey = booleanPreferencesKey("use_backend")
    private val isDarkThemeKey = booleanPreferencesKey("is_dark_theme")
    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { it[isDarkThemeKey] ?: false }
    val useBackend: Flow<Boolean> = context.dataStore.data.map { it[useBackendKey] ?: false }
    private val audioQualityKey = stringPreferencesKey("audio_quality")
    val audioQuality: Flow<String> = context.dataStore.data.map { it[audioQualityKey] ?: "exhigh" }
    val playMode: Flow<String> = context.dataStore.data.map { it[playModeKey] ?: "sequence" }

    // ---- Settings: dynamic color, theme seed color, wave progress bar ----
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[dynamicColorKey] ?: true }
    private val themeColorKey = stringPreferencesKey("theme_color")
    val themeColor: Flow<String> = context.dataStore.data.map { it[themeColorKey] ?: "#1DB954" }
    private val waveProgressKey = booleanPreferencesKey("wave_progress")
    val waveProgress: Flow<Boolean> = context.dataStore.data.map { it[waveProgressKey] ?: false }

    // ---- Backend URL (customizable) ----
    private val backendUrlKey = stringPreferencesKey("backend_url")
    val backendUrl: Flow<String> = context.dataStore.data.map { it[backendUrlKey] ?: "http://10.0.2.2:3000/api/" }

    suspend fun toggleFavorite(song: Song) {
        context.dataStore.edit { p ->
            val cur = p[favoritesKey]?.let { decode(it) } ?: emptyList()
            val next = if (cur.any { it.id == song.id }) cur.filter { it.id != song.id } else listOf(song) + cur
            p[favoritesKey] = encode(next)
        }
    }

    fun isFavoriteSync(snapshot: List<Song>, id: Long): Boolean = snapshot.any { it.id == id }

    suspend fun addRecent(song: Song) {
        context.dataStore.edit { p ->
            val cur = p[recentKey]?.let { decode(it) } ?: emptyList()
            val next = (listOf(song) + cur.filter { it.id != song.id }).take(50)
            p[recentKey] = encode(next)
        }
    }

    suspend fun clearRecent() {
        context.dataStore.edit { it[recentKey] = encode(emptyList()) }
    }

    suspend fun addSearchHistory(keyword: String) {
        val k = keyword.trim()
        if (k.isBlank()) return
        context.dataStore.edit { p ->
            val cur = p[historyKey]?.let { runCatching { json.decodeFromString(ListSerializer(serializer<String>()), it) }.getOrNull() } ?: emptyList()
            val next = (listOf(k) + cur.filter { it != k }).take(20)
            p[historyKey] = json.encodeToString(ListSerializer(serializer<String>()), next)
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { it[historyKey] = json.encodeToString(ListSerializer(serializer<String>()), emptyList()) }
    }

    suspend fun toggleSavedPlaylist(playlist: SavedPlaylist) {
        context.dataStore.edit { p ->
            val cur = p[savedPlaylistsKey]?.let { runCatching { json.decodeFromString(ListSerializer(SavedPlaylist.serializer()), it) }.getOrNull() } ?: emptyList()
            val next = if (cur.any { it.id == playlist.id }) cur.filter { it.id != playlist.id } else listOf(playlist) + cur
            p[savedPlaylistsKey] = json.encodeToString(ListSerializer(SavedPlaylist.serializer()), next)
        }
    }

    suspend fun setUseBackend(value: Boolean) { context.dataStore.edit { it[useBackendKey] = value } }
    suspend fun setIsDarkTheme(value: Boolean) { context.dataStore.edit { it[isDarkThemeKey] = value } }
    suspend fun setAudioQuality(value: String) { context.dataStore.edit { it[audioQualityKey] = value } }
    suspend fun setBackendUrl(value: String) { context.dataStore.edit { it[backendUrlKey] = value } }
    suspend fun setDynamicColor(value: Boolean) { context.dataStore.edit { it[dynamicColorKey] = value } }
    suspend fun setThemeColor(value: String) { context.dataStore.edit { it[themeColorKey] = value } }
    suspend fun setWaveProgress(value: Boolean) { context.dataStore.edit { it[waveProgressKey] = value } }
    suspend fun setPlayMode(mode: String) {
        context.dataStore.edit { it[playModeKey] = mode }
    }

    private fun decode(s: String): List<Song> =
        runCatching { json.decodeFromString(ListSerializer(Song.serializer()), s) }.getOrDefault(emptyList())

    private fun encode(list: List<Song>): String =
        json.encodeToString(ListSerializer(Song.serializer()), list)
}
