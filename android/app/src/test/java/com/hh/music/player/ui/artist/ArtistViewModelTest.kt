package com.hh.music.player.ui.artist

import com.hh.music.player.data.ArtistSongsPage
import com.hh.music.player.data.Song
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

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistViewModelTest {

    @Test
    fun `loads first page then appends more`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var calls = 0
        val vm = ArtistViewModel(
            pageLoader = { _, _, offset, _ ->
                calls++
                val count = if (offset == 0) 50 else 10
                Result.success(
                    ArtistSongsPage(
                        songs = (offset until offset + count).map { Song(id = it.toLong(), name = "s$it") },
                        total = 60
                    )
                )
            },
            artistId = 42L,
            artistName = "测试歌手"
        )

        runCurrent()
        assertEquals("测试歌手", vm.state.value.artistName)
        assertEquals(50, vm.state.value.songs.size)
        assertTrue(vm.state.value.hasMore)
        assertTrue(vm.state.value.songs.none { it.id < 0 })

        vm.loadMore()
        runCurrent()

        assertEquals(60, vm.state.value.songs.size)
        assertEquals(60, vm.state.value.total)
        assertFalse(vm.state.value.hasMore)
        assertEquals(2, calls)
        Dispatchers.resetMain()
    }

    @Test
    fun `order change reloads from the first page`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val offsets = mutableListOf<Int>()
        val vm = ArtistViewModel(
            pageLoader = { _, _, offset, order ->
                offsets += offset
                Result.success(
                    ArtistSongsPage(
                        songs = listOf(Song(id = offset.toLong(), name = order)),
                        total = 1
                    )
                )
            },
            artistId = 7L,
            artistName = "林俊杰"
        )

        runCurrent()
        assertEquals(0, offsets.single())
        assertEquals("hot", vm.state.value.order)

        vm.setOrder("time")
        runCurrent()

        assertEquals(listOf(0, 0), offsets)
        assertEquals("time", vm.state.value.order)
        Dispatchers.resetMain()
    }
}
