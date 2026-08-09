package com.hh.music.player.data.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.hh.music.player.data.Album
import com.hh.music.player.data.Artist
import com.hh.music.player.data.Song
import java.util.Locale

/**
 * 本地音乐支持：扫描设备 MediaStore 中的音频 + 读取 SAF 导入文件的元数据。
 * 本地歌曲使用负数 id，避免与网易云音乐的正数 id 冲突：
 *  - MediaStore 歌曲：-(_id)，范围约 [-2^30, 0)
 *  - SAF 导入歌曲：[uriId]，固定落在 (-2^40-2^31, -2^40) 区间，两类永远不撞。
 */
object LocalMusic {

    /**
     * Stable negative id for a SAF/local-file uri, in a reserved range below
     * -2^40 so it can never collide with MediaStore ids or NetEase positive ids.
     * Pure function — reused by [songFromUri] and unit-tested on the JVM.
     */
    fun uriId(uri: String): Long {
        val h = (uri.hashCode() and 0x7fffffff).toLong()
        return -(1L shl 40) - h.coerceAtLeast(0L)
    }

    /**
     * Combine MediaStore scan + SAF imports into one deduped list. Entries whose
     * metadata could not be read (null) are dropped. Pure function — JVM-testable.
     */
    fun merge(scanned: List<Song>, importedEntries: List<Song?>): List<Song> {
        val out = ArrayList<Song>(scanned.size + importedEntries.size)
        val seen = HashSet<String>()
        for (s in scanned) {
            val key = s.localUri ?: "media:${s.id}"
            if (seen.add(key)) out += s
        }
        for (s in importedEntries) {
            if (s == null) continue // stale/dead uri — caller should prune it
            val key = s.localUri ?: continue
            if (seen.add(key)) out += s
        }
        return out
    }

    /**
     * Quick filter by title, artist text or album name. Blank queries return the
     * original list untouched. Pure function — JVM-testable.
     */
    fun filterByQuery(songs: List<Song>, query: String): List<Song> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) return songs
        return songs.filter { song ->
            song.name.lowercase(Locale.ROOT).contains(q) ||
                song.artistText.lowercase(Locale.ROOT).contains(q) ||
                song.album.name.lowercase(Locale.ROOT).contains(q)
        }
    }

    /** Android 13+ 使用细粒度音频权限，更早版本使用存储权限。 */
    fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasAudioPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, audioPermission()) ==
            PackageManager.PERMISSION_GRANTED

    /** 扫描设备 MediaStore 中所有音乐文件（需要音频/存储权限）。 */
    fun scanDeviceMusic(context: Context): List<Song> {
        if (!hasAudioPermission(context)) return emptyList()
        val songs = mutableListOf<Song>()
        runCatching {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION
            )
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mediaId.toString()
                    )
                    songs += Song(
                        id = -mediaId,
                        name = cursor.getString(titleCol).orEmpty().ifBlank { "未知歌曲" },
                        artists = listOf(
                            Artist(name = cursor.getString(artistCol).orEmpty().ifBlank { "未知艺术家" })
                        ),
                        album = Album(name = cursor.getString(albumCol).orEmpty()),
                        duration = cursor.getLong(durationCol),
                        localUri = uri.toString()
                    )
                }
            }
        }
        return songs
    }

    /**
     * 从任意可播放的 content:// uri（SAF 导入）读取元数据并构建 [Song]。
     * 返回 null 表示该 URI 已失效（无权限或文件已删除），调用方可据此清理导入记录。
     */
    fun songFromUri(context: Context, uriString: String): Song? {
        if (uriString.isBlank()) return null
        val uri = Uri.parse(uriString)
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationMs = 0L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            durationMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            // 无法读取元数据时回退到文件名
        } finally {
            runCatching { retriever.release() }
        }
        if (title.isNullOrBlank()) {
            // 只能通过 SAF 查询拿文件名的场景：查不到说明 URI 已失效（权限被收回/文件被删）。
            title = displayName(context, uri) ?: return null
        }
        return Song(
            id = uriId(uriString),
            name = title?.ifBlank { null } ?: "未知歌曲",
            artists = listOf(Artist(name = artist?.ifBlank { null } ?: "未知艺术家")),
            album = Album(name = album.orEmpty()),
            duration = durationMs,
            localUri = uriString
        )
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()
}
