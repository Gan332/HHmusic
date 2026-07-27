package com.hh.music.player.playback

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.media.app.MediaStyle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.hh.music.player.HHMusicApp
import com.hh.music.player.MainActivity
import com.hh.music.player.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import java.util.concurrent.TimeUnit

/**
 * Foreground media playback service backed by ExoPlayer + a MediaSession.
 *
 * Integrates with all system media control centres — stock Android,
 * HarmonyOS (Huawei), MIUI (Xiaomi), ColorOS (OPPO), OriginOS (vivo) —
 * by providing a properly styled [MediaStyle] notification with album art.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var notificationManager: NotificationManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var artworkJob: Job? = null
    private var cachedArtwork: Bitmap? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

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

        // Observe media item changes to load artwork asynchronously
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    loadArtwork(mediaItem?.mediaMetadata?.artworkUri)
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != null && handleCustomAction(intent.action!!)) return START_STICKY
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Handle custom action intents from notification buttons.
     * @return true if the action was recognised and handled.
     */
    private fun handleCustomAction(action: String): Boolean {
        val player = mediaSession?.player ?: return false
        return when (action) {
            ACTION_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play()
                true
            }
            ACTION_NEXT -> {
                player.seekToNextMediaItem()
                true
            }
            ACTION_PREVIOUS -> {
                player.seekToPreviousMediaItem()
                true
            }
            ACTION_STOP -> {
                player.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                true
            }
            else -> false
        }
    }

    /** Load album art from the URI and cache the bitmap for the notification. */
    private fun loadArtwork(uri: Uri?) {
        artworkJob?.cancel()
        cachedArtwork = null
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
            if (bytes != null) {
                cachedArtwork = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                // Force notification refresh now that artwork is ready
                mediaSession?.let { session ->
                    val notification = buildNotification(session)
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    override fun onUpdateNotification(session: MediaSession): MediaNotification {
        val notification = buildNotification(session)
        return MediaNotification(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(session: MediaSession): Notification {
        val player = session.player
        val metadata = player.currentMediaItem?.mediaMetadata ?: MediaMetadata.EMPTY
        val isPlaying = player.isPlaying

        // Play/pause action
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause, "暂停",
                serviceCommand(this, ACTION_PLAY_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play, "播放",
                serviceCommand(this, ACTION_PLAY_PAUSE)
            )
        }

        return NotificationCompat.Builder(this, HHMusicApp.MEDIA_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(metadata.title ?: metadata.displayTitle ?: "")
            .setContentText(metadata.artist ?: metadata.subtitle ?: "")
            .setSubText(metadata.albumTitle ?: "")
            .setLargeIcon(cachedArtwork)
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .addAction(
                android.R.drawable.ic_media_previous, "上一首",
                serviceCommand(this, ACTION_PREVIOUS)
            )
            .addAction(playPauseAction)
            .addAction(
                android.R.drawable.ic_media_next, "下一首",
                serviceCommand(this, ACTION_NEXT)
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setDeleteIntent(
                serviceCommand(this, ACTION_STOP)
            )
            .build()
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

    companion object {
        const val NOTIFICATION_ID = 1001

        // Custom action strings (not system-defined)
        const val ACTION_PLAY_PAUSE = "com.hh.music.player.PLAY_PAUSE"
        const val ACTION_NEXT = "com.hh.music.player.NEXT"
        const val ACTION_PREVIOUS = "com.hh.music.player.PREVIOUS"
        const val ACTION_STOP = "com.hh.music.player.STOP"

        /** Build a [PendingIntent] that starts [PlaybackService] with a custom action. */
        private fun serviceCommand(context: Context, action: String): PendingIntent =
            PendingIntent.getService(
                context, action.hashCode(),
                Intent(context, PlaybackService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
    }
}
