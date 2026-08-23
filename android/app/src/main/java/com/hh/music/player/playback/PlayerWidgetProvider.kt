package com.hh.music.player.playback

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.hh.music.player.MainActivity
import com.hh.music.player.R
import com.google.common.util.concurrent.MoreExecutors

/**
 * Home-screen widget (v1.7): cover/title/artist + prev / play-pause / next.
 *
 * Talks to [PlaybackService] through a MediaController (same trusted-session
 * contract as the in-app [PlayerController]). Every button press re-connects
 * lazily, sends the command, and releases — the widget has no persistent
 * process of its own. State refreshes come from onUpdate plus the service's
 * explicit update broadcasts on track/metadata changes.
 */
class PlayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Push whatever we can resolve right now; the service broadcast keeps it fresh.
        withController(context) { controller ->
            val views = buildViews(context, controller)
            appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> withController(context) { it.toggle() }
            ACTION_NEXT -> withController(context) { it.seekToNext() }
            ACTION_PREV -> withController(context) { it.seekToPrevious() }
            ACTION_REFRESH_STATE -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidgetProvider::class.java))
                withController(context) { controller ->
                    val views = buildViews(context, controller)
                    ids.forEach { id -> manager.updateAppWidget(id, views) }
                }
            }
        }
    }

    private inline fun withController(context: Context, crossinline block: (MediaController) -> Unit) {
        runCatching {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({
                try {
                    val controller = future.get()
                    block(controller)
                    // Keep the controller alive briefly so async UI work settles,
                    // then release; widgets must not leak a binder per tap.
                    controller.release()
                } catch (_: Exception) {
                    // Service not running yet — nothing sensible to render.
                }
            }, MoreExecutors.directExecutor())
        }
    }

    private fun MediaController.toggle() {
        if (isPlaying || playbackState == Player.STATE_BUFFERING) pause() else play()
    }

    private fun buildViews(context: Context, controller: MediaController?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_player)
        val metadata = controller?.mediaMetadata
        val playing = controller?.isPlaying == true

        views.setTextViewText(
            R.id.widget_title,
            metadata?.title?.toString()?.ifBlank { null } ?: "HH Music"
        )
        views.setTextViewText(
            R.id.widget_artist,
            metadata?.artist?.toString()?.ifBlank { null } ?: "点按播放"
        )

        // Album art: Media3 can load http urls into a RemoteViews ImageView via
        // setImageViewUri only for content/file schemes; remote bitmaps are
        // downloaded by the widget host lazily. We keep the static icon to stay
        // lightweight and avoid per-track network fetches from the launcher.
        views.setImageViewResource(R.id.widget_art, R.drawable.ic_music_note)

        views.setImageViewResource(
            R.id.widget_toggle,
            if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )

        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        views.setOnClickPendingIntent(R.id.widget_prev, actionIntent(context, ACTION_PREV))
        views.setOnClickPendingIntent(R.id.widget_toggle, actionIntent(context, ACTION_TOGGLE))
        views.setOnClickPendingIntent(R.id.widget_next, actionIntent(context, ACTION_NEXT))
        return views
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun actionIntent(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, PlayerWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    companion object {
        const val ACTION_TOGGLE = "com.hh.music.player.widget.TOGGLE"
        const val ACTION_NEXT = "com.hh.music.player.widget.NEXT"
        const val ACTION_PREV = "com.hh.music.player.widget.PREV"
        const val ACTION_REFRESH_STATE = "com.hh.music.player.widget.REFRESH_STATE"

        /** Convenience hook for PlaybackService/PlayerController to push updates. */
        fun requestRefresh(context: Context) {
            context.sendBroadcast(
                Intent(context, PlayerWidgetProvider::class.java).setAction(ACTION_REFRESH_STATE)
            )
        }
    }
}
