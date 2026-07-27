package com.hh.music.player.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.hh.music.player.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Foreground media playback service backed by ExoPlayer + a MediaSession.
 *
 * Integrates with all system media control centres — stock Android,
 * HarmonyOS (Huawei), MIUI (Xiaomi), ColorOS (OPPO), OriginOS (vivo) —
 * by loading album art into [MediaMetadata.artworkData] so that Media3's
 * default notification automatically shows the cover image.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var artworkJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Observe media item changes → load artwork and attach as artworkData
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    loadAndAttachArtwork(mediaItem?.mediaMetadata?.artworkUri, player)
                }
            }
        )

        val sessionActivityPendingIntent =
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /** Load album art from URI and attach as [MediaMetadata.artworkData] on the current item. */
    private fun loadAndAttachArtwork(uri: Uri?, player: Player) {
        artworkJob?.cancel()
        if (uri == null || !uri.toString().startsWith("http")) return

        artworkJob = serviceScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                try {
                    val request = OkHttpRequest.Builder().url(uri.toString()).build()
                    httpClient.newCall(request).execute().let { resp ->
                        if (resp.isSuccessful) resp.body?.bytes() else null
                    }
                } catch (_: Exception) { null }
            }
            if (bytes == null) return@launch

            // Compress to JPEG for smaller size
            val compressed = withContext(Dispatchers.Default) {
                try {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    out.toByteArray()
                } catch (_: Exception) { bytes }
            }

            val idx = player.currentMediaItemIndex
            if (idx in 0 until player.mediaItemCount) {
                val current = player.getMediaItemAt(idx)
                val updated = current.buildUpon()
                    .setMediaMetadata(
                        current.mediaMetadata.buildUpon()
                            .setArtworkData(compressed, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            .build()
                    )
                    .build()
                player.replaceMediaItem(idx, updated)
            }
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        artworkJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
