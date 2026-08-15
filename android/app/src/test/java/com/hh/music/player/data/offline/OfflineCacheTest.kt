package com.hh.music.player.data.offline

import com.hh.music.player.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCacheTest {
    private fun entry(id: Long, size: Long, played: Long = id): DownloadEntry =
        DownloadEntry(
            song = Song(id = id, name = "song-$id"),
            fileName = OfflineCache.fileName(id, "mp3"),
            sizeBytes = size,
            lastPlayedAt = played
        )

    @Test
    fun `capacity and total bytes clamp negative values`() {
        assertEquals(0L, OfflineCache.capBytes(-1))
        assertEquals(10L, OfflineCache.totalBytes(listOf(entry(1, 10), entry(2, -5))))
        assertEquals("0 B", OfflineCache.formatBytes(-1))
    }

    @Test
    fun `eviction picks oldest entries and protects current track`() {
        val entries = listOf(entry(1, 40, 30), entry(2, 40, 10), entry(3, 40, 20))
        val picked = OfflineCache.evictionCandidates(
            entries = entries,
            newSizeBytes = 10,
            capBytes = 100,
            protectedIds = setOf(2)
        )
        assertEquals(listOf(3L), picked.map { it.id })
    }

    @Test
    fun `eviction reports when protected files prevent fitting`() {
        val entries = listOf(entry(1, 100))
        assertFalse(OfflineCache.canFitAfterEviction(entries, newSizeBytes = 50, capBytes = 100, protectedIds = setOf(1)))
        assertTrue(OfflineCache.canFitAfterEviction(entries, newSizeBytes = 50, capBytes = 100))
    }

    @Test
    fun `file names and human readable sizes are stable`() {
        assertEquals("42.mp3", OfflineCache.fileName(42, null))
        assertEquals("42.flac", OfflineCache.fileName(42, "flac"))
        assertEquals("1 KB", OfflineCache.formatBytes(1024))
        assertEquals("1.5 MB", OfflineCache.formatBytes(1572864))
    }
}
