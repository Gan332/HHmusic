package com.hh.music.player.network

import com.hh.music.player.data.Album
import com.hh.music.player.data.AlbumDetail
import com.hh.music.player.data.AlbumItem
import com.hh.music.player.data.Artist
import com.hh.music.player.data.ArtistAlbumsPage
import com.hh.music.player.data.ArtistSearchPage
import com.hh.music.player.data.ArtistSongsPage
import com.hh.music.player.data.Creator
import com.hh.music.player.data.PlaylistPlazaPage
import com.hh.music.player.data.PlazaCategory
import com.hh.music.player.data.PlazaPlaylist
import com.hh.music.player.data.SearchPage
import com.hh.music.player.data.Song
import com.hh.music.player.data.ToplistItem
import com.hh.music.player.data.UserPlaylist
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses raw NetEase JSON (ar/al/dt shape, as returned by api/cloudsearch,
 * api/v3/song/detail, api/artist/songs, etc.) into our [Song] domain model.
 *
 * Centralizing this lets [MusicRepository] keep its clean public API while
 * switching the data source from our backend to direct NetEase calls.
 */
object NcmParser {

    fun toSong(s: JSONObject): Song {
        val al = s.optJSONObject("al") ?: s.optJSONObject("album")
        val ar = s.optJSONArray("ar") ?: s.optJSONArray("artists")
        val artists = mutableListOf<Artist>()
        ar?.let { for (i in 0 until it.length()) artists += Artist(it.optJSONObject(i)?.optLong("id", 0) ?: 0, it.optJSONObject(i)?.optString("name", "") ?: "") }
        return Song(
            id = s.optLong("id", 0),
            name = s.optString("name", "未知歌曲"),
            artists = artists,
            album = Album(
                id = al?.optLong("id", 0) ?: 0,
                name = al?.optString("name", "") ?: "",
                picUrl = al?.optString("picUrl", null)
            ),
            duration = s.optLong("dt", s.optLong("duration", 0)),
            fee = s.optInt("fee", 0)
        )
    }

    fun songList(root: JSONObject, key: String = "songs"): List<Song> {
        val arr = root.optJSONArray(key) ?: return emptyList()
        val out = ArrayList<Song>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { out += toSong(it) }
        }
        return out
    }

    /** Parse cloud-search `type=100` artist results. */
    fun artists(root: JSONObject, key: String = "artists"): List<Artist> {
        val arr = root.optJSONArray(key) ?: return emptyList()
        val out = ArrayList<Artist>(arr.length())
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            out += Artist(
                id = a.optLong("id", 0),
                name = a.optString("name", ""),
                picUrl = a.optString("img1v1Url", null)
                    ?.takeIf { it.isNotBlank() && it != "null" }
                    ?: a.optString("picUrl", null)?.takeIf { it.isNotBlank() && it != "null" }
            )
        }
        return out
    }

    fun artistSearchPage(root: JSONObject): ArtistSearchPage {
        val result = root.optJSONObject("result") ?: root
        return ArtistSearchPage(
            artists = artists(result),
            total = result.optInt("artistCount", result.optInt("total", 0))
        )
    }

/** Search stores results under result.songs (with result.songCount = total). */
    fun searchPage(root: JSONObject): SearchPage {
        val result = root.optJSONObject("result") ?: root
        return SearchPage(
            songs = songList(result, "songs"),
            total = result.optInt("songCount", result.optInt("total", 0))
        )
    }

    fun artistSongsPage(root: JSONObject): ArtistSongsPage =
        ArtistSongsPage(
            songs = songList(root, "songs"),
            total = root.optInt("total", root.optInt("songCount", 0))
        )

    /**
     * Artist albums page. Direct eapi `artist/albums` returns `hotAlbums` +
     * `more`; the backend fallback mirrors the same shape.
     */
    fun artistAlbumsPage(root: JSONObject): ArtistAlbumsPage =
        ArtistAlbumsPage(
            albums = albumItems(root.optJSONArray("hotAlbums") ?: root.optJSONArray("albums")),
            more = root.optBoolean("more", false)
        )

    private fun albumItems(arr: JSONArray?): List<AlbumItem> {
        if (arr == null) return emptyList()
        val out = ArrayList<AlbumItem>(arr.length())
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            val id = a.optLong("id", 0)
            if (id <= 0) continue
            out += AlbumItem(
                id = id,
                name = a.optString("name", ""),
                picUrl = a.optString("picUrl", null)
                    ?.takeIf { it.isNotBlank() && it != "null" }
                    ?: a.optString("blurPicUrl", null)?.takeIf { it.isNotBlank() && it != "null" },
                publishTime = a.optLong("publishTime", 0),
                songCount = a.optInt("size", 0)
            )
        }
        return out
    }

    /** Album detail: plain `/api/v1/album/{id}` shape — `{album: {...}, songs: [...]}`. */
    fun albumDetail(root: JSONObject): AlbumDetail {
        val al = root.optJSONObject("album") ?: JSONObject()
        val pic = al.optString("picUrl", null)
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?: al.optString("blurPicUrl", null)?.takeIf { it.isNotBlank() && it != "null" }
        return AlbumDetail(
            id = al.optLong("id", 0),
            name = al.optString("name", ""),
            coverImgUrl = pic,
            description = al.optString("description", null)?.takeIf { it.isNotBlank() },
            publishTime = al.optLong("publishTime", 0),
            songs = songList(root, "songs")
        )
    }

    fun toplistItems(root: JSONObject): List<ToplistItem> {
        val arr = root.optJSONArray("list") ?: return emptyList()
        val out = ArrayList<ToplistItem>(arr.length())
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            out += ToplistItem(
                id = t.optLong("id", 0),
                name = t.optString("name", ""),
                coverImgUrl = t.optString("coverImgUrl", null),
                description = t.optString("description", null),
                updateFrequency = t.optString("updateFrequency", null)
            )
        }
        return out
    }

    // ---------------- v1.7: playlist plaza ----------------

    /** Parse a `playlists` array (top/playlist shape: coverImgUrl + playCount). */
    fun plazaPlaylists(root: JSONObject, key: String = "playlists"): List<PlazaPlaylist> {
        val arr = root.optJSONArray(key) ?: return emptyList()
        val out = ArrayList<PlazaPlaylist>(arr.length())
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val id = p.optLong("id", 0)
            if (id <= 0) continue
            out += PlazaPlaylist(
                id = id,
                name = p.optString("name", ""),
                picUrl = p.optString("coverImgUrl", null)?.takeIf { it.isNotBlank() && it != "null" }
                    ?: p.optString("picUrl", null)?.takeIf { it.isNotBlank() && it != "null" },
                playcount = p.optLong("playcount", p.optLong("playCount", 0)),
                creator = p.optJSONObject("creator")?.let {
                    Creator(it.optLong("id", 0), it.optString("nickname", ""))
                }
            )
        }
        return out
    }

    /** One page of the plaza: playlists + total + more flag. */
    fun playlistPlazaPage(root: JSONObject): PlaylistPlazaPage =
        PlaylistPlazaPage(
            list = plazaPlaylists(root),
            total = root.optInt("total", 0),
            more = root.optBoolean("more", false)
        )

    /** Category list from /playlist/catlist: `categories` map + flat `sub` entries. */
    fun plazaCategories(root: JSONObject): List<PlazaCategory> {
        val subs = root.optJSONArray("sub") ?: return emptyList()
        val names = LinkedHashSet<String>()
        for (i in 0 until subs.length()) {
            val c = subs.optJSONObject(i) ?: continue
            val name = c.optString("name", "").trim()
            if (name.isNotEmpty()) names += name
        }
        // Preserve the upstream category grouping order when available.
        val ordered = ArrayList<PlazaCategory>()
        val cats = root.optJSONObject("categories")
        if (cats != null) {
            val keys = mutableListOf<Int>()
            for (k in cats.keys()) keys += k.toIntOrNull() ?: continue
            keys.sort()
            for (k in keys) ordered += PlazaCategory(k, cats.optString(k.toString(), ""))
        }
        for (name in names) {
            if (ordered.none { it.name == name }) ordered += PlazaCategory(-1, name)
        }
        return ordered
    }

    // ---------------- v1.8: my cloud playlists ----------------

    /**
     * Parse `/user/playlist` → `{playlist: [...]}` into normalized rows.
     * Defensive: missing/blank arrays yield an empty list; entries without a
     * positive id are skipped. The first row is usually the immutable
     * "我喜欢的音乐" liked-songs list (`specialType == 5`).
     */
    fun userPlaylists(root: JSONObject): List<UserPlaylist> {
        val arr = root.optJSONArray("playlist") ?: return emptyList()
        val out = ArrayList<UserPlaylist>(arr.length())
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val id = p.optLong("id", 0)
            if (id <= 0) continue
            out += UserPlaylist(
                id = id,
                name = p.optString("name", ""),
                coverImgUrl = p.optString("coverImgUrl", null)?.takeIf { it.isNotBlank() && it != "null" },
                trackCount = p.optInt("trackCount", 0),
                creator = p.optJSONObject("creator")?.let {
                    Creator(it.optLong("userId", it.optLong("id", 0)), it.optString("nickname", ""))
                },
                specialType = p.optInt("specialType", 0)
            )
        }
        return out
    }
}
