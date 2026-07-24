package com.hh.music.player.network

import com.hh.music.player.data.Album
import com.hh.music.player.data.Artist
import com.hh.music.player.data.Song
import com.hh.music.player.data.ToplistItem
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

    /** Search stores results under result.songs. */
    fun searchSongs(root: JSONObject): List<Song> {
        val result = root.optJSONObject("result") ?: root
        return songList(result, "songs")
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
}
