package gd.app.musicplayer.ui.feature.widget.provider

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.ui.feature.widget.WidgetConfigStore
import gd.app.musicplayer.playback.PlaybackMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

abstract class BaseMusicAppWidgetProvider : AppWidgetProvider() {
    abstract val classify: String

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetRenderer.updateWidgets(context, appWidgetManager, appWidgetIds, classify)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = WidgetConfigStore(context)
        appWidgetIds.forEach(store::delete)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        WidgetRenderer.updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId), classify)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_MODE -> {
                val prefs = context.appDependencies.preferenceUtil
                val nextMode = when (prefs.getPlayMode()) {
                    PlaybackMode.SINGLE -> PlaybackMode.ORDER
                    PlaybackMode.ORDER -> PlaybackMode.LOOP_ALL
                    PlaybackMode.LOOP_ALL -> PlaybackMode.SHUFFLE_ALL
                    else -> PlaybackMode.SINGLE
                }
                prefs.setPlayMode(nextMode)
                WidgetRenderer.updateAll(context)
            }

            ACTION_PLAY_QUEUE_INDEX -> {
                val index = intent.getIntExtra(EXTRA_QUEUE_INDEX, -1)
                val queue = runBlocking(Dispatchers.IO) {
                    context.appDependencies.playbackQueueRepo.getQueue()
                }
                if (index in queue.indices) {
                    context.appDependencies.playTracksUseCase(context, queue, index)
                }
            }

            ACTION_TOGGLE_FAVORITE -> {
                val track = runBlocking(Dispatchers.IO) {
                    WidgetRenderer.loadPlaybackSnapshot(context).currentTrack
                } ?: run {
                    WidgetRenderer.updateAll(context)
                    return
                }
                CoroutineScope(Dispatchers.IO).launch {
                    context.appDependencies.toggleFavoriteTrackUseCase(track.id)
                    WidgetRenderer.updateAll(context)
                }
                return
            }

            ACTION_PLAYBACK_SESSION_UPDATED -> {
                WidgetRenderer.updateAll(context)
            }
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_TOGGLE_MODE = "gd.app.musicplayer.action.WIDGET_TOGGLE_MODE"
        const val ACTION_PLAY_QUEUE_INDEX = "gd.app.musicplayer.action.WIDGET_PLAY_QUEUE_INDEX"
        const val ACTION_TOGGLE_FAVORITE = "gd.app.musicplayer.action.WIDGET_TOGGLE_FAVORITE"
        const val ACTION_PLAYBACK_SESSION_UPDATED =
            "gd.app.musicplayer.action.WIDGET_PLAYBACK_SESSION_UPDATED"
        const val EXTRA_QUEUE_INDEX = "widget_queue_index"
    }
}

class Widget2x1Provider : BaseMusicAppWidgetProvider() {
    override val classify: String = "2*1"
}

class Widget3x2Provider : BaseMusicAppWidgetProvider() {
    override val classify: String = "3*2"
}

class Widget4x1Provider : BaseMusicAppWidgetProvider() {
    override val classify: String = "4*1"
}

class Widget4x2Provider : BaseMusicAppWidgetProvider() {
    override val classify: String = "4*2"
}

class Widget4x3Provider : BaseMusicAppWidgetProvider() {
    override val classify: String = "4*3"
}

class Widget4x4Provider : BaseMusicAppWidgetProvider() {
    override val classify: String = "4*4"
}

class WidgetListProvider : BaseMusicAppWidgetProvider() {
    override val classify: String = "List"
}

