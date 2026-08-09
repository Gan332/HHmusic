package com.hh.music.player.data.local

import com.hh.music.player.data.Album
import com.hh.music.player.data.Artist
import com.hh.music.player.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMusicTest {

    private fun song(id: Long, uri: String?): Song =
        Song(
            id = id,
            name = "song$id",
            artists = listOf(Artist(name = "artist")),
            album = Album(name = "album"),
            duration = 120_000L,
            localUri = uri
        )

    @Test
    fun `uriId is stable and negative`() {
        assertTrue(LocalMusic.uriId("content://doc/1") < 0)
        assertEquals(
            LocalMusic.uriId("content://doc/1"),
            LocalMusic.uriId("content://doc/1")
        )
        assertNotEquals(
            LocalMusic.uriId("content://doc/1"),
            LocalMusic.uriId("content://doc/2")
        )
    }

    @Test
    fun `uriId always falls into the reserved SAF range`() {
        // SAF ids live in (-(2^40 + 2^31), -2^40); MediaStore uses -_id (approx. [-2^30, 0)).
        val lo = -(1L shl 40) - (1L shl 31)
        val hi = -(1L shl 40) + 1
        repeat(1000) { i ->
            val id = LocalMusic.uriId("content://mt/device$i/primary/music/track$i.mp3")
            assertTrue("id=$id escaped reserved range", id > lo && id < hi)
        }
    }

    @Test
    fun `merge keeps order, drops dead entries and dedupes by uri`() {
        val scanned = listOf(song(1L, "content://a"), song(2L, "content://b"))
        val imported = listOf(song(3L, "content://b"), null, song(4L, "content://c"))
        val out = LocalMusic.merge(scanned, imported)
        assertEquals(listOf("a", "b", "c"), out.map { it.localUri?.substringAfterLast('/') })
    }

    @Test
    fun `merge falls back to id key when a scan uri is missing`() {
        val out = LocalMusic.merge(listOf(song(1L, null), song(2L, null)), emptyList())
        assertEquals(2, out.size)
    }

    @Test
    fun `imported entry without a uri is dropped silently`() {
        assertTrue(LocalMusic.merge(emptyList(), listOf(song(9L, null))).isEmpty())
    }
}