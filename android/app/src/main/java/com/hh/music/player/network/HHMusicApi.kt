package com.hh.music.player.network

import com.hh.music.player.data.Lyric
import com.hh.music.player.data.AlbumDetail
import com.hh.music.player.data.Artist
import com.hh.music.player.data.Playlist
import com.hh.music.player.data.SearchResponse
import com.hh.music.player.data.Song
import com.hh.music.player.data.SongDetailResponse
import com.hh.music.player.data.SongUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface HHMusicApi {
    @GET("search")
    suspend fun search(
        @Query("s") keyword: String,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0
    ): SearchResponse

    @GET("song/detail")
    suspend fun songDetail(@Query("ids") ids: String): SongDetailResponse

    @GET("song/url")
    suspend fun songUrl(
        @Query("id") id: Long,
        @Query("level") level: String = "exhigh"
    ): SongUrl

    @GET("lyric")
    suspend fun lyric(@Query("id") id: Long): Lyric

    @GET("playlist/detail")
    suspend fun playlistDetail(@Query("id") id: Long): Playlist

    @GET("toplist")
    suspend fun toplist(): ToplistResponse

    @POST("song/like")
    suspend fun likeSong(@Body body: LikeBody): LikeResponse

    // ---- New features endpoints ----
    @GET("recommend/songs")
    suspend fun recommendSongs(@Query("limit") limit: Int = 30): SongDetailResponse

    @GET("recommend/playlists")
    suspend fun recommendPlaylists(@Query("limit") limit: Int = 12): RecommendPlaylistResponse

    @GET("artist/songs")
    suspend fun artistSongs(
        @Query("id") id: Long,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("order") order: String = "hot"
    ): ArtistSongsResponse

    @GET("artist/search")
    suspend fun searchArtists(
        @Query("s") keyword: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): ArtistSearchResponse

    @GET("artist/albums")
    suspend fun artistAlbums(
        @Query("id") id: Long,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): ArtistAlbumsResponse

    @GET("album/detail")
    suspend fun albumDetail(@Query("id") id: Long): AlbumDetail

    @GET("search/hot")
    suspend fun hotSearch(): HotSearchResponse

    @GET("new/song")
    suspend fun newSongs(@Query("limit") limit: Int = 30): SongDetailResponse

    // ---- v1.7: playlist plaza ----
    @GET("playlist/catlist")
    suspend fun playlistCatlist(): PlaylistCatlistResponse

    @GET("top/playlist")
    suspend fun topPlaylists(
        @Query("cat") cat: String = "全部",
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
        @Query("order") order: String = "hot"
    ): TopPlaylistResponse

    // ---- v1.7: personal FM ----
    @GET("personal/fm")
    suspend fun personalFm(): SongDetailResponse
}

@Serializable
data class ToplistResponse(val code: Int = 0, val list: List<com.hh.music.player.data.ToplistItem> = emptyList())

@Serializable
data class LikeBody(val id: Long, val like: Boolean = true)

@Serializable
data class LikeResponse(val code: Int = 0, val id: Long = 0, val like: Boolean = true)

@Serializable
data class RecommendPlaylistResponse(val code: Int = 0, val list: List<RecommendPlaylistItem> = emptyList())

@Serializable
data class RecommendPlaylistItem(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String? = null,
    val playcount: Long = 0,
    val creator: com.hh.music.player.data.Creator? = null
) {
    val creatorName: String get() = creator?.nickname ?: ""

    val coverUrl: String get() = if (picUrl.isNullOrBlank()) "" else picUrl
}

@Serializable
data class ArtistSongsResponse(
    val code: Int = 0,
    val total: Int = 0,
    val songs: List<Song> = emptyList()
)

@Serializable
data class ArtistSearchResponse(
    val code: Int = 0,
    val total: Int = 0,
    val artists: List<Artist> = emptyList()
)

@Serializable
data class ArtistAlbumsResponse(
    val code: Int = 0,
    val more: Boolean = false,
    @SerialName("hotAlbums") val albums: List<com.hh.music.player.data.AlbumItem> = emptyList()
)

@Serializable
data class HotSearchResponse(val code: Int = 0, val hots: List<String> = emptyList())

// ---- v1.7: playlist plaza ----

@Serializable
data class PlaylistCatlistResponse(
    val code: Int = 0,
    val categories: Map<String, String> = emptyMap(),
    val sub: List<PlazaCategoryDto> = emptyList()
)

@Serializable
data class PlazaCategoryDto(
    val name: String = "",
    val category: Int = -1,
    val hot: Boolean = false
)

@Serializable
data class TopPlaylistResponse(
    val code: Int = 0,
    val total: Int = 0,
    val more: Boolean = false,
    val list: List<PlazaPlaylistDto> = emptyList()
)

@Serializable
data class PlazaPlaylistDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("picUrl") val picUrl: String? = null,
    val playcount: Long = 0,
    val creator: com.hh.music.player.data.Creator? = null
) {
    val coverUrl: String get() = picUrl.orEmpty()
}
