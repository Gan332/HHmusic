package com.hh.music.player.data.local

import com.hh.music.player.data.SavedQueue
import com.hh.music.player.data.Song
import kotlinx.serialization.json.Json

/**
 * Serialization + hygiene rules for the persisted playback queue. Kept free of
 * Android/DataStore so the cap logic and codec round-trips are unit-testable on
 * the JVM; [LocalStore] only stores/restores the string it produces.
 */
object QueueCodec {

    /** Upper bound for persisted songs — keeps the DataStore blob from growing forever. */
    const val MAX_QUEUED = 300

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Encode a queue for storage, or null when it is empty (nothing to persist). */
    fun encode(songs: List<Song>, index: Int, positionMs: Long = 0L): String? {
        if (songs.isEmpty()) return null
        val capped = songs.take(MAX_QUEUED)
        val saved = SavedQueue(
            songs = capped,
            index = index.coerceIn(0, capped.lastIndex),
            positionMs = positionMs.coerceAtLeast(0L)
        )
        return json.encodeToString(SavedQueue.serializer(), saved)
    }

    /** Decode a persisted queue; a corrupt/unknown blob yields null (not a crash). */
    fun decode(raw: String?): SavedQueue? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(SavedQueue.serializer(), raw) }.getOrNull()
    }
}