package com.hh.music.player.data.offline

import android.content.Context
import com.hh.music.player.data.MusicRepository
import com.hh.music.player.data.Song
import com.hh.music.player.data.local.LocalStore
import com.hh.music.player.network.DirectNcmClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Lightweight offline cache: streams playable URLs (already resolved by the
 * repository / cookie context) into `getExternalFilesDir(null)/downloads/<id>.<ext>`
 * — no storage permission needed. Keeps an LRU eviction policy with a hard cap
 * (default 1 GB) via [OfflineCache]; the currently playing track is never evicted.
 *
 * - [download] resolves the URL itself; [maybeAutoCache] piggybacks on an
 *   already-resolved URL from playback (no duplicate network call).
 * - Failures (VIP / copyright / network) are kept as failed entries with the
 *   reason attached; there is deliberately NO automatic retry storm.
 */
class DownloadManager(
    context: Context,
    private val repository: MusicRepository,
    private val local: LocalStore
) {
    private val appContext = context.applicationContext
    private val downloadsDir = File(appContext.getExternalFilesDir(null), "downloads")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _entries = MutableStateFlow<List<DownloadEntry>>(emptyList())
    /** Persisted download records: finished files + failed markers (error != null). */
    val entries: StateFlow<List<DownloadEntry>> = _entries.asStateFlow()

    private val _statuses = MutableStateFlow<Map<Long, DownloadStatus>>(emptyMap())
    /** Live download progress per song id (in-memory; not persisted). */
    val statuses: StateFlow<Map<Long, DownloadStatus>> = _statuses.asStateFlow()

    /** Played ids that must never be evicted (fed by PlayerController). */
    @Volatile
    private var playingIds: Set<Long> = emptySet()

    /** Auto-cache toggle, kept from LocalStore by PlayerController-agnostic collect below. */
    @Volatile
    private var autoCacheEnabled: Boolean = true

    @Volatile
    private var capMb: Int = OfflineCache.DEFAULT_CAP_MB.toInt()

    val autoCache: Boolean get() = autoCacheEnabled
    val capMbValue: Int get() = capMb.toInt()

    init {
        downloadsDir.mkdirs()
        scope.launch {
            local.autoCache.collect { autoCacheEnabled = it }
        }
        scope.launch {
            local.cacheCapMb.collect { capMb = it.coerceAtLeast(0) }
        }
        scope.launch {
            local.downloads.collect { list ->
                // Drop entries whose file disappeared (cleared/corrupt); keep failed markers.
                val clean = list.filter { e -> e.isFailed || fileFor(e.id) != null }
                _entries.value = clean
                if (clean != list) local.setDownloads(clean)
            }
        }
    }

    fun markPlaying(ids: Set<Long>) {
        playingIds = ids
    }

    /** Refresh the LRU timestamp when a downloaded song starts playing. */
    fun touchPlayed(songId: Long) {
        scope.launch {
            val cur = _entries.value
            val idx = cur.indexOfFirst { it.id == songId && !it.isFailed }
            if (idx < 0) return@launch
            val updated = cur.toMutableList().also { it[idx] = it[idx].copy(lastPlayedAt = System.currentTimeMillis()) }
            _entries.value = updated
            local.setDownloads(updated)
        }
    }

    /** The on-disk file for [songId] across any extension, or null when not cached. */
    fun fileFor(songId: Long): File? {
        val dir = downloadsDir
        if (!dir.isDirectory) return null
        return dir.listFiles { f -> f.isFile && f.name.startsWith("$songId.") }?.firstOrNull()
    }

    /** Start (or restart) a download for [song]. Resolves the URL itself. */
    fun download(song: Song) {
        if (song.isLocal) return
        scope.launch {
            if (fileFor(song.id) != null) return@launch
            if (_statuses.value[song.id]?.state == DownloadState.DOWNLOADING) return@launch
            val result = runCatching {
                val urlInfo = repository.songUrl(song.id).getOrThrow()
                val url = urlInfo.url
                require(!url.isNullOrBlank()) { "无法获取播放地址（可能为会员或版权受限）" }
                downloadToFile(song, url, urlInfo.type)
            }
            result.fold(
                onSuccess = { entry ->
                    _statuses.value = _statuses.value + (song.id to DownloadStatus(DownloadState.DONE, 100))
                    _entries.value = (_entries.value.filter { it.id != song.id } + entry)
                            .sortedByDescending { it.downloadedAt }
                    local.setDownloads(_entries.value)
                    evictIf()
                },
                onFailure = { e ->
                    File(downloadsDir, "tmp_${song.id}").delete() // drop a half-written file
                    val reason = e.message?.takeIf { it.isNotBlank() } ?: "下载失败"
                    _statuses.value = _statuses.value + (song.id to DownloadStatus(DownloadState.ERROR, 0, reason))
                    val failed = DownloadEntry(song = song, error = reason)
                    _entries.value = _entries.value.filter { it.id != song.id } + failed
                    local.setDownloads(_entries.value)
                }
            )
        }
    }

    /**
     * Auto-cache hook: called right after a track's URL was resolved for playback.
     * Skips local files, disabled auto-cache, already-downloaded and in-flight tracks.
     */
    fun maybeAutoCache(song: Song, resolvedUrl: String?) {
        if (!autoCacheEnabled) return
        if (song.isLocal) return
        if (resolvedUrl.isNullOrBlank()) return
        scope.launch {
            if (fileFor(song.id) != null) return@launch
            if (_statuses.value[song.id]?.state == DownloadState.DOWNLOADING) return@launch
            val result = runCatching {
                downloadToFile(song, resolvedUrl, extractFromUrl(resolvedUrl))
            }
            result.fold(
                onSuccess = { entry ->
                    _statuses.value = _statuses.value + (song.id to DownloadStatus(DownloadState.DONE, 100))
                    _entries.value = (_entries.value.filter { it.id != song.id } + entry)
                            .sortedByDescending { it.downloadedAt }
                    local.setDownloads(_entries.value)
                    evictIf()
                },
                onFailure = {
                    // auto-cache failures stay silent (VIP/copyright) — no error entry
                    File(downloadsDir, "tmp_${song.id}").delete()
                }
            )
        }
    }

    /** Delete one download (file + record + status). */
    fun remove(songId: Long) {
        scope.launch {
            filesFor(songId).forEach { it.delete() }
            _entries.value = _entries.value.filter { it.id != songId }
            _statuses.value = _statuses.value - songId
            local.setDownloads(_entries.value)
        }
    }

    /** Delete every download and reset the store. */
    fun clear() {
        scope.launch {
            downloadsDir.listFiles()?.forEach { it.delete() }
            _entries.value = emptyList()
            _statuses.value = emptyMap()
            local.setDownloads(emptyList())
        }
    }

    /** Total on-disk bytes currently recorded (excludes failed markers). */
    fun totalBytes(): Long = OfflineCache.totalBytes(_entries.value.filter { !it.isFailed })

    fun formatBytes(): String = OfflineCache.formatBytes(totalBytes())

    private fun downloadToFile(song: Song, url: String, type: String?): DownloadEntry {
        val ext = when (type?.lowercase()) {
            "flac" -> "flac"
            "mp3", "m4a", "aac", "ogg" -> type!!.lowercase()
            else -> extractFromUrl(url)
        }
        val fileName = OfflineCache.fileName(song.id, ext)
        // "tmp_" prefix keeps in-flight files outside the "$id." lookup pattern so
        // fileFor() can never mistake a half-written download for a finished one.
        val tmp = File(downloadsDir, "tmp_${song.id}")
        tmp.delete()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Referer", "https://music.163.com/")
            .header("Cookie", DirectNcmClient.getCookie().orEmpty())
            .build()
        DirectNcmClient.client.newCall(request).execute().use { res ->
            if (!res.isSuccessful) throw Exception("HTTP ${res.code}")
            val body = res.body ?: throw Exception("响应为空")
            val total = body.contentLength()
            body.byteStream().use { input ->
                FileOutputStream(tmp).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        downloaded += n
                        if (total > 0) {
                            val pct = (downloaded * 100 / total).toInt().coerceIn(0, 99)
                            if (pct != lastPct) {
                                lastPct = pct
                                val st = _statuses.value[song.id]
                                if (st?.state == DownloadState.DOWNLOADING) {
                                    _statuses.value = _statuses.value + (song.id to st.copy(progress = pct))
                                }
                            }
                        }
                    }
                    if (total > 0 && downloaded < total) throw Exception("下载不完整")
                }
            }
        }
        val target = File(downloadsDir, fileName)
        target.delete()
        if (!tmp.renameTo(target)) throw Exception("保存文件失败")
        return DownloadEntry(
            song = song,
            fileName = fileName,
            sizeBytes = target.length(),
            downloadedAt = System.currentTimeMillis(),
            lastPlayedAt = System.currentTimeMillis()
        )
    }

    /**
     * LRU eviction after a new download landed ([_entries] already includes it).
     * Never evicts the currently playing song ([playingIds]); a cap of 0 disables
     * the cap check entirely (treat as "no limit").
     */
    private suspend fun evictIf() {
        val cap = OfflineCache.capBytes(capMb.toLong())
        if (cap <= 0L) return
        val toEvict = OfflineCache.evictionCandidates(
            entries = _entries.value.filter { !it.isFailed },
            newSizeBytes = 0L, // the new entry is already inside entries
            capBytes = cap,
            protectedIds = playingIds
        )
        if (toEvict.isEmpty()) return
        val ids = toEvict.map { it.id }.toSet()
        filesForIds(ids).forEach { it.delete() }
        _entries.value = _entries.value.filter { it.id !in ids }
        _statuses.value = _statuses.value.filterKeys { it !in ids }
        local.setDownloads(_entries.value)
    }

    private fun extractFromUrl(url: String): String =
        url.substringAfterLast('.', "")
            .substringBefore('?')
            .takeIf { it.isNotBlank() && it.length <= 4 }
            ?: OfflineCache.DEFAULT_EXT

    private fun filesForIds(ids: Set<Long>): List<File> =
        downloadsDir.listFiles()?.filter { f ->
            f.isFile && ids.any { f.name.startsWith("$it.") }
        } ?: emptyList()

    private fun filesFor(songId: Long): List<File> =
        downloadsDir.listFiles { f ->
            f.isFile && (f.name.startsWith("$songId.") || f.name == "tmp_$songId")
        }
            ?.toList() ?: emptyList()
}
