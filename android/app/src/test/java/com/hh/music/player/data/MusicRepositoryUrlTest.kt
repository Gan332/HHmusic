package com.hh.music.player.data

import com.hh.music.player.network.ArtistSongsResponse
import com.hh.music.player.network.ArtistSearchResponse
import com.hh.music.player.network.HHMusicApi
import com.hh.music.player.network.LikeBody
import com.hh.music.player.network.LikeResponse
import com.hh.music.player.network.RecommendPlaylistResponse
import com.hh.music.player.network.ToplistResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repository contract for empty playable URLs (VIP/copyright-restricted tracks):
 * with the backend proxy (useBackend=true) a null `url` is a SUCCESS with
 * `isPlayable == false` — callers decide to surface it, never a network exception.
 */
class MusicRepositoryUrlTest {

    private class FakeApi(private val urlFor: (Long) -> SongUrl) : HHMusicApi {
        override suspend fun search(keyword: String, limit: Int, offset: Int) =
            throw UnsupportedOperationException()
        override suspend fun songDetail(ids: String) = throw UnsupportedOperationException()
        override suspend fun songUrl(id: Long, level: String): SongUrl = urlFor(id)
        override suspend fun lyric(id: Long) = throw UnsupportedOperationException()
        override suspend fun playlistDetail(id: Long) = throw UnsupportedOperationException()
        override suspend fun toplist(): ToplistResponse = throw UnsupportedOperationException()
        override suspend fun likeSong(body: LikeBody): LikeResponse = throw UnsupportedOperationException()
        override suspend fun recommendSongs(limit: Int): SongDetailResponse = throw UnsupportedOperationException()
        override suspend fun recommendPlaylists(limit: Int): RecommendPlaylistResponse = throw UnsupportedOperationException()
        override suspend fun artistSongs(id: Long, limit: Int, offset: Int, order: String): ArtistSongsResponse =
            throw UnsupportedOperationException()
        override suspend fun searchArtists(keyword: String, limit: Int, offset: Int): ArtistSearchResponse =
            throw UnsupportedOperationException()
        override suspend fun newSongs(limit: Int): SongDetailResponse = throw UnsupportedOperationException()
    }

    @Test
    fun `empty url is a successful but non-playable result`() {
        val repo = MusicRepository(
            api = FakeApi { SongUrl(id = it, url = null) },
            local = null
        ).apply { useBackend = true }
        val result = repo.songUrl(42L)
        assertTrue(result.isSuccess)
        val url = result.getOrThrow()
        assertEquals(42L, url.id)
        assertFalse(url.isPlayable)
    }

    @Test
    fun `playable url results in isPlayable true`() {
        val repo = MusicRepository(
            api = FakeApi { SongUrl(id = it, url = "https://example/42.flac", br = 320_000) },
            local = null
        ).apply { useBackend = true }
        val url = repo.songUrl(42L).getOrThrow()
        assertTrue(url.isPlayable)
        assertEquals("https://example/42.flac", url.url)
    }
}
