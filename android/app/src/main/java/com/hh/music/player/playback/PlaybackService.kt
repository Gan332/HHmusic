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

    private var mediaSession: MediaSession? = null

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
            // Keep the CPU awake while the screen is off (WAKE_LOCK is declared
            // in the manifest); WAKE_MODE_NONE (the default) lets playback stall
            // during lock-screen listening on some devices.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // ExoPlayer only assigns its real audio session id when the audio sink is
        // created on first playback; at service-creation time it is still
        // C.AUDIO_SESSION_ID_UNSET (0). Attaching the equalizer then would either
        // fail outright or bind to the global output mix (affecting ALL audio on
        // the device), so we attach on the session-id change callback instead and
        // re-attach whenever the id changes (e.g. after an audio-track reset).
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                val eq = AppContainer.instance?.equalizerController ?: return
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    eq.attachTo(audioSessionId)
                } else {
                    eq.detach()
                }
            }
        })

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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        // Only our own app (same UID) and trusted system/assistant controllers may
        // drive playback — arbitrary third-party apps must not pause/skip/query.
        return if (controllerInfo.isTrusted) mediaSession else null
    }

    /**
     * Explicit task-removal decision: while a track is actually playing or
     * buffering we keep the foreground service alive so lock-screen playback
     * survives the swipe-away; when nothing is actively playing we tear the
     * service down instead of letting it linger.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        val activelyPlaying = player != null &&
            (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) &&
            player.playWhenReady
        if (!activelyPlaying) {
            player?.pause()
            stopSelf()
        }
    }

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