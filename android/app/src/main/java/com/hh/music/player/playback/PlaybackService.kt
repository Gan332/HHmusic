package com.hh.music.player.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.hh.music.player.MainActivity
import com.hh.music.player.data.AppContainer

/**
 * Foreground media playback service backed by ExoPlayer + a MediaSession.
 * Exposes a MediaController on the client side via [PlayerController].
 */
class PlaybackService : MediaSessionService() {

    companion object {
        /**
         * Fixed audio session id so the platform Equalizer (attached once, reused
         * across track switches) never loses track of the player's session.
         */
        const val FIXED_AUDIO_SESSION_ID = 1001
    }

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioSessionId(FIXED_AUDIO_SESSION_ID)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Mount the equalizer on the player's (fixed) audio session.
        AppContainer.instance?.equalizerController?.attachTo(FIXED_AUDIO_SESSION_ID)

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

    override fun onDestroy() {
        AppContainer.instance?.equalizerController?.detach()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
