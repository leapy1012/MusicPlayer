package gd.app.musicplayer.feature.widget.provider

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.R
import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import gd.app.musicplayer.domain.usecase.playlist.ToggleFavoriteTrackUseCase
import gd.app.musicplayer.playback.service.MusicPlaybackService
import gd.app.musicplayer.feature.widget.WidgetCatalog
import gd.app.musicplayer.feature.widget.WidgetConfig
import gd.app.musicplayer.core.datastore.WidgetConfigStore
import gd.app.musicplayer.playback.command.PlaybackServiceActions
import gd.app.musicplayer.playback.command.PlaybackServiceStarter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetUpdateCoordinator @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val snapshotLoader: WidgetPlaybackSnapshotLoader,
    private val widgetConfigStore: WidgetConfigStore,
    private val settingPreferencesDataStore: SettingPreferencesDataStore,
    private val toggleFavoriteTrackUseCase: ToggleFavoriteTrackUseCase
) {

    suspend fun updateAll() {
        val snapshot = snapshotLoader.load()
        updateAll(snapshot)
    }

    suspend fun updateAll(snapshot: WidgetPlaybackSnapshot) {
        val manager = AppWidgetManager.getInstance(appContext)
        WidgetCatalog.items.forEach { spec ->
            val appWidgetIds = manager.getAppWidgetIds(
                ComponentName(appContext, spec.providerClass)
            )

            if (appWidgetIds.isNotEmpty()) {
                updateWidgetsInternal(
                    manager = manager,
                    appWidgetIds = appWidgetIds,
                    classify = spec.classify,
                    snapshot = snapshot
                )
            }
        }
    }

    suspend fun updateWidgets(
        appWidgetIds: IntArray,
        classify: String
    ) {
        if (appWidgetIds.isEmpty()) return

        val manager = AppWidgetManager.getInstance(appContext)
        val snapshot = snapshotLoader.load()

        updateWidgetsInternal(
            manager = manager,
            appWidgetIds = appWidgetIds,
            classify = classify,
            snapshot = snapshot
        )
    }

    suspend fun deleteWidgets(appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            widgetConfigStore.delete(appWidgetId)
        }
    }

    suspend fun togglePlayMode() {
        settingPreferencesDataStore.cyclePlayMode()
        updateAll()
    }

    suspend fun toggleFavorite() {
        val snapshot = snapshotLoader.load()
        val track = snapshot.currentTrack ?: return

        if (MusicPlaybackService.isRunning) {
            PlaybackServiceStarter.startAction(
                context = appContext,
                action = PlaybackServiceActions.ACTION_TOGGLE_FAVORITE
            )
        } else {
            toggleFavoriteTrackUseCase(track.id)
            updateAll()
        }
    }

    suspend fun playQueueIndex(index: Int) {
        if (index < 0) return

        PlaybackServiceStarter.startActionWithIndex(
            context = appContext,
            action = PlaybackServiceActions.ACTION_CHANGE_MUSIC_BY_INDEX,
            index = index
        )

        updateAll()
    }

    fun notifyQueueChanged(appWidgetIds: IntArray) {
        val manager = AppWidgetManager.getInstance(appContext)

        appWidgetIds.forEach { appWidgetId ->
            manager.notifyAppWidgetViewDataChanged(
                appWidgetId,
                R.id.widget_queue
            )
        }
    }

    private suspend fun updateWidgetsInternal(
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        classify: String,
        snapshot: WidgetPlaybackSnapshot
    ) {
        val configs: Map<Int, WidgetConfig> =
            appWidgetIds.associateWith { appWidgetId ->
                widgetConfigStore.load(
                    appWidgetId = appWidgetId,
                    classify = classify
                )
            }

        WidgetRenderer.updateWidgets(
            context = appContext,
            manager = manager,
            appWidgetIds = appWidgetIds,
            classify = classify,
            snapshot = snapshot,
            configs = configs
        )
    }
}
