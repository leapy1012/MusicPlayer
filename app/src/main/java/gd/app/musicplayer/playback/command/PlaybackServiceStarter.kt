package gd.app.musicplayer.playback.command

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Parcelable
import android.util.Log
import gd.app.musicplayer.playback.service.MusicPlaybackService

/** Explicit user controls can start playback even when no service instance is alive. */
object PlaybackServiceStarter {
    fun startIntent(context: Context, intent: Intent): Boolean = try {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            PlaybackServiceActions.requiresForegroundStart(intent.action)) app.startForegroundService(intent)
        else app.startService(intent)
        true
    } catch (error: Exception) {
        Log.w("MusicPlayback", "Could not dispatch playback action " + intent.action, error)
        false
    }

    fun startAction(context: Context, action: String): Boolean = startAction(context, action, null)

    fun startAction(context: Context, action: String, actionData: Parcelable?): Boolean =
        startIntent(context, Intent(context.applicationContext, MusicPlaybackService::class.java).apply {
            this.action = action
            actionData?.let { putExtra(PlaybackServiceExtras.EXTRA_ACTION_DATA, it) }
        })

    fun startActionWithIndex(context: Context, action: String, index: Int): Boolean =
        startIntent(context, Intent(context.applicationContext, MusicPlaybackService::class.java).apply {
            this.action = action
            putExtra(PlaybackServiceExtras.EXTRA_ACTION_DATA, IndexActionData(index))
            putExtra(PlaybackServiceExtras.EXTRA_INDEX, index)
        })
}
