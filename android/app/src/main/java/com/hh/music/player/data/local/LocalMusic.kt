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

/**
 * 本地音乐支持：扫描设备 MediaStore 中的音频 + 读取 SAF 导入文件的元数据。
 * 本地歌曲使用负数 id，避免与网易云音乐的正数 id 冲突。
 */
object LocalMusic {

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

    /** 从任意可播放的 content:// uri（SAF 导入）读取元数据并构建 [Song]。 */
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
            title = displayName(context, uri) ?: uri.lastPathSegment
        }
        return Song(
            id = -uriHash(uriString),
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

    private fun uriHash(uri: String): Long = (uri.hashCode() and 0x7fffffff).toLong()
}
