package com.hh.music.player.data

import android.content.Context
import com.hh.music.player.data.local.LocalStore
import com.hh.music.player.data.offline.DownloadManager
import com.hh.music.player.network.DirectNcmClient
import com.hh.music.player.network.NcmParser
import com.hh.music.player.network.HHMusicApi
import com.hh.music.player.network.NetworkModule
import com.hh.music.player.network.RecommendPlaylistItem
import com.hh.music.player.network.ToplistResponse
import kotlinx.coroutines.launch
import com.hh.music.player.playback.PlayerController
import com.hh.music.player.playback.EqualizerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single source of truth for music data.
 *
 * By default the app talks to NetEase Cloud Music directly (via [DirectNcmClient]
 * + eapi encryption, ported from GuitaristRin/Ncrust) — no backend required.
 * The legacy [HHMusicApi] (talking to our Node server) is kept only as an
 * opt-in fallback when [USE_BACKEND] is set true.
 */
class MusicRepository(
    private val api: HHMusicApi = NetworkModule.api,
    private val local: LocalStore? = null
) {

    /** Runtime flags, kept in sync with [LocalStore] by [AppContainer]. */
    @Volatile var useBackend: Boolean = false
    @Volatile var audioQuality: String = "exhigh"

    companion object {
        private const val TAG = "HHMusicRepo"
    }

    // Small in-memory LRU caches: lyrics are re-opened every time you re-visit the
    // player screen, and search results are re-fetched on every keystroke debounce.
    private val lyricCache = LruCache<Long, Lyric>(30)
    private val searchCache = LruCache<SearchCacheKey, SearchPage>(30)

    // ---------------- direct (NetEase) implementations ----------------

    suspend fun search(keyword: String, limit: Int = 30, offset: Int = 0): Result<SearchPage> =
        runCatching {
            val key = SearchCacheKey(keyword, limit, offset)
            searchCache[key]?.let { return@runCatching it }
            val page = withContext(Dispatchers.IO) {
                if (useBackend) {
                    val resp = api.search(keyword, limit, offset)
                    SearchPage(songs = resp.songs, total = resp.songCount)
                } else {
                    val fields = mapOf(
                        "s" to keyword,
                        "type" to "1",
                        "limit" to limit.toString(),
                        "offset" to offset.toString()
                    )
                    val body = DirectNcmClient.apiPost("cloudsearch/pc", fields)
                    NcmParser.searchPage(JSONObject(body))
                }
            }
            searchCache[key] = page
            page
        }

    suspend fun searchArtists(keyword: String, limit: Int = 20, offset: Int = 0): Result<ArtistSearchPage> =
        runCatching {
            withContext(Dispatchers.IO) {
                if (useBackend) {
                    val resp = api.searchArtists(keyword, limit, offset)
                    ArtistSearchPage(artists = resp.artists, total = resp.total)
                } else {
                    val fields = mapOf(
                        "s" to keyword,
                        "type" to "100",
                        "limit" to limit.toString(),
                        "offset" to offset.toString()
                    )
                    val body = DirectNcmClient.apiPost("cloudsearch/pc", fields)
                    NcmParser.artistSearchPage(JSONObject(body))
                }
            }
        }

    suspend fun songDetail(ids: List<Long>): Result<List<Song>> = runCatching {
        withContext(Dispatchers.IO) {
            if (useBackend) {
                api.songDetail(ids.joinToString(",")).songs
            } else {
                val c = JSONArray().also { arr -> ids.forEach { arr.put(JSONObject().put("id", it)) } }.toString()
                val body = DirectNcmClient.apiPost("v3/song/detail", mapOf("c" to c))
                NcmParser.songList(JSONObject(body))
            }
        }
    }

    /**
     * Resolve a playable URL. Uses eapi for the official V1 endpoint and, if no
     * link is returned (common without a login cookie), falls back to the
     * public "outer url" — exactly Ncrust's strategy.
     */
    suspend fun songUrl(id: Long): Result<SongUrl> = runCatching {
        withContext(Dispatchers.IO) {
            if (useBackend) {
                api.songUrl(id)
            } else {
                val payload = mapOf<String, Any>(
                    "ids" to JSONArray().put(id).toString(),
                    "level" to audioQuality,
                    "encodeType" to "flac"
                )
                val body = DirectNcmClient.eapiPost("song/enhance/player/url/v1", payload)
                val dataArr = JSONObject(body).optJSONArray("data")
                val first = dataArr?.optJSONObject(0)
                val url = first?.optString("url", "") ?: ""
                SongUrl(
                    id = id,
                    url = if (url.isNullOrEmpty()) DirectNcmClient.outerUrl(id) else url,
                    br = first?.optLong("br", 0) ?: 0,
                    size = first?.optLong("size", 0) ?: 0,
                    type = first?.optString("type", null)?.takeIf { it.isNotEmpty() },
                    md5 = first?.optString("md5", null)?.takeIf { it.isNotEmpty() }
                )
            }
        }
    }

    suspend fun lyric(id: Long): Result<Lyric> = runCatching {
        lyricCache[id]?.let { return@runCatching it }
        val lrc = withContext(Dispatchers.IO) {
            if (useBackend) {
                api.lyric(id)
            } else {
                val fields = mapOf(
                    "id" to id.toString(),
                    "cp" to "false", "tv" to "0", "lv" to "0", "rv" to "0", "kv" to "0",
                    "yv" to "0", "ytv" to "0", "yrv" to "0"
                )
                val body = DirectNcmClient.apiPost("song/lyric", fields)
                val j = JSONObject(body)
                Lyric(
                    lrc = j.optJSONObject("lrc")?.optString("lyric", "") ?: "",
                    tlyric = j.optJSONObject("tlyric")?.optString("lyric", "") ?: "",
                    romalrc = j.optJSONObject("romalrc")?.optString("lyric", "") ?: "",
                    yrc = j.optJSONObject("yrc")?.optString("lyric", "") ?: ""
                )
            }
        }
        lyricCache[id] = lrc
        lrc
    }

    suspend fun playlistDetail(id: Long): Result<Playlist> = runCatching {
        withContext(Dispatchers.IO) {
            if (useBackend) {
                api.playlistDetail(id)
            } else {
                val body = DirectNcmClient.apiPost("v6/playlist/detail", mapOf("id" to id.toString(), "n" to "1000"))
                val pl = JSONObject(body).optJSONObject("playlist") ?: JSONObject()
                Playlist(
                    id = pl.optLong("id", id),
                    name = pl.optString("name", ""),
                    coverImgUrl = pl.optString("coverImgUrl", null),
                    creator = pl.optJSONObject("creator")?.let { Creator(it.optLong("id", 0), it.optString("nickname", "")) },
                    tracks = NcmParser.songList(pl, "tracks")
                )
            }
        }
    }

    suspend fun toplists(): Result<List<ToplistItem>> = runCatching {
        withContext(Dispatchers.IO) {
            if (useBackend) {
                api.toplist().list
            } else {
                val body = DirectNcmClient.apiPost("toplist/detail", emptyMap())
                NcmParser.toplistItems(JSONObject(body))
            }
        }
    }

    suspend fun recommendSongs(limit: Int = 30): Result<List<Song>> = runCatching {
        withContext(Dispatchers.IO) {
            if (useBackend) {
                api.recommendSongs(limit).songs
            } else {
                val body = DirectNcmClient.apiPost("v3/discovery/recommend/songs", mapOf("limit" to limit.toString()))
                val root = JSONObject(body)
                val daily = root.optJSONObject("data")?.optJSONArray("dailySongs") ?: root.optJSONArray("recommend")
                val out = ArrayList<Song>(daily?.length() ?: 0)
                if (daily != null) for (i in 0 until daily.length()) {
                    daily.optJSONObject(i)?.let { out += NcmParser.toSong(it) }
                }
                out
            }
        }
    }

    suspend fun recommendPlaylists(limit: Int = 12): Result<List<RecommendPlaylistItem>> = runCatching {
        withContext(Dispatchers.IO) {
            if (useBackend) {
                api.recommendPlaylists(limit).list
            } else {
                val body = DirectNcmClient.apiPost("personalized/playlist", mapOf("limit" to limit.toString()))
                val arr = JSONObject(body).optJSONArray("result") ?: JSONArray()
                val out = ArrayList<RecommendPlaylistItem>(arr.length())
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    val creator = p.optJSONObject("creator")
                    out += RecommendPlaylistItem(
                        id = p.optLong("id", 0),
                        name = p.optString("name", ""),
                        picUrl = p.optString("picUrl", null),
                        playcount = p.optLong("playcount", p.optLong("playCount", 0)),
                        creator = creator?.let { com.hh.music.player.data.Creator(it.optLong("id", 0), it.optString("nickname", "")) }
                    )
                }
                out
            }
        }
    }

    suspend fun artistSongsPage(id: Long, limit: Int = 50, offset: Int = 0, order: String = "hot"): Result<ArtistSongsPage> =
        runCatching {
            withContext(Dispatchers.IO) {
                if (useBackend) {
                    val resp = api.artistSongs(id, limit, offset, order)
                    ArtistSongsPage(songs = resp.songs, total = resp.total)
                } else {
                    val fields = mapOf(
                        "id" to id.toString(),
                        "limit" to limit.toString(),
                        "offset" to offset.toString(),
                        "order" to order,
                        "total" to "true"
                    )
                    val body = DirectNcmClient.apiPost("v1/artist/songs", fields)
                    NcmParser.artistSongsPage(JSONObject(body))
                }
            }
        }

    suspend fun artistSongs(id: Long, limit: Int = 50, offset: Int = 0, order: String = "hot"): Result<List<Song>> =
        artistSongsPage(id, limit, offset, order).map { it.songs }

    suspend fun newSongs(limit: Int = 30): Result<List<Song>> = runCatching {
        withContext(Dispatchers.IO) {
            if (useBackend) {
                api.newSongs(limit).songs
            } else {
                val body = DirectNcmClient.apiPost("personalized/newsong", mapOf("limit" to limit.toString()))
                val arr = JSONObject(body).optJSONArray("result") ?: JSONArray()
                val out = ArrayList<Song>(arr.length())
                for (i in 0 until arr.length()) {
                    val it = arr.optJSONObject(i) ?: continue
                    val s = it.optJSONObject("song") ?: it
                    out += NcmParser.toSong(s)
                }
                out
            }
        }
    }
}

/** Cache key for search results: paging parameters are part of the identity. */
data class SearchCacheKey(
    val keyword: String,
    val limit: Int,
    val offset: Int
)

/** Manual dependency injection container, created in HHMusicApp. */
class AppContainer(context: Context) {
    val localStore: LocalStore = LocalStore(context.applicationContext)
    val repository: MusicRepository = MusicRepository(local = localStore)
    val downloadManager: DownloadManager = DownloadManager(context.applicationContext, repository, localStore)
    val equalizerController: EqualizerController = EqualizerController(localStore)
    val playerController: PlayerController =
        PlayerController(context.applicationContext, repository, localStore, downloadManager)

    // Keep the repository runtime flags in sync with persisted user settings.
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    companion object {
        /**
         * Process-wide handle so [PlaybackService] (a separate Android component)
         * can reach the equalizer / download manager without a DI framework.
         */
        @Volatile
        var instance: AppContainer? = null
    }

    init {
        scope.launch { localStore.useBackend.collect { repository.useBackend = it } }
        scope.launch { localStore.audioQuality.collect { repository.audioQuality = it } }
    }
}

/** Tiny thread-safe LRU map used for in-memory response caches. */
private class LruCache<K, V>(maxEntries: Int) {
    private val map = object : LinkedHashMap<K, V>(maxEntries * 2, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean =
            size > maxEntries
    }

    @Synchronized
    operator fun get(key: K): V? = map[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        map[key] = value
    }
}
