package gd.app.musicplayer.ui.feature.shortcut

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.annotation.DrawableRes
import androidx.core.content.pm.ShortcutInfoCompat
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.ui.common.base.BaseActivity

object MusicSetShortcutHelper {

    const val EXTRA_SOURCE = "shortcut_source"
    const val EXTRA_MUSIC_SET_ID = "shortcut_music_set_id"

    private const val SOURCE_MUSIC_SET = "music_set"

    fun isPinShortcutSupported(context: Context): Boolean {
        return AppShortcutManager.isPinShortcutSupported(context)
    }

    fun buildShortcut(
        context: Context,
        musicSet: MusicSet,
        title: String,
        @DrawableRes iconResId: Int = R.drawable.ic_location
    ): ShortcutInfoCompat {
        val launchIntent = Intent(context, BaseActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SOURCE, SOURCE_MUSIC_SET)
            putExtra(EXTRA_MUSIC_SET_ID, musicSet.id)
        }

        return AppShortcutManager.buildShortcut(
            context = context,
            id = "music_set_${musicSet.id}",
            shortLabel = title,
            longLabel = title,
            intent = launchIntent,
            iconResId = iconResId
        )
    }

    fun requestPinnedShortcut(
        context: Context,
        musicSet: MusicSet,
        title: String,
        @DrawableRes iconResId: Int = R.drawable.ic_location,
        callback: IntentSender? = null
    ): Boolean {
        val shortcut = buildShortcut(
            context = context,
            musicSet = musicSet,
            title = title,
            iconResId = iconResId
        )
        return AppShortcutManager.requestPinnedShortcut(context, shortcut, callback)
    }

    fun extractMusicSetId(intent: Intent?): Long? {
        if (intent?.getStringExtra(EXTRA_SOURCE) != SOURCE_MUSIC_SET) return null
        if (intent.hasExtra(EXTRA_MUSIC_SET_ID).not()) return null
        return intent.getLongExtra(EXTRA_MUSIC_SET_ID, Long.MIN_VALUE)
            .takeUnless { it == Long.MIN_VALUE }
    }
}
