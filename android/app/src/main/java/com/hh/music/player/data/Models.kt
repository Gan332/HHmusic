package com.hh.music.player.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Domain model for a track. Stable shape produced by our backend
 * (server/src/index.js normalizes NetEase data to this).
 */
@Serializable
data class Song(
    val id: Long,
    val name: String,
    val artists: List<Artist> = emptyList(),
    val album: Album = Album(),
    val duration: Long = 0L,      // milliseconds
    val fee: Int = 0               // 0 free, 1 vip, 4 album-only, 8 trial
) {
    val artistText: String
        get() = artists.joinToString(", ") { it.name }.ifBlank { "未知艺术家" }

    val coverUrl: String
        get() = album.picUrl?.ifBlank { null } ?: ""
}

@Serializable
data class Artist(val id: Long = 0, val name: String = "")

@Serializable
data class Album(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String? = null
)

/** Playable URL info for a song. */
@Serializable
data class SongUrl(
    val id: Long = 0,
    val url: String? = null,
    val br: Long = 0,
    val size: Long = 0,
    val type: String? = null,
    val md5: String? = null,
    @SerialName("freeTrialInfo") val freeTrialInfo: FreeTrialInfo? = null
) {
    val isPlayable: Boolean get() = !url.isNullOrBlank()
}

@Serializable
data class FreeTrialInfo(
    val start: Long = 0,
    val end: Long = 0
)

/** Time-synced lyric result. */
@Serializable
data class Lyric(
    val lrc: String = "",
    val tlyric: String = "",
    val romalrc: String = "",
    val yrc: String = ""
)

@Serializable
data class SearchResponse(
    val code: Int = 0,
    @SerialName("songCount") val songCount: Int = 0,
    val songs: List<Song> = emptyList()
)

@Serializable
data class SongDetailResponse(
    val code: Int = 0,
    val songs: List<Song> = emptyList()
)

@Serializable
data class Playlist(
    val id: Long = 0,
    val name: String = "",
    @SerialName("coverImgUrl") val coverImgUrl: String? = null,
    val creator: Creator? = null,
    val tracks: List<Song> = emptyList()
)

@Serializable
data class Creator(val id: Long = 0, val nickname: String = "")

@Serializable
data class ToplistItem(
    val id: Long = 0,
    val name: String = "",
    @SerialName("coverImgUrl") val coverImgUrl: String? = null,
    val description: String? = null,
    @SerialName("updateFrequency") val updateFrequency: String? = null
)
