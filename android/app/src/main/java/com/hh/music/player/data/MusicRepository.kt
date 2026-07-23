package com.hh.music.player.data

import android.content.Context
import com.hh.music.player.network.HHMusicApi
import com.hh.music.player.network.NetworkModule
import com.hh.music.player.network.RecommendPlaylistItem
import com.hh.music.player.network.ToplistResponse
import com.hh.music.player.playback.PlayerController
import com.hh.music.player.data.local.LocalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single source of truth for music data. Wraps the backend API and
 * adds error handling.
 */
class MusicRepository(
    private val api: HHMusicApi = NetworkModule.api,
    val local: LocalStore? = null
) {

    suspend fun search(keyword: String, limit: Int = 30, offset: Int = 0): Result<List<Song>> =
        runCatching {
            withContext(Dispatchers.IO) {
                api.search(keyword, limit, offset).songs
            }
        }

    suspend fun songDetail(ids: List<Long>): Result<List<Song>> = runCatching {
        withContext(Dispatchers.IO) {
            api.songDetail(ids.joinToString(",")).songs
        }
    }

    suspend fun songUrl(id: Long): Result<SongUrl> = runCatching {
        withContext(Dispatchers.IO) { api.songUrl(id) }
    }

    suspend fun lyric(id: Long): Result<Lyric> = runCatching {
        withContext(Dispatchers.IO) { api.lyric(id) }
    }

    suspend fun playlistDetail(id: Long): Result<Playlist> = runCatching {
        withContext(Dispatchers.IO) { api.playlistDetail(id) }
    }

    suspend fun toplists(): Result<List<ToplistItem>> = runCatching {
        withContext(Dispatchers.IO) { api.toplist().list }
    }

    // ---- New: recommendations & artist & new songs ----
    suspend fun recommendSongs(limit: Int = 30): Result<List<Song>> = runCatching {
        withContext(Dispatchers.IO) { api.recommendSongs(limit).songs }
    }

    suspend fun recommendPlaylists(limit: Int = 12): Result<List<RecommendPlaylistItem>> = runCatching {
        withContext(Dispatchers.IO) { api.recommendPlaylists(limit).list }
    }

    suspend fun artistSongs(id: Long, limit: Int = 50, offset: Int = 0, order: String = "hot"): Result<List<Song>> = runCatching {
        withContext(Dispatchers.IO) { api.artistSongs(id, limit, offset, order).songs }
    }

    suspend fun newSongs(limit: Int = 30): Result<List<Song>> = runCatching {
        withContext(Dispatchers.IO) { api.newSongs(limit).songs }
    }
}

/** Manual dependency injection container, created in HHMusicApp. */
class AppContainer(context: Context) {
    val localStore: LocalStore = LocalStore(context.applicationContext)
    val repository: MusicRepository = MusicRepository(local = localStore)
    val playerController: PlayerController = PlayerController(context.applicationContext, repository, localStore)
}
