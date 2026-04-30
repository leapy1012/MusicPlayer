package gd.app.musicplayer.playback.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build

abstract class BaseMusicNotificationBuilder(
    protected val context: Context,
    private val shouldUseDynamicColors: Boolean,
) {
    protected val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    abstract fun buildNotification(
        content: MusicNotificationContent,
    ): Notification

    protected open fun resolveColorStyle(
        albumArt: NotificationAlbumArtwork,
    ): NotificationColorStyle {
        if (!shouldUseDynamicColors) {
            return NotificationColorStyle(
                backgroundColor = COLOR_LIGHT_BACKGROUND,
                useLightTheme = true,
            )
        }
        return NotificationColorStyle(
            backgroundColor = if (Build.VERSION.SDK_INT == 28 && isSamsungDevice()) {
                COLOR_ANDROID_9_SAMSUNG_BACKGROUND
            } else {
                COLOR_TRANSPARENT
            },
            useLightTheme = false,
        )
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun show(content: MusicNotificationContent) {
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(content),
        )
    }

    companion object {

        const val NOTIFICATION_ID = 123321469
        const val CHANNEL_ID = "audio_play_channel"
        const val CHANNEL_NAME = "AudioPlayerNotification"

        private const val COLOR_TRANSPARENT = 0
        private const val COLOR_LIGHT_BACKGROUND = -657931
        private const val COLOR_ANDROID_9_SAMSUNG_BACKGROUND = -14277082

        fun create(
            context: Context,
            shouldUseDynamicColors: Boolean,
        ): BaseMusicNotificationBuilder {
            return MusicNotificationBuilderFactory.create(context, shouldUseDynamicColors)
        }

        private fun isSamsungDevice(): Boolean {
            return Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        }
    }
}
