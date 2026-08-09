package com.hh.music.player.playback

import com.hh.music.player.data.Song
import kotlin.random.Random

/** Aggregate queue metadata used by the UI and pure-logic tests. */
data class QueueStats(
    val count: Int = 0,
    val durationMs: Long = 0L
)

/**
 * Pure, Android-free playback decision logic. Kept separate from
 * [PlayerController] so it can be unit-tested on the JVM without a device:
 * auto-skip policy, per-mode next/prefetch index selection (shuffle dedupe),
 * and queue index bookkeeping after remove/move.
 */
object PlaybackEngine {

    /** Same song failing this many consecutive times stops auto-advancing. */
    const val MAX_CONSECUTIVE_AUTO_SKIPS = 2

    /**
     * Auto-skip decision: only advance automatically while there is another
     * item AND the same song hasn't already failed and been skipped too many
     * times in a row (otherwise we'd thrash between two broken tracks).
     */
    fun shouldAutoSkip(consecutiveFailures: Int, mediaItemCount: Int): Boolean =
        mediaItemCount > 1 && consecutiveFailures <= MAX_CONSECUTIVE_AUTO_SKIPS

    /**
     * Index to pre-resolve ahead of the current one, or null when there is
     * nothing useful to prefetch.
     *  - SEQUENCE: the immediate next index.
     *  - REPEAT_ONE: none (the same track is replayed).
     *  - SHUFFLE: a random index that isn't the current one and hasn't already
     *    been resolved ([resolvedIndices]) — avoids re-resolving the same song
     *    repeatedly while others are still uncached.
     */
    fun nextPrefetchIndex(
        queueSize: Int,
        currentIndex: Int,
        mode: PlayMode,
        resolvedIndices: Set<Int> = emptySet(),
        random: Random = Random.Default
    ): Int? {
        if (queueSize < 2) return null
        return when (mode) {
            PlayMode.SEQUENCE -> (currentIndex + 1).takeIf { it < queueSize }
            PlayMode.REPEAT_ONE -> null
            PlayMode.SHUFFLE -> {
                val candidates = (0 until queueSize)
                    .filter { it != currentIndex && it !in resolvedIndices }
                candidates.randomOrNull(random)
            }
        }
    }

    /**
     * Maintain the "current" index after removing [removedIndex] (used by
     * remove-from-queue without MediaController feedback races). Mirror of
     * ExoPlayer's behaviour: removal before/at the current index shifts it.
     */
    fun indexAfterRemove(currentIndex: Int, removedIndex: Int, newSize: Int): Int {
        if (currentIndex < 0) return currentIndex
        val updatedSize = newSize.coerceAtLeast(1)
        // ExoPlayer clamps the current index to the last remaining item (or -1).
        return if (removedIndex == currentIndex) {
            currentIndex.coerceAtMost(updatedSize - 1)
        } else if (removedIndex < currentIndex) {
            currentIndex - 1
        } else {
            currentIndex
        }
    }

    /** Maintain the current index after moving a queue item from from to to. */
    fun indexAfterMove(currentIndex: Int, from: Int, to: Int): Int = when {
        currentIndex == from -> to
        from < currentIndex && to >= currentIndex -> currentIndex - 1
        from > currentIndex && to <= currentIndex -> currentIndex + 1
        else -> currentIndex
    }

    /** Total queue size and summed track durations, ignoring negative durations. */
    fun queueStats(queue: List<Song>): QueueStats =
        QueueStats(
            count = queue.size,
            durationMs = queue.fold(0L) { acc, song -> acc + song.duration.coerceAtLeast(0L) }
        )

    /**
     * Live index of [songId] in the queue, used to sanity-check "the current
     * song still exists" before resolving/playing it (stale persisted queues).
     */
    fun indexOfSong(songs: List<Long>, songId: Long): Int = songs.indexOf(songId)
}
