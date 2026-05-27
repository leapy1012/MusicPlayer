package gd.app.musicplayer.playback.command

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Parcelable
import gd.app.musicplayer.playback.service.MusicPlaybackService

object PlaybackServiceStarter {
    fun startAction(context: Context, action: String): Boolean = startAction(context, action, null)

    fun startAction(context: Context, action: String, actionData: Parcelable?): Boolean {
        return runCatching {
            val appContext = context.applicationContext
            val intent = Intent(appContext, MusicPlaybackService::class.java).apply {
                this.action = action
                actionData?.let { putExtra(PlaybackServiceExtras.EXTRA_ACTION_DATA, it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
            true
        }.getOrDefault(false)
    }

    fun startActionWithIndex(context: Context, action: String, index: Int): Boolean {
        return runCatching {
            val appContext = context.applicationContext
            val intent = Intent(appContext, MusicPlaybackService::class.java).apply {
                this.action = action
                putExtra(PlaybackServiceExtras.EXTRA_ACTION_DATA, IndexActionData(index))
                putExtra(PlaybackServiceExtras.EXTRA_INDEX, index)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
            true
        }.getOrDefault(false)
    }
}
