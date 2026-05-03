package gd.app.musicplayer.playback.notification


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import gd.app.musicplayer.R

open class LegacyRemoteViewsNotificationBuilder(
    context: Context,
    shouldUseDynamicColors: Boolean,
) : BaseMusicNotificationBuilder(
    context = context,
    shouldUseDynamicColors = shouldUseDynamicColors,
) {

    override fun buildNotification(
        content: MusicNotificationContent,
    ): Notification {
        createNotificationChannelIfNeeded()

        return NotificationCompat.Builder(context, context.packageName)
            .setChannelIdIfNeeded()
            .setCustomContentView(createCollapsedRemoteViews(content))
            .setCustomBigContentView(createExpandedRemoteViews(content))
            .setContentIntent(content.createContentIntent(context))
            .setSmallIcon(content.getSmallIconRes())
            .setOngoing(content.isPlaying())
            .setContentTitle(content.getTitle())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(false)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    protected fun createExpandedRemoteViews(
        content: MusicNotificationContent,
    ): RemoteViews {
        val albumArt = content.getAlbumArt(VIEW_TYPE_EXPANDED)
        val colorStyle = resolveColorStyle(albumArt)

        val layoutRes = if (colorStyle.useLightTheme) {
            R.layout.notify_layout_v18_large
        } else {
            R.layout.notify_layout_v18_large_night
        }

        return RemoteViews(context.packageName, layoutRes).apply {
            bindCommonActions(content)
            bindAlbumArt(
                albumArt = albumArt,
                fallbackRes = content.getDefaultAlbumArtRes(
                    viewType = VIEW_TYPE_EXPANDED,
                    useNightArtwork = false,
                ),
            )

            setImageViewResource(
                R.id.notify_play_pause,
                if (content.isPlaying()) {
                    R.drawable.notify_pause_new
                } else {
                    R.drawable.notify_play_new
                },
            )

            setImageViewResource(
                R.id.notify_favorite,
                if (content.isFavorite()) {
                    R.drawable.notify_favorite_new
                } else {
                    R.drawable.notify_unfavorite_new
                },
            )

            setViewVisibility(
                R.id.notify_desk_lrc,
                if (content.isDesktopLyricsEnabled()) View.VISIBLE else View.GONE,
            )

            setTextViewText(R.id.notify_text, content.getTitle())
            setTextViewText(R.id.notify_msg, content.getArtistName())
            setTextViewText(R.id.notify_extra, content.getAlbumName())
            setInt(R.id.notify_background, "setBackgroundColor", colorStyle.backgroundColor)

            applyExpandedTheme(
                useLightTheme = colorStyle.useLightTheme,
                favorite = content.isFavorite(),
            )
        }
    }

    protected fun createCollapsedRemoteViews(
        content: MusicNotificationContent,
    ): RemoteViews {
        val albumArt = content.getAlbumArt(VIEW_TYPE_COLLAPSED)
        val colorStyle = resolveColorStyle(albumArt)

        val layoutRes = if (colorStyle.useLightTheme) {
            R.layout.notify_layout_v18_samll
        } else {
            R.layout.notify_layout_v18_samll_night
        }

        return RemoteViews(context.packageName, layoutRes).apply {
            bindCommonActions(content)
            bindAlbumArt(
                albumArt = albumArt,
                fallbackRes = content.getDefaultAlbumArtRes(
                    viewType = VIEW_TYPE_COLLAPSED,
                    useNightArtwork = false,
                ),
            )

            setImageViewResource(
                R.id.notify_play_pause,
                if (content.isPlaying()) {
                    R.drawable.notify_pause_new
                } else {
                    R.drawable.notify_play_new
                },
            )

            setImageViewResource(
                R.id.notify_favorite,
                if (content.isFavorite()) {
                    R.drawable.notify_favorite_new
                } else {
                    R.drawable.notify_unfavorite_new
                },
            )

            setViewVisibility(
                R.id.notify_desk_lrc,
                if (content.isDesktopLyricsEnabled()) View.VISIBLE else View.GONE,
            )

            setTextViewText(R.id.notify_text, content.getTitle())
            setTextViewText(R.id.notify_msg, "(${content.getArtistName()})")
            setInt(R.id.notify_background, "setBackgroundColor", colorStyle.backgroundColor)

            applyCollapsedTheme(
                useLightTheme = colorStyle.useLightTheme,
                favorite = content.isFavorite(),
            )
        }
    }

    private fun RemoteViews.bindCommonActions(
        content: MusicNotificationContent,
    ) {
        setOnClickPendingIntent(R.id.notify_previous, content.createPreviousIntent(context))
        setOnClickPendingIntent(R.id.notify_play_pause, content.createPlayPauseIntent(context))
        setOnClickPendingIntent(R.id.notify_next, content.createNextIntent(context))
        setOnClickPendingIntent(R.id.notify_exit, content.createStopIntent(context))
        setOnClickPendingIntent(R.id.notify_favorite, content.createFavoriteIntent(context))
        setOnClickPendingIntent(R.id.notify_desk_lrc, content.createDesktopLyricsIntent(context))
    }

    private fun RemoteViews.bindAlbumArt(
        albumArt: NotificationAlbumArtwork,
        fallbackRes: Int,
    ) {
        if (albumArt.hasDisplayBitmap) {
            setImageViewBitmap(R.id.notify_image, albumArt.displayBitmap)
        } else {
            setImageViewResource(R.id.notify_image, fallbackRes)
        }
    }

    private fun RemoteViews.applyExpandedTheme(
        useLightTheme: Boolean,
        favorite: Boolean,
    ) {
        if (useLightTheme) {
            setTextColor(R.id.notify_text, COLOR_LIGHT_PRIMARY_TEXT)
            setTextColor(R.id.notify_msg, COLOR_LIGHT_SECONDARY_TEXT)
            setTextColor(R.id.notify_extra, COLOR_LIGHT_SECONDARY_TEXT)

            setDividerColors(COLOR_LIGHT_DIVIDER)
            setControlIconColor(COLOR_LIGHT_PRIMARY_TEXT)

            setInt(
                R.id.notify_favorite,
                "setColorFilter",
                if (favorite) COLOR_FAVORITE else COLOR_LIGHT_PRIMARY_TEXT,
            )
        } else {
            setTextColor(R.id.notify_text, COLOR_DARK_PRIMARY_TEXT)
            setTextColor(R.id.notify_msg, COLOR_DARK_SECONDARY_TEXT)
            setTextColor(R.id.notify_extra, COLOR_DARK_SECONDARY_TEXT)

            setDividerColors(COLOR_DARK_DIVIDER)
            setControlIconColor(COLOR_DARK_PRIMARY_TEXT)

            setInt(
                R.id.notify_favorite,
                "setColorFilter",
                if (favorite) COLOR_FAVORITE else COLOR_DARK_PRIMARY_TEXT,
            )
        }
    }

    private fun RemoteViews.applyCollapsedTheme(
        useLightTheme: Boolean,
        favorite: Boolean,
    ) {
        if (useLightTheme) {
            setTextColor(R.id.notify_text, COLOR_LIGHT_PRIMARY_TEXT)
            setTextColor(R.id.notify_msg, COLOR_LIGHT_SECONDARY_TEXT)
            setControlIconColor(COLOR_LIGHT_PRIMARY_TEXT)

            setInt(
                R.id.notify_favorite,
                "setColorFilter",
                if (favorite) COLOR_FAVORITE else COLOR_LIGHT_PRIMARY_TEXT,
            )
        } else {
            setTextColor(R.id.notify_text, COLOR_DARK_PRIMARY_TEXT)
            setTextColor(R.id.notify_msg, COLOR_DARK_SECONDARY_TEXT)
            setControlIconColor(COLOR_DARK_PRIMARY_TEXT)

            setInt(
                R.id.notify_favorite,
                "setColorFilter",
                if (favorite) COLOR_FAVORITE else COLOR_DARK_PRIMARY_TEXT,
            )
        }
    }

    private fun RemoteViews.setDividerColors(color: Int) {
        setInt(R.id.notify_line_0, "setBackgroundColor", color)
        setInt(R.id.notify_line_1, "setBackgroundColor", color)
        setInt(R.id.notify_line_2, "setBackgroundColor", color)
        setInt(R.id.notify_line_3, "setBackgroundColor", color)
    }

    private fun RemoteViews.setControlIconColor(color: Int) {
        setInt(R.id.notify_previous, "setColorFilter", color)
        setInt(R.id.notify_play_pause, "setColorFilter", color)
        setInt(R.id.notify_next, "setColorFilter", color)
        setInt(R.id.notify_exit, "setColorFilter", color)
        setInt(R.id.notify_desk_lrc, "setColorFilter", color)
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < 26) return

        val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (existingChannel != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setShowBadge(false)
        }

        notificationManager.createNotificationChannel(channel)
    }

    private fun NotificationCompat.Builder.setChannelIdIfNeeded(): NotificationCompat.Builder {
        return if (Build.VERSION.SDK_INT >= 26) {
            setChannelId(CHANNEL_ID)
        } else {
            setPriority(NotificationCompat.PRIORITY_DEFAULT)
        }
    }

    companion object {
        private const val VIEW_TYPE_COLLAPSED = 0
        private const val VIEW_TYPE_EXPANDED = 1

        private const val COLOR_FAVORITE = -514295

        private const val COLOR_LIGHT_PRIMARY_TEXT = -570425344
        private const val COLOR_LIGHT_SECONDARY_TEXT = -1979711488
        private const val COLOR_LIGHT_DIVIDER = 436207616

        private const val COLOR_DARK_PRIMARY_TEXT = -1
        private const val COLOR_DARK_SECONDARY_TEXT = -1275068417
        private const val COLOR_DARK_DIVIDER = 452984831
    }
}
