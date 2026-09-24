package gd.app.musicplayer.playback.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import gd.app.musicplayer.R
import java.lang.ref.SoftReference

/**
 * Modern notification — original [z6.d]:
 * MediaStyle only (no custom RemoteViews), actions
 * favorite / prev / play-pause / next / stop, compact indices 1,2,3.
 */
class MediaStyleMusicNotificationBuilder(
    context: Context,
    shouldUseDynamicColors: Boolean,
) : AlbumColorRemoteViewsNotificationBuilder(
    context = context,
    shouldUseDynamicColors = shouldUseDynamicColors,
) {
    private var cachedDefaultAlbumBitmap: SoftReference<Bitmap>? = null

    override fun buildNotification(content: MusicNotificationContent): Notification {
        createNotificationChannelIfNeeded()
        val art = content.getAlbumArt(1)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(content.getSmallIconRes())
            .setContentTitle(content.getTitle())
            .setContentText("${content.getArtistName()}-${content.getAlbumName()}")
            .setContentIntent(content.createContentIntent(context))
            .setDeleteIntent(content.createStopIntent(context))
            .setOngoing(content.isPlaying())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setLargeIcon(
                if (art.hasDisplayBitmap) art.displayBitmap
                else getDefaultAlbumBitmap(content.getDefaultAlbumArtRes(1, false))
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    if (content.isFavorite()) R.drawable.notify_favorite_new
                    else R.drawable.notify_unfavorite_new,
                    if (content.isFavorite()) "UNFAVORITE" else "FAVORITE",
                    content.createFavoriteIntent(context),
                ).build(),
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.notify_previous_new,
                    "PREVIOUS",
                    content.createPreviousIntent(context),
                ).build(),
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    if (content.isPlaying()) R.drawable.notify_pause_new
                    else R.drawable.notify_play_new,
                    if (content.isPlaying()) "PAUSE" else "PLAY",
                    content.createPlayPauseIntent(context),
                ).build(),
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.notify_next_new,
                    "NEXT",
                    content.createNextIntent(context),
                ).build(),
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.notify_close_new,
                    "STOP",
                    content.createStopIntent(context),
                ).build(),
            )

        // Original z6.d: skip colorize on Samsung API 28
        if (!(isSamsungDevice() && Build.VERSION.SDK_INT == 28)) {
            builder.setColor(resolveColorStyle(art).backgroundColor)
            builder.setColorized(true)
        }

        val mediaStyle = MediaStyle()
            .setShowActionsInCompactView(
                ACTION_INDEX_PREVIOUS,
                ACTION_INDEX_PLAY_PAUSE,
                ACTION_INDEX_NEXT
            )
            .setCancelButtonIntent(content.createStopIntent(context))
            .setShowCancelButton(true)

        content.getMediaSessionToken()?.let { token ->
            mediaStyle.setMediaSession(MediaSessionCompat.Token.fromToken(token))
        }
        builder.setStyle(mediaStyle)

        return builder.build()
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun getDefaultAlbumBitmap(drawableRes: Int): Bitmap {
        cachedDefaultAlbumBitmap?.get()?.takeIf { !it.isRecycled }?.let { return it }
        val drawable = ContextCompat.getDrawable(context, drawableRes)
        val bitmap = if (drawable is BitmapDrawable) drawable.bitmap
        else BitmapFactory.decodeResource(context.resources, drawableRes)
        cachedDefaultAlbumBitmap = SoftReference(bitmap)
        return bitmap
    }

    private fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    private companion object {
        // Original z6.d action order: 0 fav, 1 prev, 2 play/pause, 3 next, 4 stop
        // Compact l(1, 2, 3) = prev / play-pause / next
        const val ACTION_INDEX_PREVIOUS = 1
        const val ACTION_INDEX_PLAY_PAUSE = 2
        const val ACTION_INDEX_NEXT = 3
    }
}
