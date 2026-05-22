package gd.app.musicplayer.playback.notification

import android.content.Context
import android.os.Build
import gd.app.musicplayer.core.datastore.NotificationSettingPreference

object MusicNotificationBuilderFactory {

    fun create(
        context: Context,
        shouldUseDynamicColors: Boolean,
        notificationSettings: NotificationSettingPreference
    ): BaseMusicNotificationBuilder {
        return when {
            shouldUseLegacyRemoteViews(notificationSettings) &&
                    notificationSettings.colorNotificationEnabled -> {
                AlbumColorRemoteViewsNotificationBuilder(
                    context = context,
                    shouldUseDynamicColors = shouldUseDynamicColors
                )
            }

            shouldUseLegacyRemoteViews(notificationSettings) -> {
                LegacyRemoteViewsNotificationBuilder(
                    context = context,
                    shouldUseDynamicColors = shouldUseDynamicColors
                )
            }

            else -> {
                MediaStyleMusicNotificationBuilder(
                    context = context,
                    shouldUseDynamicColors = shouldUseDynamicColors
                )
            }
        }
    }

    private fun shouldUseLegacyRemoteViews(
        notificationSettings: NotificationSettingPreference
    ): Boolean {
        return !supportsModernMediaStyleNotification() ||
                notificationSettings.oldNotificationEnabled
    }

    private fun supportsModernMediaStyleNotification(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }
}