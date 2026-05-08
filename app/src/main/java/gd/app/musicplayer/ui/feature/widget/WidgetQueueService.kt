package gd.app.musicplayer.ui.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dagger.hilt.android.EntryPointAccessors
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.ui.feature.widget.provider.BaseMusicAppWidgetProvider
import gd.app.musicplayer.ui.feature.widget.provider.WidgetPlaybackSnapshotLoader
import gd.app.musicplayer.ui.feature.widget.provider.WidgetProviderEntryPoint
import kotlinx.coroutines.runBlocking

class WidgetQueueService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return QueueFactory(
            packageName = packageName,
            appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            ),
            service = this
        )
    }

    private class QueueFactory(
        private val packageName: String,
        private val appWidgetId: Int,
        private val service: WidgetQueueService
    ) : RemoteViewsFactory {

        private var items: List<Music> = emptyList()

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            items = runBlocking {
                snapshotLoader(service).load().queue
            }
        }

        override fun onDestroy() {
            items = emptyList()
        }

        override fun getCount(): Int {
            return items.size
        }

        override fun getViewAt(position: Int): RemoteViews {
            val track = items.getOrNull(position)
                ?: return RemoteViews(packageName, R.layout.widget_queue_item)

            val fillInIntent = Intent().putExtra(
                BaseMusicAppWidgetProvider.EXTRA_QUEUE_INDEX,
                position
            )

            return RemoteViews(packageName, R.layout.widget_queue_item).apply {
                setTextViewText(
                    R.id.widget_queue_item_position,
                    (position + 1).toString()
                )

                setTextViewText(
                    R.id.widget_queue_item_title,
                    track.title
                )

                setTextViewText(
                    R.id.widget_queue_item_artist,
                    track.artist
                )

                setViewVisibility(
                    R.id.widget_queue_item_divider,
                    if (position == items.lastIndex) View.GONE else View.VISIBLE
                )

                setOnClickFillInIntent(R.id.widget_queue_item, fillInIntent)
                setOnClickFillInIntent(R.id.widget_queue_item_title, fillInIntent)
                setOnClickFillInIntent(R.id.widget_queue_item_artist, fillInIntent)
                setOnClickFillInIntent(R.id.widget_queue_item_position, fillInIntent)
            }
        }

        override fun getLoadingView(): RemoteViews {
            return RemoteViews(packageName, R.layout.widget_queue_item)
        }

        override fun getViewTypeCount(): Int {
            return 1
        }

        override fun getItemId(position: Int): Long {
            return items.getOrNull(position)?.id ?: position.toLong()
        }

        override fun hasStableIds(): Boolean {
            return true
        }

        private fun snapshotLoader(
            service: WidgetQueueService
        ): WidgetPlaybackSnapshotLoader {
            return EntryPointAccessors
                .fromApplication(
                    service.applicationContext,
                    WidgetProviderEntryPoint::class.java
                )
                .widgetPlaybackSnapshotLoader()
        }
    }
}