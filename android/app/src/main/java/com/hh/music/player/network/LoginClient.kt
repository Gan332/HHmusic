package com.hh.music.player.network

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Minimal profile cached after a successful login. */
data class AccountInfo(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String = ""
)

/** QR login lifecycle as surfaced by the poll endpoint. */
sealed interface QrPollStatus {
    /** Nobody has scanned yet (code 801). */
    data object WaitingForScan : QrPollStatus

    /** Scanned on the phone, waiting for the user to confirm (code 802). */
    data object WaitingForConfirm : QrPollStatus

    /** Authorized — Set-Cookie carries MUSIC_U (code 803). */
    data class Success(val musicU: String) : QrPollStatus

    /** QR expired; caller must regenerate the key (code 800). */
    data object Expired : QrPollStatus
}

/**
 * Direct NetEase account calls: anonymous QR-key creation, poll, profile fetch,
 * and cloud like/unlike. All plain /api form POSTs mirroring the rest of
 * [DirectNcmClient]; the session rides on the shared MUSIC_U cookie.
 *
 * Pure parsing helpers ([extractMusicU], [parseAccount], [parsePollCode]) live
 * here so they can be unit-tested on the JVM.
 */
object LoginClient {

    const val POLL_EXPIRED = 800
    const val POLL_WAITING_SCAN = 801
    const val POLL_WAITING_CONFIRM = 802
    const val POLL_SUCCESS = 803

    /** Content encoded into the QR image — the official web scan target. */
    fun qrContent(unikey: String): String = "https://music.163.com/login?codekey=$unikey"

    /**
     * Pull the MUSIC_U token out of raw Set-Cookie header values. Returns null
     * when no usable token is present.
     */
    fun extractMusicU(setCookieHeaders: List<String>): String? =
        setCookieHeaders.asSequence()
            .map { it.substringBefore(';').trim() }
            .filter { it.uppercase().startsWith("MUSIC_U=") }
            .map { it.substringAfter('=') }
            .firstOrNull { it.isNotBlank() }

    /** Defensive parse of /api/nuser/account/get → [AccountInfo] or null when logged out. */
    fun parseAccount(body: String): AccountInfo? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (root.optInt("code", -1) != 200) return null
        val profile = root.optJSONObject("profile") ?: return null
        val id = profile.optLong("userId", 0)
        if (id <= 0) return null
        return AccountInfo(
            userId = id,
            nickname = profile.optString("nickname", ""),
            avatarUrl = profile.optString("avatarUrl", "")
        )
    }

    /** Map a poll response body (+ its Set-Cookie headers) to a [QrPollStatus]. */
    fun parsePollStatus(body: String, setCookies: List<String>): QrPollStatus {
        val code = runCatching { JSONObject(body).optInt("code", -1) }.getOrDefault(-1)
        return when (code) {
            POLL_SUCCESS -> QrPollStatus.Success(extractMusicU(setCookies) ?: "")
            POLL_WAITING_CONFIRM -> QrPollStatus.WaitingForConfirm
            POLL_EXPIRED -> QrPollStatus.Expired
            else -> QrPollStatus.WaitingForScan
        }
    }

    suspend fun createQrKey(ioDispatcher: CoroutineDispatcher): Result<String> =
        withContext(ioDispatcher) {
            runCatching {
                val body = DirectNcmClient.apiPost(
                    "login/qrcode/unikey",
                    mapOf("type" to "1")
                )
                val json = JSONObject(body)
                val key = json.optString("unikey", "").ifBlank {
                    json.optJSONObject("data")?.optString("unikey", "").orEmpty()
                }
                if (key.isBlank()) throw Exception("获取登录二维码失败")
                key
            }
        }

    suspend fun pollQr(key: String, ioDispatcher: CoroutineDispatcher): Result<QrPollStatus> =
        withContext(ioDispatcher) {
            runCatching {
                val resp = DirectNcmClient.apiPostWithResponse(
                    "login/qrcode/client/login",
                    mapOf("key" to key, "type" to "1")
                )
                parsePollStatus(resp.body, resp.setCookies)
            }
        }

    suspend fun fetchAccount(ioDispatcher: CoroutineDispatcher): Result<AccountInfo> =
        withContext(ioDispatcher) {
            runCatching {
                val body = DirectNcmClient.apiPost("nuser/account/get", emptyMap())
                parseAccount(body) ?: throw Exception("未登录或登录已过期")
            }
        }

    suspend fun like(songId: Long, like: Boolean, ioDispatcher: CoroutineDispatcher): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val body = DirectNcmClient.apiPost(
                    "song/like",
                    mapOf("trackId" to songId.toString(), "like" to like.toString())
                )
                val code = JSONObject(body).optInt("code", -1)
                if (code != 200) throw Exception("云端收藏失败 (code=$code)")
            }
        }
}
