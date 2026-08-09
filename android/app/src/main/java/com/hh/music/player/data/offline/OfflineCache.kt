package com.hh.music.player.data.offline

import com.hh.music.player.data.Song
import kotlinx.serialization.Serializable

/** Lifecycle of one download (drives the progress UI). */
enum class DownloadState { IDLE, DOWNLOADING, DONE, ERROR }

/** Live per-song download progress/result. */
data class DownloadStatus(
    val state: DownloadState = DownloadState.IDLE,
    /** 0..100 while [DownloadState.DOWNLOADING]; 100 when done. */
    val progress: Int = 0,
    /** Failure reason for [DownloadState.ERROR] (VIP / copyright / network). */
    val error: String? = null
)

/**
 * One persisted download record. A row is either a finished file
 * ([error] == null) or a failed download marker ([error] != null, size 0)
 * kept so the UI can surface the reason and offer a retry.
 */
@Serializable
data class DownloadEntry(
    val song: Song,
    val fileName: String = "",
    val sizeBytes: Long = 0L,
    val downloadedAt: Long = 0L,
    val lastPlayedAt: Long = 0L,
    val error: String? = null
) {
    val id: Long get() = song.id
    val isFailed: Boolean get() = error != null
}

/**
 * Pure, Android-free cache bookkeeping: capacity math, LRU eviction selection
 * (oldest-played first, never the currently playing track) and file naming.
 * Kept as plain functions so the whole policy is unit-testable on the JVM.
 */
object OfflineCache {

    /** Default auto-cache cap (1 GB). */
    const val DEFAULT_CAP_MB = 1024L

    /** The typical file extension used when the URL carries no type hint. */
    const val DEFAULT_EXT = "mp3"

    fun capBytes(capMb: Long): Long = capMb.coerceAtLeast(0L) * 1024L * 1024L

    fun totalBytes(entries: List<DownloadEntry>): Long =
        entries.sumOf { it.sizeBytes.coerceAtLeast(0L) }

    /**
     * Entries that must be deleted so that adding [newSizeBytes] keeps the total
     * within [capBytes]. Candidates are ordered oldest-lastPlayedAt first and
     * [protectedIds] (currently playing track) are never chosen.
     */
    fun evictionCandidates(
        entries: List<DownloadEntry>,
        newSizeBytes: Long,
        capBytes: Long,
        protectedIds: Set<Long> = emptySet()
    ): List<DownloadEntry> {
        val over = totalBytes(entries) + newSizeBytes.coerceAtLeast(0L) - capBytes
        if (over <= 0L) return emptyList()
        val candidates = entries
            .filter { it.id !in protectedIds }
            .sortedBy { it.lastPlayedAt } // oldest first
        val picked = mutableListOf<DownloadEntry>()
        var freed = 0L
        for (e in candidates) {
            if (freed >= over) break
            picked += e
            freed += e.sizeBytes.coerceAtLeast(0L)
        }
        return picked
    }

    /**
     * Whether evicting every non-protected entry would make room for [newSizeBytes].
     * When false the cache cap simply cannot hold the file (set the cap or clear).
     */
    fun canFitAfterEviction(
        entries: List<DownloadEntry>,
        newSizeBytes: Long,
        capBytes: Long,
        protectedIds: Set<Long> = emptySet()
    ): Boolean {
        val evictable = entries
            .filter { it.id !in protectedIds }
            .sumOf { it.sizeBytes.coerceAtLeast(0L) }
        if (evictable <= 0L) return false
        return totalBytes(entries) - evictable + newSizeBytes.coerceAtLeast(0L) <= capBytes
    }

    /** 1034 B → "1.0 KB", 2_621_440 → "2.5 MB", etc. */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        return when {
            kb >= 1024.0 * 1024.0 -> "${trimDecimal(kb / (1024.0 * 1024.0))} GB"
            kb >= 1024.0 -> "${trimDecimal(kb / 1024.0)} MB"
            else -> "${trimDecimal(kb)} KB"
        }
    }

    fun fileName(id: Long, ext: String?): String =
        "$id.${ext?.takeIf { it.isNotBlank() } ?: DEFAULT_EXT}"

    private fun trimDecimal(value: Double): String {
        val rounded = (value * 10).toLong() / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
        else "%.1f".format(rounded)
    }
}
