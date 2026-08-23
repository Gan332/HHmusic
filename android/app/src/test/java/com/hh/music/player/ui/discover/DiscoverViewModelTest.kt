package com.hh.music.player.ui.discover

import com.hh.music.player.data.Album
import com.hh.music.player.data.Artist
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Song
import com.hh.music.player.data.SongDetailResponse
import com.hh.music.player.data.SongUrl
import com.hh.music.player.network.ArtistSongsResponse
import com.hh.music.player.network.ArtistSearchResponse
import com.hh.music.player.network.HHMusicApi
import com.hh.music.player.network.LikeBody
import com.hh.music.player.network.LikeResponse
import com.hh.music.player.network.RecommendPlaylistItem
import com.hh.music.player.network.RecommendPlaylistResponse
import com.hh.music.player.network.ToplistResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DiscoverViewModel contract: a refresh supersedes its in-flight predecessor
 * (stale batches never publish), and a recent refresh is served from the
 * in-memory cache instead of re-fetching.
 *
 * NB: the discovery cache is process-wide, so every test starts by forcing a
 * refresh (which ignores any leftover cache) to make the JVM share safe.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {

    /** Each section call registers a deferred; tests complete them in any order. */
    private class FlakyApi : HHMusicApi {
        val rec = mutableListOf<CompletableDeferred<SongDetailResponse>>()
        val fresh = mutableListOf<CompletableDeferred<SongDetailResponse>>()
        val pl = mutableListOf<CompletableDeferred<RecommendPlaylistResponse>>()

        override suspend fun recommendSongs(limit: Int): SongDetailResponse {
            val d = CompletableDeferred<SongDetailResponse>()
            rec += d
            return d.await()
        }

        override suspend fun newSongs(limit: Int): SongDetailResponse {
            val d = CompletableDeferred<SongDetailResponse>()
            fresh += d
            return d.await()
        }

        override suspend fun recommendPlaylists(limit: Int): RecommendPlaylistResponse {
            val d = CompletableDeferred<RecommendPlaylistResponse>()
            pl += d
            return d.await()
        }

        override suspend fun search(keyword: String, limit: Int, offset: Int) =
            throw UnsupportedOperationException()
        override suspend fun songDetail(ids: String) = throw UnsupportedOperationException()
        override suspend fun songUrl(id: Long, level: String): SongUrl = throw UnsupportedOperationException()
        override suspend fun lyric(id: Long) = throw UnsupportedOperationException()
        override suspend fun playlistDetail(id: Long) = throw UnsupportedOperationException()
        override suspend fun toplist(): ToplistResponse = throw UnsupportedOperationException()
        override suspend fun likeSong(body: LikeBody): LikeResponse = throw UnsupportedOperationException()
        override suspend fun artistSongs(id: Long, limit: Int, offset: Int, order: String): ArtistSongsResponse =
            throw UnsupportedOperationException()
        override suspend fun searchArtists(keyword: String, limit: Int, offset: Int): ArtistSearchResponse =
            throw UnsupportedOperationException()
        override suspend fun artistAlbums(id: Long, limit: Int, offset: Int): com.hh.music.player.network.ArtistAlbumsResponse =
            throw UnsupportedOperationException()
        override suspend fun albumDetail(id: Long): com.hh.music.player.data.AlbumDetail =
            throw UnsupportedOperationException()
        override suspend fun hotSearch(): com.hh.music.player.network.HotSearchResponse =
            throw UnsupportedOperationException()
        override suspend fun playlistCatlist(): com.hh.music.player.network.PlaylistCatlistResponse =
            throw UnsupportedOperationException()
        override suspend fun topPlaylists(cat: String, limit: Int, offset: Int, order: String): com.hh.music.player.network.TopPlaylistResponse =
            throw UnsupportedOperationException()
        override suspend fun personalFm(): SongDetailResponse = throw UnsupportedOperationException()
    }

    private fun section(tag: String) = Song(
        id = tag.hashCode().toLong(),
        name = tag,
        artists = listOf(Artist(name = "a")),
        album = Album(name = "al")
    )

    private fun completeAt(api: FlakyApi, tag: String, idx: Int) {
        api.rec[idx].complete(SongDetailResponse(songs = listOf(section("$tag-rec"))))
        api.fresh[idx].complete(SongDetailResponse(songs = listOf(section("$tag-new"))))
        api.pl[idx].complete(
            RecommendPlaylistResponse(list = listOf(RecommendPlaylistItem(id = 1L, name = "$tag-pl")))
        )
    }

    @Test
    fun `a newer refresh supersedes the in-flight one`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = FlakyApi()
        val vm = DiscoverViewModel(
            MusicRepository(api, ioDispatcher = StandardTestDispatcher(testScheduler)).apply { useBackend = true }
        )
        vm.refresh(force = true) // supersede the cached/no-op init before it runs
        runCurrent()
        assertEquals(1, api.rec.size)
        assertTrue(vm.state.value.refreshing)

        vm.refresh(force = true) // supersedes batch 0 → batch 1 pending
        runCurrent()
        assertEquals(2, api.rec.size)

        completeAt(api, "old", 0) // stale batch lands later — must be discarded
        runCurrent()
        assertTrue(vm.state.value.recommend.data.isEmpty())

        completeAt(api, "new", 1)
        runCurrent()
        assertEquals("new-rec", vm.state.value.recommend.data.single().name)
        assertEquals("new-pl", vm.state.value.playlists.data.single().name)
        assertFalse(vm.state.value.refreshing)
        Dispatchers.resetMain()
    }

    @Test
    fun `a successful refresh warms the cache for the next call`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = FlakyApi()
        val vm = DiscoverViewModel(
            MusicRepository(api, ioDispatcher = StandardTestDispatcher(testScheduler)).apply { useBackend = true }
        )
        runCurrent()
        vm.refresh(force = true) // supersede whatever init did (cache or network)
        runCurrent()
        assertTrue(api.rec.size >= 1)

        completeAt(api, "warm", api.rec.size - 1)
        runCurrent()
        assertFalse(vm.state.value.refreshing)
        assertEquals("warm-pl", vm.state.value.playlists.data.single().name)

        val callsBefore = api.rec.size
        vm.refresh(force = false) // cache is still fresh → no new network call
        runCurrent()
        assertEquals(callsBefore, api.rec.size)
        assertEquals("warm-rec", vm.state.value.recommend.data.single().name)
        Dispatchers.resetMain()
    }
}
