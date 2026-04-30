package gd.app.musicplayer.playback.notification

import android.content.Context
import android.os.Build
import gd.app.musicplayer.core.extension.appDependencies

object MusicNotificationBuilderFactory {

    fun create(
        context: Context,
        shouldUseDynamicColors: Boolean,
    ): BaseMusicNotificationBuilder {
        val preferenceUtil = context.appDependencies.preferenceUtil
        val oldNotificationEnabled = preferenceUtil.getBooleanPreference(
            KEY_OLD_NOTIFICATION,
            false,
        )
        val shouldUseLegacyRemoteViews =
            !supportsModernMediaStyleNotification() || oldNotificationEnabled
//        val shouldUseLegacyRemoteViews = false
        return if (shouldUseLegacyRemoteViews) {
            if (preferenceUtil.getBooleanPreference(KEY_COLOR_NOTIFICATION, true)) {
                AlbumColorRemoteViewsNotificationBuilder(
                    context = context,
                    shouldUseDynamicColors = shouldUseDynamicColors,
                )
            } else {
                LegacyRemoteViewsNotificationBuilder(
                    context = context,
                    shouldUseDynamicColors = shouldUseDynamicColors,
                )
            }
        } else {
            MediaStyleMusicNotificationBuilder(
                context = context,
                shouldUseDynamicColors = shouldUseDynamicColors,
            )
        }
    }

    private fun supportsModernMediaStyleNotification(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }

    private const val KEY_OLD_NOTIFICATION = "old_notification"
    private const val KEY_COLOR_NOTIFICATION = "color_notification"
}
