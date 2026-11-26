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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val renderMutex = Mutex()
    private data class Rendered(val config: WidgetConfig, val snapshot: WidgetPlaybackSnapshot)
    private val rendered = mutableMapOf<Int, Rendered>()

    suspend fun updateAll() = renderMutex.withLock {
        renderAll(snapshotLoader.load())
    }

    suspend fun updateAll(snapshot: WidgetPlaybackSnapshot) = renderMutex.withLock {
        renderAll(snapshot)
    }

    private suspend fun renderAll(snapshot: WidgetPlaybackSnapshot) {
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
        classify: String,
        forceFull: Boolean = false
    ) = renderMutex.withLock {
        if (forceFull) appWidgetIds.forEach { rendered.remove(it) }
        if (appWidgetIds.isEmpty()) return@withLock

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

        appWidgetIds.forEach { id ->
            val config = configs.getValue(id)
            val previous = rendered[id]
            val controlsOnly = previous != null && previous.config == config &&
                previous.snapshot.copy(positionMs = snapshot.positionMs, isPlaying = snapshot.isPlaying) == snapshot
            if (controlsOnly) {
                if (previous!!.snapshot != snapshot) WidgetRenderer.updatePlaybackControls(
                    appContext, manager, id, classify, config, snapshot)
            } else {
                WidgetRenderer.updateWidgets(appContext, manager, intArrayOf(id), classify, snapshot, configs)
            }
            rendered[id] = Rendered(config, snapshot)
        }
    }
}
