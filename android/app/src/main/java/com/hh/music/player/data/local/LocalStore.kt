package com.hh.music.player.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hh.music.player.data.SavedPlaylist
import com.hh.music.player.data.SavedQueue
import com.hh.music.player.data.Song
import com.hh.music.player.data.ToplistItem
import com.hh.music.player.data.offline.DownloadEntry
import com.hh.music.player.data.offline.OfflineCache
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

    // ---- Imported local audio files (SAF content:// uris) ----
    private val importedUrisKey = stringPreferencesKey("imported_uris")
    val importedUris: Flow<List<String>> = context.dataStore.data.map { p ->
        p[importedUrisKey]?.let { runCatching { json.decodeFromString(ListSerializer(serializer<String>()), it) }.getOrNull() } ?: emptyList()
    }

    // ---- Saved playlists (as ToplistItem-like entries) ----
    private val savedPlaylistsKey = stringPreferencesKey("saved_playlists")
    val savedPlaylists: Flow<List<SavedPlaylist>> = context.dataStore.data.map { p ->
        p[savedPlaylistsKey]?.let { runCatching { json.decodeFromString(ListSerializer(SavedPlaylist.serializer()), it) }.getOrNull() } ?: emptyList()
    }

    // ---- Play mode (string persisted) ----
    private val playModeKey = stringPreferencesKey("play_mode")
    // ---- Last playback queue (songs + current index) ----
    private val queueKey = stringPreferencesKey("queue")
    val savedQueue: Flow<SavedQueue?> = context.dataStore.data.map { p ->
        p[queueKey]?.let { runCatching { json.decodeFromString(SavedQueue.serializer(), it) }.getOrNull() }
    }
    // ---- Settings: direct vs backend, default audio quality ----
    private val useBackendKey = booleanPreferencesKey("use_backend")
    val useBackend: Flow<Boolean> = context.dataStore.data.map { it[useBackendKey] ?: false }
    private val audioQualityKey = stringPreferencesKey("audio_quality")
    val audioQuality: Flow<String> = context.dataStore.data.map { it[audioQualityKey] ?: "exhigh" }
    private val progressStyleKey = stringPreferencesKey("progress_style")
    val progressStyle: Flow<String> = context.dataStore.data.map { it[progressStyleKey] ?: "slider" }
    val playMode: Flow<String> = context.dataStore.data.map { it[playModeKey] ?: "sequence" }
    // ---- v1.5: playback speed, equalizer, offline cache ----
    private val speedKey = floatPreferencesKey("speed")
    val speed: Flow<Float> = context.dataStore.data.map { (it[speedKey] ?: 1f).coerceIn(0.5f, 2f) }
    private val equalizerEnabledKey = booleanPreferencesKey("equalizer_enabled")
    val equalizerEnabled: Flow<Boolean> = context.dataStore.data.map { it[equalizerEnabledKey] ?: false }
    private val equalizerPresetKey = stringPreferencesKey("equalizer_preset")
    val equalizerPreset: Flow<String> = context.dataStore.data.map { it[equalizerPresetKey] ?: "default" }
    private val equalizerBandsKey = stringPreferencesKey("equalizer_bands")
    val equalizerBands: Flow<String> = context.dataStore.data.map { it[equalizerBandsKey] ?: "" }
    private val autoCacheKey = booleanPreferencesKey("auto_cache")
    val autoCache: Flow<Boolean> = context.dataStore.data.map { it[autoCacheKey] ?: true }
    private val cacheCapMbKey = intPreferencesKey("cache_cap_mb")
    val cacheCapMb: Flow<Int> = context.dataStore.data.map { it[cacheCapMbKey] ?: OfflineCache.DEFAULT_CAP_MB.toInt() }
    private val downloadsKey = stringPreferencesKey("downloads")
    val downloads: Flow<List<DownloadEntry>> = context.dataStore.data.map { p ->
        p[downloadsKey]?.let {
            runCatching { json.decodeFromString(ListSerializer(DownloadEntry.serializer()), it) }.getOrNull()
        } ?: emptyList()
    }

    suspend fun toggleFavorite(song: Song) {
        context.dataStore.edit { p ->
            val cur = p[favoritesKey]?.let { decode(it) } ?: emptyList()
            val next = if (cur.any { it.id == song.id }) cur.filter { it.id != song.id } else listOf(song) + cur
            p[favoritesKey] = encode(next)
        }
    }

    fun isFavoriteSync(snapshot: List<Song>, id: Long): Boolean = snapshot.any { it.id == id }

    suspend fun removeFavorite(songId: Long) {
        context.dataStore.edit { p ->
            val cur = p[favoritesKey]?.let { decode(it) } ?: emptyList()
            p[favoritesKey] = encode(cur.filter { it.id != songId })
        }
    }

    suspend fun addRecent(song: Song) {
        context.dataStore.edit { p ->
            val cur = p[recentKey]?.let { decode(it) } ?: emptyList()
            val next = (listOf(song) + cur.filter { it.id != song.id }).take(50)
            p[recentKey] = encode(next)
        }
    }

    suspend fun removeRecent(songId: Long) {
        context.dataStore.edit { p ->
            val cur = p[recentKey]?.let { decode(it) } ?: emptyList()
            p[recentKey] = encode(cur.filterNot { it.id == songId })
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

    /** Remove a single entry from the search history. */
    suspend fun removeSearchHistory(keyword: String) {
        context.dataStore.edit { p ->
            val cur = p[historyKey]?.let { runCatching { json.decodeFromString(ListSerializer(serializer<String>()), it) }.getOrNull() } ?: emptyList()
            p[historyKey] = json.encodeToString(ListSerializer(serializer<String>()), cur.filterNot { it == keyword })
        }
    }

    suspend fun addImportedUris(uris: List<String>) {
        if (uris.isEmpty()) return
        context.dataStore.edit { p ->
            val cur = p[importedUrisKey]?.let { runCatching { json.decodeFromString(ListSerializer(serializer<String>()), it) }.getOrNull() } ?: emptyList()
            val next = (uris + cur).distinct()
            p[importedUrisKey] = json.encodeToString(ListSerializer(serializer<String>()), next)
        }
    }

    suspend fun removeImportedUri(uri: String) {
        context.dataStore.edit { p ->
            val cur = p[importedUrisKey]?.let { runCatching { json.decodeFromString(ListSerializer(serializer<String>()), it) }.getOrNull() } ?: emptyList()
            p[importedUrisKey] = json.encodeToString(ListSerializer(serializer<String>()), cur.filter { it != uri })
        }
    }

    suspend fun toggleSavedPlaylist(playlist: SavedPlaylist) {
        context.dataStore.edit { p ->
            val cur = p[savedPlaylistsKey]?.let { runCatching { json.decodeFromString(ListSerializer(SavedPlaylist.serializer()), it) }.getOrNull() } ?: emptyList()
            val next = if (cur.any { it.id == playlist.id }) cur.filter { it.id != playlist.id } else listOf(playlist) + cur
            p[savedPlaylistsKey] = json.encodeToString(ListSerializer(SavedPlaylist.serializer()), next)
        }
    }

    suspend fun setUseBackend(value: Boolean) { context.dataStore.edit { it[useBackendKey] = value } }
    suspend fun setAudioQuality(value: String) { context.dataStore.edit { it[audioQualityKey] = value } }
    suspend fun setProgressStyle(value: String) { context.dataStore.edit { it[progressStyleKey] = value } }
    suspend fun setSpeed(value: Float) { context.dataStore.edit { it[speedKey] = value } }
    suspend fun setEqualizerEnabled(value: Boolean) { context.dataStore.edit { it[equalizerEnabledKey] = value } }
    suspend fun setEqualizerPreset(value: String) { context.dataStore.edit { it[equalizerPresetKey] = value } }
    suspend fun setEqualizerBands(value: String) { context.dataStore.edit { it[equalizerBandsKey] = value } }
    suspend fun setAutoCache(value: Boolean) { context.dataStore.edit { it[autoCacheKey] = value } }
    suspend fun setCacheCapMb(value: Int) { context.dataStore.edit { it[cacheCapMbKey] = value } }
    suspend fun setDownloads(entries: List<DownloadEntry>) {
        context.dataStore.edit {
            it[downloadsKey] = json.encodeToString(ListSerializer(DownloadEntry.serializer()), entries)
        }
    }
    suspend fun setPlayMode(mode: String) {
        context.dataStore.edit { it[playModeKey] = mode }
    }

    suspend fun setQueue(songs: List<Song>, index: Int, positionMs: Long = 0L) {
        val saved = QueueCodec.encode(songs, index, positionMs)
        if (saved == null) {
            context.dataStore.edit { it.remove(queueKey) }
            return
        }
        context.dataStore.edit { it[queueKey] = saved }
    }

    suspend fun clearQueue() {
        context.dataStore.edit { it.remove(queueKey) }
    }

    private fun decode(s: String): List<Song> =
        runCatching { json.decodeFromString(ListSerializer(Song.serializer()), s) }.getOrDefault(emptyList())

    private fun encode(list: List<Song>): String =
        json.encodeToString(ListSerializer(Song.serializer()), list)
}
