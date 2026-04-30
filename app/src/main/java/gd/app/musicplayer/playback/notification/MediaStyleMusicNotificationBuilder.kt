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
import gd.app.musicplayer.playback.MusicPlaybackService
import java.lang.ref.SoftReference

class MediaStyleMusicNotificationBuilder(
    context: Context,
    shouldUseDynamicColors: Boolean,
) : AlbumColorRemoteViewsNotificationBuilder(
    context = context,
    shouldUseDynamicColors = shouldUseDynamicColors,
) {
    private var cachedDefaultAlbumBitmap: SoftReference<Bitmap>? = null
    private var cachedBuilder: MutableNotificationBuilder? = null

    override fun buildNotification(content: MusicNotificationContent): Notification {
        android.util.Log.e("Leapy", "buildNotification " + content.isFavorite() + ":" + content.getTitle())
        createNotificationChannelIfNeeded()
        val art = content.getAlbumArt(1)
        val builder = ensureBuilder(content)
        builder
            .setContentTitle(content.getTitle())
            .setContentText("${content.getArtistName()}-${content.getAlbumName()}")
            .setOngoing(content.isPlaying())
            .setLargeIcon(
                if (art.hasDisplayBitmap) art.displayBitmap
                else getDefaultAlbumBitmap(content.getDefaultAlbumArtRes(1, false))
            )

        builder.replaceAction(
            ACTION_INDEX_FAVORITE,
            NotificationCompat.Action.Builder(
                if (content.isFavorite()) R.drawable.notify_favorite_new else R.drawable.notify_unfavorite_new,
                if (content.isFavorite()) "FAVORITE" else "UNFAVORITE",
                content.createFavoriteIntent(context),
            ).build(),
        )
        builder.replaceAction(
            ACTION_INDEX_PLAY_PAUSE,
            NotificationCompat.Action.Builder(
                if (content.isPlaying()) R.drawable.notify_pause_new else R.drawable.notify_play_new,
                if (content.isPlaying()) "PAUSE" else "PLAY",
                content.createPlayPauseIntent(context),
            ).build(),
        )

        val mediaStyle = MediaStyle()
            .setShowActionsInCompactView(1, 2, 3)
        content.getMediaSessionToken()?.let { token ->
            mediaStyle.setMediaSession(MediaSessionCompat.Token.fromToken(token))
        }
        builder.setStyle(mediaStyle)

        val skipColorizeForVivoApi28 =
            Build.VERSION.SDK_INT == Build.VERSION_CODES.P &&
                Build.MANUFACTURER.contains("vivo", ignoreCase = true)
        if (!skipColorizeForVivoApi28) {
            builder.setColor(resolveColorStyle(art).backgroundColor)
            builder.setColorized(true)
        }
        return builder.build()
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun ensureBuilder(content: MusicNotificationContent): MutableNotificationBuilder {
        cachedBuilder?.let { return it }
        val builder = MutableNotificationBuilder(context, CHANNEL_ID)
        builder.setSmallIcon(content.getSmallIconRes())
        builder.setContentIntent(content.createContentIntent(context))
        builder.setDeleteIntent(content.createStopIntent(context))
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        builder.setOnlyAlertOnce(true)
        builder.setAutoCancel(false)
        builder.setShowWhen(false)
        builder.setCategory(NotificationCompat.CATEGORY_SERVICE)
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
        builder.addAction(
            NotificationCompat.Action.Builder(
                if (content.isFavorite()) R.drawable.notify_favorite_new else R.drawable.notify_unfavorite_new,
                "UNFAVORITE",
                content.createFavoriteIntent(context),
            ).build(),
        )
        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.notify_previous_new,
                "PREVIOUS",
                content.createPreviousIntent(context),
            ).build(),
        )
        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.notify_play_new,
                "PLAY",
                content.createPlayPauseIntent(context),
            ).build(),
        )
        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.notify_next_new,
                "NEXT",
                content.createNextIntent(context),
            ).build(),
        )
        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.notify_close_new,
                "STOP",
                content.createStopIntent(context),
            ).build(),
        )
        cachedBuilder = builder
        return builder
    }

    private fun getDefaultAlbumBitmap(drawableRes: Int): Bitmap {
        cachedDefaultAlbumBitmap?.get()?.takeIf { !it.isRecycled }?.let { return it }
        val drawable = ContextCompat.getDrawable(context, drawableRes)
        val bitmap = if (drawable is BitmapDrawable) drawable.bitmap
        else BitmapFactory.decodeResource(context.resources, drawableRes)
        cachedDefaultAlbumBitmap = SoftReference(bitmap)
        return bitmap
    }

    private class MutableNotificationBuilder(
        context: Context,
        channelId: String,
    ) : NotificationCompat.Builder(context, channelId) {
        fun replaceAction(index: Int, action: NotificationCompat.Action) {
            if (index in mActions.indices) {
                mActions[index] = action
            }
        }
    }

    private companion object {
        const val ACTION_INDEX_FAVORITE = 0
        const val ACTION_INDEX_PLAY_PAUSE = 2

    }
}
