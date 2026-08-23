package com.hh.music.player.data

import android.content.Context
import com.hh.music.player.data.local.LocalStore
import com.hh.music.player.data.offline.DownloadManager
import com.hh.music.player.network.DirectNcmClient
import com.hh.music.player.network.NcmParser
import com.hh.music.player.network.HHMusicApi
import com.hh.music.player.network.NetworkModule
import com.hh.music.player.network.RecommendPlaylistItem
import com.hh.music.player.network.SubscribeBody
import com.hh.music.player.network.ToplistResponse
import kotlinx.coroutines.launch
import com.hh.music.player.playback.PlayerController
import com.hh.music.player.playback.EqualizerController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
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
    private val local: LocalStore? = null,
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /** Runtime flags, kept in sync with [LocalStore] by [AppContainer]. */
    @Volatile var useBackend: Boolean = false
    @Volatile var audioQuality: String = "exhigh"

    /** True when a MUSIC_U login token is loaded (direct mode only). */
    @Volatile var hasLoginCookie: Boolean = false

    companion object {
        private const val TAG = "HHMusicRepo"
    }

    // Small in-memory LRU caches: lyrics are re-opened every time you re-visit the
    // player screen, and search results are re-fetched on every keystroke debounce.
    private val lyricCache = LruCache<Long, Lyric>(30)
    private val searchCache = LruCache<SearchCacheKey, SearchPage>(30)
    private val plazaCache = LruCache<PlazaCacheKey, PlaylistPlazaPage>(20)
    private val userPlaylistCache = LruCache<Long, List<UserPlaylist>>(4)
    // ---------------- direct (NetEase) implementations ----------------

    suspend fun search(keyword: String, limit: Int = 30, offset: Int = 0): Result<SearchPage> =
        runCatching {
            val key = SearchCacheKey(keyword, limit, offset)
            searchCache[key]?.let { return@runCatching it }
            val page = withContext(ioDispatcher) {
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
            withContext(ioDispatcher) {
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

    /** Real-time hot search keywords; callers fall back to a static list when empty. */
    suspend fun hotSearches(limit: Int = 12): Result<List<String>> =
        runCatching {
            withContext(ioDispatcher) {
                if (useBackend) {
                    api.hotSearch().hots.take(limit)
                } else {
                    val body = DirectNcmClient.apiPost("search/hot", mapOf("type" to "1111"))
                    val hots = JSONObject(body).optJSONObject("result")?.optJSONArray("hots") ?: JSONArray()
                    val out = ArrayList<String>(hots.length())
                    for (i in 0 until hots.length()) {
                        if (out.size >= limit) break
                        val word = hots.optJSONObject(i)?.optString("first").orEmpty().trim()
                        if (word.isNotEmpty()) out += word
                    }
                    out
                }
            }
        }

    suspend fun songDetail(ids: List<Long>): Result<List<Song>> = runCatching {
        withContext(ioDispatcher) {
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
     *
     * Anonymous requests are frequently denied anything above standard quality,
     * so when the configured level yields no URL we retry once at "standard"
     * before giving up — this alone recovers playback for most logged-out users.
     */
    suspend fun songUrl(id: Long): Result<SongUrl> = runCatching {
        withContext(ioDispatcher) {
            if (useBackend) {
                api.songUrl(id)
            } else {
                // 免登录播放: no MUSIC_U → grab an anonymous device session once so
                // standard-quality URLs resolve without an account.
                if (!hasLoginCookie && DirectNcmClient.getCookie() == null) {
                    DirectNcmClient.ensureAnonymousCookie()
                }
                val levels = linkedSetOf(audioQuality, "standard")
                var first: JSONObject? = null
                for (level in levels) {
                    val payload = mapOf<String, Any>(
                        "ids" to JSONArray().put(id).toString(),
                        "level" to level,
                        "encodeType" to "flac"
                    )
                    val body = DirectNcmClient.eapiPost("song/enhance/player/url/v1", payload)
                    first = JSONObject(body).optJSONArray("data")?.optJSONObject(0)
                    val candidate = first?.optString("url", "")
                    if (!candidate.isNullOrEmpty()) break
                    first = null
                }
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
        val lrc = withContext(ioDispatcher) {
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
        withContext(ioDispatcher) {
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
        withContext(ioDispatcher) {
            if (useBackend) {
                api.toplist().list
            } else {
                val body = DirectNcmClient.apiPost("toplist/detail", emptyMap())
                NcmParser.toplistItems(JSONObject(body))
            }
        }
    }

    suspend fun recommendSongs(limit: Int = 30): Result<List<Song>> = runCatching {
        withContext(ioDispatcher) {
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
        withContext(ioDispatcher) {
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
            withContext(ioDispatcher) {
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

    /**
     * Push a favorite toggle to the cloud (direct mode + logged in only —
     * callers gate via [CloudSync]). Requires the MUSIC_U cookie.
     */
    suspend fun likeSong(songId: Long, like: Boolean): Result<Unit> =
        com.hh.music.player.network.LoginClient.like(songId, like, ioDispatcher)

    /** Paged album list for an artist. Direct path uses eapi `artist/albums`. */
    suspend fun artistAlbumsPage(id: Long, limit: Int = 50, offset: Int = 0): Result<ArtistAlbumsPage> =
        runCatching {
            withContext(ioDispatcher) {
                if (useBackend) {
                    val resp = api.artistAlbums(id, limit, offset)
                    ArtistAlbumsPage(albums = resp.albums, more = resp.more)
                } else {
                    val payload = mapOf<String, Any>(
                        "id" to id.toString(),
                        "limit" to limit.toString(),
                        "offset" to offset.toString()
                    )
                    val body = DirectNcmClient.eapiPost("artist/albums", payload)
                    NcmParser.artistAlbumsPage(JSONObject(body))
                }
            }
        }

    /** Album metadata + full track list. Direct path uses plain `/api/v1/album/{id}`. */
    suspend fun albumDetail(id: Long): Result<AlbumDetail> =
        runCatching {
            withContext(ioDispatcher) {
                if (useBackend) {
                    api.albumDetail(id)
                } else {
                    val body = DirectNcmClient.apiGet("v1/album/$id")
                    NcmParser.albumDetail(JSONObject(body))
                }
            }
        }

    suspend fun newSongs(limit: Int = 30): Result<List<Song>> = runCatching {
        withContext(ioDispatcher) {
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

    // ---------------- v1.7: playlist plaza ----------------

    /** All playlist categories (chips row on the plaza screen). */
    suspend fun playlistCategories(): Result<List<PlazaCategory>> = runCatching {
        withContext(ioDispatcher) {
            if (useBackend) {
                val resp = api.playlistCatlist()
                resp.sub.map { PlazaCategory(it.category, it.name) }.distinctBy { it.name }
            } else {
                NcmParser.plazaCategories(JSONObject(DirectNcmClient.apiPost("playlist/catlist", emptyMap())))
            }
        }
    }

    /**
     * One page of the playlist plaza. `order` is "hot" or "new"; paging via offset.
     * Results are cached per (cat, order, limit, offset) like search.
     */
    suspend fun topPlaylists(cat: String, limit: Int = 30, offset: Int = 0, order: String = "hot"): Result<PlaylistPlazaPage> =
        runCatching {
            val key = PlazaCacheKey(cat, order, limit, offset)
            plazaCache[key]?.let { return@runCatching it }
            val page = withContext(ioDispatcher) {
                if (useBackend) {
                    val resp = api.topPlaylists(cat, limit, offset, order)
                    PlaylistPlazaPage(
                        list = resp.list.map {
                            PlazaPlaylist(
                                id = it.id,
                                name = it.name,
                                picUrl = it.picUrl,
                                playcount = it.playcount,
                                creator = it.creator
                            )
                        },
                        total = resp.total,
                        more = resp.more
                    )
                } else {
                    val fields = mapOf(
                        "cat" to cat,
                        "limit" to limit.toString(),
                        "offset" to offset.toString(),
                        "order" to order
                    )
                    NcmParser.playlistPlazaPage(JSONObject(DirectNcmClient.apiPost("top/playlist", fields)))
                }
            }
            plazaCache[key] = page
            page
        }

    // ---------------- v1.7: personal FM ----------------

    /**
     * Personal FM: returns a batch of songs to play. Direct mode uses the plain
     * api path; backend mode proxies through /api/personal/fm.
     */
    suspend fun personalFm(): Result<List<Song>> = runCatching {
        withContext(ioDispatcher) {
            if (useBackend) {
                api.personalFm().songs
            } else {
                val body = DirectNcmClient.apiPost("personal_fm", mapOf("limit" to "6"))
                val dataArr = JSONObject(body).optJSONArray("data") ?: JSONArray()
                val out = ArrayList<Song>(dataArr.length())
                for (i in 0 until dataArr.length()) {
                    val s = dataArr.optJSONObject(i) ?: continue
                    out += NcmParser.toSong(s)
                }
                out
            }
        }
    }

    // ---------------- v1.8: my cloud playlists ----------------

    /**
     * The logged-in user's cloud playlists (first row is usually the immutable
     * "我喜欢的音乐" liked-songs list). Cached per uid; empty result is NOT cached
     * so a transient upstream hiccup can be retried.
     */
    suspend fun userPlaylists(uid: Long): Result<List<UserPlaylist>> = runCatching {
        withContext(ioDispatcher) {
            userPlaylistCache[uid]?.let { return@withContext it }
            val rows = if (useBackend) {
                api.userPlaylists(uid).playlist.map {
                    UserPlaylist(
                        id = it.id,
                        name = it.name,
                        coverImgUrl = it.coverImgUrl,
                        trackCount = it.trackCount,
                        creator = it.creator,
                        specialType = it.specialType
                    )
                }
            } else {
                NcmParser.userPlaylists(
                    JSONObject(
                        DirectNcmClient.apiPost(
                            "user/playlist",
                            mapOf(
                                "uid" to uid.toString(),
                                "limit" to "30",
                                "offset" to "0",
                                "includeVideo" to "true"
                            )
                        )
                    )
                )
            }
            if (rows.isNotEmpty()) userPlaylistCache[uid] = rows
            rows
        }
    }

    /** Cloud-subscribe (true) or unsubscribe (false) a playlist. t=1/2 per NetEase. */
    suspend fun subscribePlaylist(id: Long, subscribe: Boolean): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            val code = if (useBackend) {
                api.subscribePlaylist(SubscribeBody(id = id, t = if (subscribe) 1 else 2)).code
            } else {
                JSONObject(
                    DirectNcmClient.apiPost(
                        "playlist/subscribe",
                        mapOf("id" to id.toString(), "t" to if (subscribe) "1" else "2")
                    )
                ).optInt("code", -1)
            }
            if (code != 200) throw Exception("云端歌单订阅失败 (code=$code)")
        }
    }
}

/** Cache key for search results: paging parameters are part of the identity. */
data class SearchCacheKey(
    val keyword: String,
    val limit: Int,
    val offset: Int
)

/** Cache key for playlist plaza pages. */
data class PlazaCacheKey(
    val cat: String,
    val order: String,
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
    // Application scope for settings collection; declared before its first user.
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    val cloudSync: CloudSync = CloudSync(repository, localStore, scope)

    // Keep the repository runtime flags in sync with persisted user settings.
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
        // Restore the login session (MUSIC_U) and keep the flag in sync for CloudSync.
        // A fresh (previously unseen) non-blank token triggers a one-shot favorites
        // reconciliation — covers QR scan AND cookie-paste logins with zero UI wiring.
        scope.launch {
            var previousToken = ""
            localStore.loginCookie.collect { token ->
                DirectNcmClient.setCookie(token.takeIf { it.isNotBlank() }?.let { "MUSIC_U=$it" })
                repository.hasLoginCookie = token.isNotBlank()
                if (token.isNotBlank() && token != previousToken) {
                    val uid = localStore.userId.firstOrNull()
                    if (uid != null && uid > 0) {
                        cloudSync.reconcileFavorites(uid)?.let { r ->
                            android.util.Log.i(
                                "HHMusicSync",
                                "favorites reconciled: +${r.cloudAddedLocally} local, ${r.pushedToCloud} pushed, ${r.cloudPushFailures} failed"
                            )
                        }
                    }
                }
                previousToken = token
            }
        }
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
