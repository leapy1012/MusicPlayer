package gd.app.musicplayer.ui.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import gd.app.musicplayer.R
import gd.app.musicplayer.ui.feature.widget.provider.BaseMusicAppWidgetProvider
import gd.app.musicplayer.playback.MusicPlaybackController

class WidgetQueueService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return QueueFactory(
            packageName = packageName,
            appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
        )
    }

    private class QueueFactory(
        private val packageName: String,
        private val appWidgetId: Int
    ) : RemoteViewsFactory {
        private var items = MusicPlaybackController.state.value.queue

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            items = MusicPlaybackController.state.value.queue
        }

        override fun onDestroy() = Unit

        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews {
            val track = items.getOrNull(position) ?: return RemoteViews(packageName, R.layout.widget_queue_item)
            return RemoteViews(packageName, R.layout.widget_queue_item).apply {
                setTextViewText(R.id.widget_queue_item_position, (position + 1).toString())
                setTextViewText(R.id.widget_queue_item_title, track.title)
                setTextViewText(R.id.widget_queue_item_artist, track.artist)
                setViewVisibility(
                    R.id.widget_queue_item_divider,
                    if (position == items.lastIndex) View.GONE else View.VISIBLE
                )
                val fillInIntent = Intent().putExtra(BaseMusicAppWidgetProvider.EXTRA_QUEUE_INDEX, position)
                setOnClickFillInIntent(R.id.widget_queue_item, fillInIntent)
                setOnClickFillInIntent(R.id.widget_queue_item_title, fillInIntent)
                setOnClickFillInIntent(R.id.widget_queue_item_artist, fillInIntent)
                setOnClickFillInIntent(R.id.widget_queue_item_position, fillInIntent)
            }
        }

        override fun getLoadingView(): RemoteViews = RemoteViews(packageName, R.layout.widget_queue_item)

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = items.getOrNull(position)?.id ?: position.toLong()

        override fun hasStableIds(): Boolean = true
    }
}
