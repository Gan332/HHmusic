package com.hh.music.player.playback

import com.hh.music.player.data.Song
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackEngineTest {

    // ---------------- auto-skip policy ----------------

    @Test
    fun `auto skip allowed while there are other items and failures are bounded`() {
        assertTrue(PlaybackEngine.shouldAutoSkip(consecutiveFailures = 1, mediaItemCount = 3))
        assertTrue(PlaybackEngine.shouldAutoSkip(consecutiveFailures = PlaybackEngine.MAX_CONSECUTIVE_AUTO_SKIPS, mediaItemCount = 3))
    }

    @Test
    fun `auto skip stops after max consecutive failures`() {
        val over =
            PlaybackEngine.MAX_CONSECUTIVE_AUTO_SKIPS + 1
        assertFalse(PlaybackEngine.shouldAutoSkip(over, mediaItemCount = 3))
    }

    @Test
    fun `auto skip never fires with a single item`() {
        assertFalse(PlaybackEngine.shouldAutoSkip(1, mediaItemCount = 1))
        assertFalse(PlaybackEngine.shouldAutoSkip(PlaybackEngine.MAX_CONSECUTIVE_AUTO_SKIPS, mediaItemCount = 1))
    }

    // ---------------- prefetch index by play mode ----------------

    @Test
    fun `sequence prefetches the next index and none at the tail`() {
        assertEquals(1, PlaybackEngine.nextPrefetchIndex(queueSize = 5, currentIndex = 0, mode = PlayMode.SEQUENCE))
        assertEquals(4, PlaybackEngine.nextPrefetchIndex(queueSize = 5, currentIndex = 3, mode = PlayMode.SEQUENCE))
        assertNull(PlaybackEngine.nextPrefetchIndex(queueSize = 5, currentIndex = 4, mode = PlayMode.SEQUENCE))
    }

    @Test
    fun `repeat one never prefetches`() {
        assertNull(PlaybackEngine.nextPrefetchIndex(queueSize = 5, currentIndex = 2, mode = PlayMode.REPEAT_ONE))
    }

    @Test
    fun `shuffle prefetches a random index that is neither current nor already resolved`() {
        val rng = Random(7)
        for (i in 1..50) {
            val idx = PlaybackEngine.nextPrefetchIndex(
                queueSize = 5,
                currentIndex = 2,
                mode = PlayMode.SHUFFLE,
                resolvedIndices = setOf(2),
                random = rng
            )
            assertTrue("got $idx", idx != null && idx != 2 && idx in 0..4)
        }
    }

    @Test
    fun `shuffle skips resolved tracks until all are cached`() {
        val rng = Random(3)
        val resolved = setOf(1, 3, 4)
        for (attempt in 1..100) { // never picks a resolved index
            val idx = PlaybackEngine.nextPrefetchIndex(
                queueSize = 5,
                currentIndex = 0,
                mode = PlayMode.SHUFFLE,
                resolvedIndices = resolved,
                random = rng
            )
            assertEquals(2, idx)
        }
        // Everything resolved -> nothing left to prefetch (no duplicate re-resolves).
        val all = (0 until 5).toSet()
        assertNull(
            PlaybackEngine.nextPrefetchIndex(5, 0, PlayMode.SHUFFLE, resolvedIndices = all, random = rng)
        )
    }

    @Test
    fun `prefetch requires at least two items`() {
        assertNull(PlaybackEngine.nextPrefetchIndex(queueSize = 1, currentIndex = 0, mode = PlayMode.SEQUENCE))
    }

    // ---------------- queue index bookkeeping ----------------

    @Test
    fun `removing a track before the current one shifts it down`() {
        assertEquals(1, PlaybackEngine.indexAfterRemove(currentIndex = 2, removedIndex = 0, newSize = 4))
    }

    @Test
    fun `removing the current track picks the next item`() {
        assertEquals(2, PlaybackEngine.indexAfterRemove(currentIndex = 2, removedIndex = 2, newSize = 4))
        // Last item removed while it plays -> clamp to the new tail.
        assertEquals(2, PlaybackEngine.indexAfterRemove(currentIndex = 3, removedIndex = 3, newSize = 3))
        // Emptying the queue -> -1.
        assertEquals(-1, PlaybackEngine.indexAfterRemove(currentIndex = 0, removedIndex = 0, newSize = 0))
    }

    @Test
    fun `removing after the current track keeps the index`() {
        assertEquals(2, PlaybackEngine.indexAfterRemove(currentIndex = 2, removedIndex = 3, newSize = 4))
    }

    @Test
    fun `moving updates the current index consistently`() {
        assertEquals(1, PlaybackEngine.indexAfterMove(currentIndex = 4, from = 4, to = 1))
        assertEquals(1, PlaybackEngine.indexAfterMove(currentIndex = 0, from = 2, to = 0))
        assertEquals(2, PlaybackEngine.indexAfterMove(currentIndex = 3, from = 1, to = 3))
        assertEquals(3, PlaybackEngine.indexAfterMove(currentIndex = 3, from = 1, to = 2))
        assertEquals(2, PlaybackEngine.indexAfterMove(currentIndex = 3, from = 1, to = 5))
    }

    @Test
    fun `song index lookup is a plain find`() {
        val ids = listOf(10L, 20L, 30L)
        assertEquals(1, PlaybackEngine.indexOfSong(ids, 20L))
        assertEquals(-1, PlaybackEngine.indexOfSong(ids, 99L))
    }

    @Test
    fun `queue stats sums valid durations for empty and single queues`() {
        assertEquals(0, PlaybackEngine.queueStats(emptyList()).count)
        assertEquals(0L, PlaybackEngine.queueStats(emptyList()).durationMs)

        val single = PlaybackEngine.queueStats(listOf(Song(id = 1, name = "a", duration = 60_000L)))
        assertEquals(1, single.count)
        assertEquals(60_000L, single.durationMs)
    }

    @Test
    fun `queue stats counts every song and ignores negative durations`() {
        val queue = listOf(
            Song(id = 1, name = "晴天", duration = 4_29000L),
            Song(id = 2, name = "七里香", duration = -1L),
            Song(id = 3, name = "夜曲", duration = 3_60000L)
        )
        val stats = PlaybackEngine.queueStats(queue)
        assertEquals(3, stats.count)
        assertEquals(7_89000L, stats.durationMs)
    }

    @Test
    fun `moving a queue item to top or bottom yields the expected current index`() {
        assertEquals(0, PlaybackEngine.indexAfterMove(currentIndex = 4, from = 4, to = 0))
        assertEquals(1, PlaybackEngine.indexAfterMove(currentIndex = 0, from = 2, to = 0))
        assertEquals(0, PlaybackEngine.indexAfterMove(currentIndex = 0, from = 0, to = 0))

        assertEquals(4, PlaybackEngine.indexAfterMove(currentIndex = 0, from = 0, to = 4))
        assertEquals(2, PlaybackEngine.indexAfterMove(currentIndex = 3, from = 1, to = 4))
        assertEquals(5, PlaybackEngine.indexAfterMove(currentIndex = 5, from = 5, to = 5))
    }
}
