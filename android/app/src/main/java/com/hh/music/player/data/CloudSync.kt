package com.hh.music.player.data

import com.hh.music.player.data.local.LocalStore
import com.hh.music.player.network.AccountInfo
import com.hh.music.player.network.LoginClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Summary of one successful favorites reconciliation pass (v1.8). */
data class ReconcileResult(
    /** Cloud-liked songs merged into local favorites. */
    val cloudAddedLocally: Int,
    /** Local-only favorites pushed to the cloud. */
    val pushedToCloud: Int,
    /** Local-only pushes the cloud rejected (VIP/copyright/rate limits). */
    val cloudPushFailures: Int
)

/**
 * Best-effort bridge between local data and the NetEase cloud.
 *
 * A toggle is ALWAYS persisted locally first; the cloud push is fire-and-forget:
 * failures (offline, not logged in, backend mode) are swallowed so the UI never
 * waits on sync. Cloud state mirrors local intent; it is never the source of truth.
 */
class CloudSync(
    private val repository: MusicRepository,
    private val store: LocalStore,
    private val scope: CoroutineScope
) {
    fun pushLike(songId: Long, like: Boolean) {
        if (!canSync()) return
        scope.launch {
            runCatching { repository.likeSong(songId, like) }
        }
    }

    /** v1.8: best-effort cloud playlist subscribe/unsubscribe after the local toggle. */
    fun pushPlaylistSubscribe(playlistId: Long, subscribe: Boolean) {
        if (!canSync()) return
        scope.launch {
            runCatching { repository.subscribePlaylist(playlistId, subscribe) }
        }
    }

    /** Verify the stored MUSIC_U token is still valid; returns the profile or null. */
    suspend fun refreshAccount(): AccountInfo? {
        if (!canSync()) return null
        return runCatching { LoginClient.fetchAccount(repository.ioDispatcher).getOrThrow() }.getOrNull()
    }

    /**
     * One-shot bidirectional favorites alignment, run once per fresh login:
     *
     *  1. Find the user's immutable "我喜欢的音乐" liked-songs playlist.
     *  2. Merge cloud-liked songs missing locally into local favorites.
     *  3. Push local-only favorites to the cloud (capped, failures tolerated).
     *
     * Returns null when there was nothing/nowhere to sync or anything failed —
     * callers treat reconciliation as purely opportunistic.
     */
    suspend fun reconcileFavorites(userId: Long): ReconcileResult? {
        if (!canSync() || userId <= 0) return null
        return runCatching {
            val mine = repository.userPlaylists(userId).getOrThrow()
            val liked = mine.firstOrNull { it.isLikedSongs } ?: return null

            val cloudSongs = repository.playlistDetail(liked.id).getOrThrow().tracks
            val localSongs = store.favorites.first()

            // Pure, JVM-tested decision: what to merge locally and what to push.
            val plan = planReconciliation(cloudSongs, localFavorites = localSongs)
            if (plan.missingLocally.isNotEmpty()) store.addFavorites(plan.missingLocally)

            var pushed = 0
            var failures = 0
            for (song in plan.toPushToCloud) {
                val ok = runCatching { repository.likeSong(song.id, true) }.isSuccess
                if (ok) pushed++ else failures++
            }

            ReconcileResult(
                cloudAddedLocally = plan.missingLocally.size,
                pushedToCloud = pushed,
                cloudPushFailures = failures
            )
        }.getOrNull()
    }

    private fun canSync(): Boolean = repository.hasLoginCookie && !repository.useBackend

    companion object {
        /** Safety cap so one login can never hammer the like endpoint unbounded. */
        const val MAX_CLOUD_PUSHES_PER_LOGIN = 100

        /**
         * Pure reconciliation decision between the cloud liked-songs list and
         * local favorites:
         *
         * - [cloudLiked] songs absent locally become [ReconcilePlan.missingLocally].
         * - Local songs the cloud doesn't know become [ReconcilePlan.toPushToCloud]:
         *   only positive ids qualify (local imports/SAF use synthetic negative
         *   ids), capped at [maxPushes] to bound a single login's like traffic.
         */
        fun planReconciliation(
            cloudLiked: List<Song>,
            localFavorites: List<Song>,
            maxPushes: Int = MAX_CLOUD_PUSHES_PER_LOGIN
        ): ReconcilePlan {
            val cloudIds = cloudLiked.mapTo(HashSet()) { it.id }
            val localIds = localFavorites.mapTo(HashSet()) { it.id }
            val missingLocally = cloudLiked.filter { it.id !in localIds }
            val toPush = localFavorites.asSequence()
                .filter { it.id !in cloudIds && it.id > 0 }
                .take(maxPushes.coerceAtLeast(0))
                .toList()
            return ReconcilePlan(missingLocally, toPush)
        }
    }
}

/** What one reconciliation pass intends to do; pure output of [CloudSync.planReconciliation]. */
data class ReconcilePlan(
    val missingLocally: List<Song>,
    val toPushToCloud: List<Song>
)
