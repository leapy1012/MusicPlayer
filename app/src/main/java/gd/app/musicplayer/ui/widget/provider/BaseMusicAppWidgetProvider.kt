package gd.app.musicplayer.ui.widget.provider

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class BaseMusicAppWidgetProvider : AppWidgetProvider() {

    protected abstract val classify: String

    private val widgetScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        widgetScope.launch {
            coordinator(context).updateWidgets(
                appWidgetIds = appWidgetIds,
                classify = classify
            )
        }
    }

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray
    ) {
        super.onDeleted(context, appWidgetIds)

        widgetScope.launch {
            coordinator(context).deleteWidgets(appWidgetIds)
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_REFRESH_WIDGETS,
            ACTION_TOGGLE_MODE,
            ACTION_TOGGLE_FAVORITE,
            ACTION_PLAY_QUEUE_INDEX -> {
                val pendingResult = goAsync()

                widgetScope.launch {
                    runCatching {
                        handleWidgetAction(
                            context = context,
                            intent = intent
                        )
                    }

                    pendingResult.finish()
                }
            }
        }
    }

    private suspend fun handleWidgetAction(
        context: Context,
        intent: Intent
    ) {
        val coordinator = coordinator(context)

        when (intent.action) {
            ACTION_REFRESH_WIDGETS -> {
                coordinator.updateAll()
            }

            ACTION_TOGGLE_MODE -> {
                coordinator.togglePlayMode()
            }

            ACTION_TOGGLE_FAVORITE -> {
                coordinator.toggleFavorite()
            }

            ACTION_PLAY_QUEUE_INDEX -> {
                val index = intent.getIntExtra(EXTRA_QUEUE_INDEX, -1)
                coordinator.playQueueIndex(index)
            }
        }

        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            coordinator.updateWidgets(
                appWidgetIds = intArrayOf(appWidgetId),
                classify = classify
            )
        } else {
            coordinator.updateAll()
        }
    }

    private fun coordinator(context: Context): WidgetUpdateCoordinator {
        return EntryPointAccessors
            .fromApplication(
                context.applicationContext,
                WidgetProviderEntryPoint::class.java
            )
            .widgetUpdateCoordinator()
    }

    companion object {
        const val ACTION_REFRESH_WIDGETS =
            "gd.app.musicplayer.action.REFRESH_WIDGETS"

        const val ACTION_TOGGLE_MODE =
            "gd.app.musicplayer.action.WIDGET_TOGGLE_MODE"

        const val ACTION_TOGGLE_FAVORITE =
            "gd.app.musicplayer.action.WIDGET_TOGGLE_FAVORITE"

        const val ACTION_PLAY_QUEUE_INDEX =
            "gd.app.musicplayer.action.WIDGET_PLAY_QUEUE_INDEX"

        const val EXTRA_QUEUE_INDEX =
            "extra_queue_index"
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