package com.hh.music.player.data.local

import com.hh.music.player.data.Album
import com.hh.music.player.data.Song
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueCodecTest {

    private fun song(id: Long) = Song(
        id = id,
        name = "s$id",
        artists = emptyList(),
        album = Album(id = 0, name = "a$id", picUrl = "http://x/$id.jpg"),
        duration = 200_000L,
        fee = if (id % 2 == 0L) 1 else 0
    )

    @Test
    fun `empty queue encodes to null and decodes to null`() {
        assertNull(QueueCodec.encode(emptyList(), 0, 42L))
        assertNull(QueueCodec.decode(null))
        assertNull(QueueCodec.decode(""))
        assertNull(QueueCodec.decode("   "))
    }

    @Test
    fun `round trip preserves songs index and position`() {
        val songs = (0L..4L).map { id -> song(id) }
        val raw = QueueCodec.encode(songs, index = 3, positionMs = 123_456L)
        val back = QueueCodec.decode(raw)
        assertEquals(5, back!!.songs.size)
        assertEquals(3, back.index)
        assertEquals(123_456L, back.positionMs)
        // Serialization round-trip keeps the song data (cover, fee) intact.
        assertEquals(songs[3], back.songs[3])
    }

    @Test
    fun `out-of-range index is clamped`() {
        val raw = QueueCodec.encode(listOf(song(1), song(2)), index = 99)
        val back = QueueCodec.decode(raw)
        assertEquals(1, back!!.index)
    }

    @Test
    fun `negative position is normalized to zero`() {
        val raw = QueueCodec.encode(listOf(song(1)), index = 0, positionMs = -5L)
        assertEquals(0L, QueueCodec.decode(raw)!!.positionMs)
    }

    @Test
    fun `huge queues are capped to MAX_QUEUED`() {
        val many = (0 until QueueCodec.MAX_QUEUED * 2).map { id -> song(id) }
        val raw = QueueCodec.encode(many, index = QueueCodec.MAX_QUEUED * 2 - 1)
        val back = QueueCodec.decode(raw)!!
        assertEquals(QueueCodec.MAX_QUEUED, back.songs.size)
        // Index clamped into the capped list rather than pointing past it.
        assertTrue(back.index in back.songs.indices)
    }

    @Test
    fun `older format without positionMs still decodes`() {
        // Pre-positionMs payload: only songs + index.
        val legacy =
            """{"songs":[{"id":7,"name":"s7","artists":[],"album":{"id":0,"name":"a","picUrl":null},"duration":200000,"fee":0}],"index":0}"""
        val back = QueueCodec.decode(legacy)!!
        assertEquals(7L, back.songs[0].id)
        assertEquals(0L, back.positionMs)
    }

    @Test
    fun `unknown json keys are tolerated`() {
        val raw = """{"songs":[],"index":-1,"positionMs":5,"futureField":"x"}"""
        assertEquals(5L, QueueCodec.decode(raw)!!.positionMs)
    }
}
