package gd.app.musicplayer.core.datastore

import android.view.Gravity

data class StatusBarLyricPreference(
    val xRatio: Float = StatusBarLyricPreferenceStore.DEFAULT_X,
    val yRatio: Float = StatusBarLyricPreferenceStore.UNSET_Y,
    val widthRatio: Float = StatusBarLyricPreferenceStore.DEFAULT_WIDTH,
    val fontSizeRatio: Float = StatusBarLyricPreferenceStore.DEFAULT_FONT_SIZE,
    val alphaRatio: Float = StatusBarLyricPreferenceStore.DEFAULT_ALPHA,

    val enabled: Boolean = false,
    val contentType: Int = StatusBarLyricPreferenceStore.CONTENT_TYPE_LYRIC,
    val gravity: Int = Gravity.CENTER,
    val clickable: Boolean = false,
    val showPaused: Boolean = false,

    val textColor: Int = 0,
    val pendingEnableAfterPermission: Boolean = false
)