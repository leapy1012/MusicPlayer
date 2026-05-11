package gd.app.musicplayer.ui.shortcut

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.annotation.DrawableRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

object AppShortcutManager {

    fun isPinShortcutSupported(context: Context): Boolean {
        return ShortcutManagerCompat.isRequestPinShortcutSupported(context)
    }

    fun addDynamicShortcuts(
        context: Context,
        shortcuts: List<ShortcutInfoCompat>
    ): Boolean {
        if (shortcuts.isEmpty()) return true
        return ShortcutManagerCompat.addDynamicShortcuts(context, shortcuts)
    }

    fun getDynamicShortcuts(context: Context): List<ShortcutInfoCompat> {
        return ShortcutManagerCompat.getDynamicShortcuts(context)
    }

    fun pushDynamicShortcut(
        context: Context,
        shortcut: ShortcutInfoCompat
    ): Boolean {
        return ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    fun removeDynamicShortcuts(
        context: Context,
        shortcutIds: List<String>
    ) {
        if (shortcutIds.isEmpty()) return
        ShortcutManagerCompat.removeDynamicShortcuts(context, shortcutIds)
    }

    fun removeAllDynamicShortcuts(context: Context) {
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
    }

    fun requestPinnedShortcut(
        context: Context,
        shortcut: ShortcutInfoCompat,
        callback: IntentSender? = null
    ): Boolean {
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, callback)
    }

    fun buildShortcut(
        context: Context,
        id: String,
        shortLabel: String,
        intent: Intent,
        @DrawableRes iconResId: Int,
        longLabel: String? = null,
        disabledMessage: String? = null
    ): ShortcutInfoCompat {
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(shortLabel)
            .setIntent(intent)
            .setIcon(IconCompat.createWithResource(context, iconResId))
            .apply {
                if (!longLabel.isNullOrBlank()) {
                    setLongLabel(longLabel)
                }
                if (!disabledMessage.isNullOrBlank()) {
                    setDisabledMessage(disabledMessage)
                }
            }
            .build()
    }
}
