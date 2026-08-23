package com.hh.music.player.data

import com.hh.music.player.network.AccountInfo
import com.hh.music.player.network.LoginClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Best-effort bridge between local favorites and the NetEase cloud.
 *
 * A toggle is ALWAYS persisted locally first; the cloud push is fire-and-forget:
 * failures (offline, not logged in, backend mode) are swallowed so the UI never
 * waits on sync. Cloud state mirrors local intent; it is never the source of truth.
 */
class CloudSync(
    private val repository: MusicRepository,
    private val scope: CoroutineScope
) {
    fun pushLike(songId: Long, like: Boolean) {
        if (!repository.hasLoginCookie || repository.useBackend) return
        scope.launch {
            runCatching { repository.likeSong(songId, like) }
        }
    }

    /** Verify the stored MUSIC_U token is still valid; returns the profile or null. */
    suspend fun refreshAccount(): AccountInfo? {
        if (!repository.hasLoginCookie || repository.useBackend) return null
        return runCatching { LoginClient.fetchAccount(repository.ioDispatcher) }.getOrNull()
    }
}
