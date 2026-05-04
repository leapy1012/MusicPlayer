package gd.app.musicplayer.ui.feature.widget.provider

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.core.extension.isFavorite
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.playback.MusicPlaybackService
import gd.app.musicplayer.playback.PlaybackMode
import gd.app.musicplayer.ui.feature.widget.WidgetCatalog
import gd.app.musicplayer.ui.feature.widget.WidgetConfigActivity
import gd.app.musicplayer.ui.feature.widget.WidgetConfigStore
import gd.app.musicplayer.ui.feature.widget.WidgetQueueService
import gd.app.musicplayer.ui.shell.MainActivity
import gd.app.musicplayer.util.PreferenceUtil
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal object WidgetRenderer {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        WidgetCatalog.items.forEach { spec ->
            val ids = manager.getAppWidgetIds(ComponentName(context, spec.providerClass))
            if (ids.isNotEmpty()) {
                updateWidgets(context, manager, ids, spec.classify)
            }
        }
    }

    fun updateWidgets(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        classify: String
    ) {
        val snapshot = loadPlaybackSnapshot(context)
        appWidgetIds.forEach { appWidgetId ->
            val remoteViews = buildRemoteViews(context, appWidgetId, classify, snapshot)
            manager.updateAppWidget(appWidgetId, remoteViews)
            manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_queue)
        }
    }

    internal fun loadPlaybackSnapshot(context: Context): WidgetPlaybackSnapshot = runBlocking(Dispatchers.IO) {
        val dependencies = context.appDependencies
        val queue = dependencies.playbackQueueRepo.getQueue()
        val runtimeState = dependencies.playbackRuntimeStateStore.state.value
        val currentTrack = runtimeState.currentTrack

        val resolvedIndex = currentTrack
            ?.let { track -> queue.indexOfFirst { queued -> queued.id == track.id } }
            ?: -1

        WidgetPlaybackSnapshot(
            queue = queue,
            currentTrack = currentTrack,
            currentIndex = resolvedIndex,
            positionMs = runtimeState.positionMs,
            isPlaying = runtimeState.isPlaying
        )
    }

    private fun buildRemoteViews(
        context: Context,
        appWidgetId: Int,
        classify: String,
        snapshot: WidgetPlaybackSnapshot
    ): RemoteViews {
        val store = WidgetConfigStore(context)
        val config = store.load(appWidgetId, classify)
        val spec = WidgetCatalog.specForClassify(classify)
        val style = spec.styles.firstOrNull { it.styleKey == config.styleKey } ?: spec.styles.first()
        val theme = WidgetCatalog.themeOption(config.themeType, config.themeIndex)
        val track = snapshot.currentTrack
        val remoteViews = RemoteViews(context.packageName, style.layoutRes)
        val useDarkForeground = theme.drawableRes == R.drawable.widget_color_bg_012
        val textColor = if (useDarkForeground) Color.BLACK else Color.WHITE
        val secondaryTextColor = if (useDarkForeground) 0x99000000.toInt() else 0xB3FFFFFF.toInt()
        val iconColor = if (useDarkForeground) Color.BLACK else Color.WHITE

        remoteViews.setImageViewResource(R.id.widget_background_image, theme.drawableRes)
        remoteViews.setInt(R.id.widget_background_image, "setImageAlpha", (config.alpha * 255f).toInt())
        remoteViews.setTextViewText(R.id.widget_title, track?.title ?: context.getString(R.string.music))
        remoteViews.setTextViewText(
            R.id.widget_artist,
            track?.artist?.takeIf { it.isNotBlank() } ?: context.getString(R.string.artist)
        )
        remoteViews.setTextViewText(
            R.id.widget_queue_info,
            if (snapshot.hasTrack) "${snapshot.currentIndex + 1}/${snapshot.queue.size}" else "0/0"
        )
        remoteViews.setTextColor(R.id.widget_title, textColor)
        remoteViews.setTextColor(R.id.widget_artist, secondaryTextColor)
        remoteViews.setTextColor(R.id.widget_queue_info, secondaryTextColor)
        remoteViews.setViewVisibility(R.id.widget_play, if (snapshot.isPlaying) View.GONE else View.VISIBLE)
        remoteViews.setViewVisibility(R.id.widget_pause, if (snapshot.isPlaying) View.VISIBLE else View.GONE)
        remoteViews.setViewVisibility(
            R.id.widget_favorite_selected,
            if (track?.isFavorite() == true) View.VISIBLE else View.GONE
        )
        remoteViews.setViewVisibility(
            R.id.widget_favorite_unselected,
            if (track?.isFavorite() == true) View.GONE else View.VISIBLE
        )
        remoteViews.setImageViewResource(R.id.widget_mode, modeIcon(PreferenceUtil.getInstance(context).getPlayMode()))
        tintControl(remoteViews, R.id.widget_previous, iconColor)
        tintControl(remoteViews, R.id.widget_next, iconColor)
        tintControl(remoteViews, R.id.widget_mode, iconColor)
        tintControl(remoteViews, R.id.widget_play, iconColor)
        tintControl(remoteViews, R.id.widget_pause, iconColor)
        tintControl(remoteViews, R.id.widget_setting, iconColor)
        tintControl(remoteViews, R.id.widget_favorite_selected, ContextCompat.getColor(context, R.color.color_theme))
        tintControl(remoteViews, R.id.widget_favorite_unselected, iconColor)

        val progress = if (track != null && track.duration > 0) {
            (snapshot.positionMs * 100 / track.duration.toLong()).coerceIn(0L, 100L).toInt()
        } else {
            0
        }
        remoteViews.setProgressBar(R.id.widget_progress, 100, progress, false)
        remoteViews.setProgressBar(R.id.widget_progress_black, 100, progress, false)
        remoteViews.setViewVisibility(
            R.id.widget_progress,
            if (useDarkForeground) View.GONE else View.VISIBLE
        )
        remoteViews.setViewVisibility(
            R.id.widget_progress_black,
            if (useDarkForeground) View.VISIBLE else View.GONE
        )

        val artwork = loadArtwork(context, track)
        if (artwork != null) {
            remoteViews.setImageViewBitmap(R.id.widget_album_image, artwork)
        } else {
            remoteViews.setImageViewResource(R.id.widget_album_image, R.drawable.default_album_identify)
        }

        bindActions(context, remoteViews, appWidgetId, classify, snapshot, spec.providerClass)
        return remoteViews
    }

    private fun bindActions(
        context: Context,
        remoteViews: RemoteViews,
        appWidgetId: Int,
        classify: String,
        snapshot: WidgetPlaybackSnapshot,
        providerClass: Class<*>
    ) {
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_EXPAND_PLAYER, true)
        }
        remoteViews.setOnClickPendingIntent(
            R.id.widget_background_image,
            PendingIntent.getActivity(
                context,
                appWidgetId * 100 + 1,
                mainIntent,
                pendingIntentFlags()
            )
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widget_previous,
            serviceAction(context, MusicPlaybackService.ACTION_PREVIOUS, appWidgetId * 100 + 2)
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widget_next,
            serviceAction(context, MusicPlaybackService.ACTION_NEXT, appWidgetId * 100 + 3)
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widget_play,
            serviceAction(context, MusicPlaybackService.ACTION_TOGGLE_PLAY_PAUSE, appWidgetId * 100 + 4)
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widget_pause,
            serviceAction(context, MusicPlaybackService.ACTION_TOGGLE_PLAY_PAUSE, appWidgetId * 100 + 5)
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widget_flipper_play_pause,
            serviceAction(context, MusicPlaybackService.ACTION_TOGGLE_PLAY_PAUSE, appWidgetId * 100 + 13)
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widget_mode,
            widgetBroadcast(
                context = context,
                providerClass = providerClass,
                requestCode = appWidgetId * 100 + 6,
                action = BaseMusicAppWidgetProvider.ACTION_TOGGLE_MODE,
                appWidgetId = appWidgetId
            )
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widget_setting,
            PendingIntent.getActivity(
                context,
                appWidgetId * 100 + 7,
                WidgetConfigActivity.intent(context, appWidgetId, classify),
                pendingIntentFlags()
            )
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widget_favorite_selected,
            widgetBroadcast(
                context = context,
                providerClass = providerClass,
                requestCode = appWidgetId * 100 + 8,
                action = BaseMusicAppWidgetProvider.ACTION_TOGGLE_FAVORITE,
                appWidgetId = appWidgetId
            )
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widget_favorite_unselected,
            widgetBroadcast(
                context = context,
                providerClass = providerClass,
                requestCode = appWidgetId * 100 + 9,
                action = BaseMusicAppWidgetProvider.ACTION_TOGGLE_FAVORITE,
                appWidgetId = appWidgetId
            )
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widget_flipper_favorite,
            widgetBroadcast(
                context = context,
                providerClass = providerClass,
                requestCode = appWidgetId * 100 + 14,
                action = BaseMusicAppWidgetProvider.ACTION_TOGGLE_FAVORITE,
                appWidgetId = appWidgetId
            )
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widget_visualizer,
            PendingIntent.getActivity(
                context,
                appWidgetId * 100 + 10,
                mainIntent,
                pendingIntentFlags()
            )
        )

        if (classify == "List") {
            val intent = Intent(context, WidgetQueueService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            remoteViews.setRemoteAdapter(R.id.widget_queue, intent)
            remoteViews.setEmptyView(R.id.widget_queue, R.id.widget_queue_empty)
            remoteViews.setPendingIntentTemplate(
                R.id.widget_queue,
                widgetBroadcast(
                    context = context,
                    providerClass = providerClass,
                    requestCode = appWidgetId * 100 + 11,
                    action = BaseMusicAppWidgetProvider.ACTION_PLAY_QUEUE_INDEX,
                    appWidgetId = appWidgetId
                )
            )
        } else if (!snapshot.hasTrack) {
            remoteViews.setOnClickPendingIntent(
                R.id.widget_content,
                PendingIntent.getActivity(
                    context,
                    appWidgetId * 100 + 12,
                    mainIntent,
                    pendingIntentFlags()
                )
            )
        }
    }

    private fun tintControl(remoteViews: RemoteViews, viewId: Int, color: Int) {
        remoteViews.setInt(viewId, "setColorFilter", color)
    }

    private fun loadArtwork(context: Context, track: Music?): Bitmap? {
        if (track == null) return null
        return runCatching {
            val source = track.albumPicture?.takeIf { it.isNotBlank() }
                ?: track.albumId.takeIf { it.isNotBlank() }?.let {
                    "content://media/external/audio/albumart/$it"
                }
                ?: track.data
            if (source.isNullOrBlank()) return@runCatching null
            val stream: InputStream? = if (source.contains("://")) {
                context.contentResolver.openInputStream(Uri.parse(source))
            } else {
                context.contentResolver.openInputStream(Uri.fromFile(File(source)))
            }
            stream.use { input -> BitmapFactory.decodeStream(input) }
        }.getOrNull()
    }

    private fun serviceAction(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MusicPlaybackService::class.java).setAction(action)
        return PendingIntent.getService(context, requestCode, intent, pendingIntentFlags())
    }

    private fun widgetBroadcast(
        context: Context,
        providerClass: Class<*>,
        requestCode: Int,
        action: String,
        appWidgetId: Int
    ): PendingIntent {
        val intent = Intent(context, providerClass).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, pendingIntentFlags())
    }

    private fun pendingIntentFlags(): Int {
        val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        return PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
    }

    private fun modeIcon(mode: Int): Int {
        return when (mode) {
            PlaybackMode.SINGLE -> R.drawable.vector_mode_single
            PlaybackMode.LOOP_ALL -> R.drawable.vector_mode_circle
            PlaybackMode.SHUFFLE_ALL -> R.drawable.vector_mode_random
            else -> R.drawable.vector_mode_order
        }
    }
}

internal data class WidgetPlaybackSnapshot(
    val queue: List<Music>,
    val currentTrack: Music?,
    val currentIndex: Int,
    val positionMs: Long,
    val isPlaying: Boolean
) {
    val hasTrack: Boolean
        get() = currentTrack != null && currentIndex in queue.indices
}
