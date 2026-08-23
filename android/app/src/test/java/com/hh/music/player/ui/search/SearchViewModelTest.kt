package com.hh.music.player.ui.search

import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.SearchResponse
import com.hh.music.player.data.Song
import com.hh.music.player.data.AlbumDetail
import com.hh.music.player.network.ArtistSongsResponse
import com.hh.music.player.network.ArtistAlbumsResponse
import com.hh.music.player.network.ArtistSearchResponse
import com.hh.music.player.network.HHMusicApi
import com.hh.music.player.network.HotSearchResponse
import com.hh.music.player.network.LikeBody
import com.hh.music.player.network.LikeResponse
import com.hh.music.player.network.RecommendPlaylistResponse
import com.hh.music.player.data.SongUrl
import com.hh.music.player.data.SongDetailResponse
import com.hh.music.player.network.ToplistResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SearchViewModel contract under real concurrency: debounce collapses fast
 * typing, and a slow stale response must never overwrite the newest results.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private class FakeApi : HHMusicApi {
        @Volatile var calls = 0
        @Volatile var handler: (suspend (String, Int, Int) -> SearchResponse)? = null

        override suspend fun search(keyword: String, limit: Int, offset: Int): SearchResponse {
            calls++
            return handler?.invoke(keyword, limit, offset) ?: SearchResponse()
        }

        override suspend fun songDetail(ids: String) = throw UnsupportedOperationException()
        override suspend fun songUrl(id: Long, level: String): SongUrl = throw UnsupportedOperationException()
        override suspend fun lyric(id: Long) = throw UnsupportedOperationException()
        override suspend fun playlistDetail(id: Long) = throw UnsupportedOperationException()
        override suspend fun toplist(): ToplistResponse = throw UnsupportedOperationException()
        override suspend fun likeSong(body: LikeBody): LikeResponse = throw UnsupportedOperationException()
        override suspend fun recommendSongs(limit: Int) = throw UnsupportedOperationException()
        override suspend fun recommendPlaylists(limit: Int): RecommendPlaylistResponse = throw UnsupportedOperationException()
        override suspend fun artistSongs(id: Long, limit: Int, offset: Int, order: String): ArtistSongsResponse =
            throw UnsupportedOperationException()
        override suspend fun searchArtists(keyword: String, limit: Int, offset: Int): ArtistSearchResponse =
            ArtistSearchResponse()
        override suspend fun artistAlbums(id: Long, limit: Int, offset: Int): ArtistAlbumsResponse =
            ArtistAlbumsResponse()
        override suspend fun albumDetail(id: Long): AlbumDetail = throw UnsupportedOperationException()
        override suspend fun hotSearch(): HotSearchResponse = HotSearchResponse()
        override suspend fun newSongs(limit: Int) = throw UnsupportedOperationException()
        override suspend fun playlistCatlist(): com.hh.music.player.network.PlaylistCatlistResponse =
            throw UnsupportedOperationException()
        override suspend fun topPlaylists(cat: String, limit: Int, offset: Int, order: String): com.hh.music.player.network.TopPlaylistResponse =
            throw UnsupportedOperationException()
        override suspend fun personalFm(): SongDetailResponse = throw UnsupportedOperationException()
        override suspend fun userPlaylists(uid: Long, limit: Int, offset: Int): com.hh.music.player.network.UserPlaylistsResponse =
            throw UnsupportedOperationException()
        override suspend fun subscribePlaylist(body: com.hh.music.player.network.SubscribeBody): com.hh.music.player.network.SubscribeResponse =
            throw UnsupportedOperationException()
    }

    private fun song(name: String) = Song(id = name.hashCode().toLong(), name = name)

    @Test
    fun `rapid typing collapses into a single debounced request`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = FakeApi()
        val vm = SearchViewModel(
            MusicRepository(api, ioDispatcher = StandardTestDispatcher(testScheduler)).apply { useBackend = true }
        )
        api.handler = { kw, _, _ -> SearchResponse(songCount = 1, songs = listOf(song(kw))) }

        vm.onQueryChange("a")
        advanceTimeBy(100) // below the 350ms debounce
        vm.onQueryChange("ab")
        advanceTimeBy(400)
        runCurrent()

        assertEquals(1, api.calls)
        assertEquals("ab", vm.state.value.query)
        assertEquals(listOf("ab"), vm.state.value.results.map { it.name })
        Dispatchers.resetMain()
    }

    @Test
    fun `a stale slow response never overwrites the newest results`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = FakeApi()
        val vm = SearchViewModel(
            MusicRepository(api, ioDispatcher = StandardTestDispatcher(testScheduler)).apply { useBackend = true }
        )
        val slow = CompletableDeferred<SearchResponse>()
        api.handler = { kw, _, _ ->
            if (kw == "a") slow.await() else SearchResponse(songCount = 1, songs = listOf(song(kw)))
        }

        vm.onQueryChange("a")
        advanceTimeBy(400) // "a" issued, stuck on slow.await (IO thread)
        vm.onQueryChange("b")
        advanceTimeBy(400) // cancels "a", "b" completes immediately
        runCurrent()
        assertEquals(listOf("b"), vm.state.value.results.map { it.name })

        // The stale "a" lands afterwards — must be discarded.
        slow.complete(SearchResponse(songCount = 1, songs = listOf(song("late"))))
        runCurrent()
        assertEquals(listOf("b"), vm.state.value.results.map { it.name })
        Dispatchers.resetMain()
    }

    @Test
    fun `loadMore appends pages and dedupes overlapping ids`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = FakeApi()
        val vm = SearchViewModel(
            MusicRepository(api, ioDispatcher = StandardTestDispatcher(testScheduler)).apply { useBackend = true }
        )
        api.handler = { kw, limit, offset ->
            val first = offset
            val count = minOf(limit, 45 - offset)
            val ids = (first until first + count).toList()
            // Page 2 deliberately repeats the first song id of page 1.
            val overlaps = if (offset > 0) listOf(first - 1) else emptyList()
            SearchResponse(
                songCount = 45,
                songs = (ids + overlaps).map { song(kw + it) }
            )
        }

        vm.onQueryChange("x")
        advanceTimeBy(400)
        runCurrent()
        vm.loadMore()
        runCurrent()

        assertEquals(45, vm.state.value.results.size)
        Dispatchers.resetMain()
    }
}
