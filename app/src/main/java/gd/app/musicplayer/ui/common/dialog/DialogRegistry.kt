package gd.app.musicplayer.ui.common.dialog

import android.app.Activity
import java.util.concurrent.ConcurrentHashMap

internal object DialogRegistry {

    private val dialogs = ConcurrentHashMap<String, BaseDialog>()
    private var bulkDismissInProgress = false

    fun register(dialog: BaseDialog) {
        dialogs[dialog.dialogKey] = dialog
    }

    fun unregister(dialogKey: String) {
        if (!bulkDismissInProgress) {
            dialogs.remove(dialogKey)
        }
    }

    fun dismiss(dialogKey: String) {
        val dialog = dialogs.remove(dialogKey) ?: return
        runCatching { dialog.dismiss() }
            .onFailure {  }
    }

    fun dismiss(activity: Activity, config: BaseDialog.Config) {
        dismiss(config.cacheKey(activity))
    }

    fun dismissAll() {
        if (dialogs.isEmpty()) return

        try {
            bulkDismissInProgress = true
            dialogs.values.toList().forEach { dialog ->
                runCatching { dialog.dismiss() }
                    .onFailure {  }
            }
        } finally {
            bulkDismissInProgress = false
        }
    }

    fun dismissAll(activity: Activity) {
        if (dialogs.isEmpty()) return

        try {
            bulkDismissInProgress = true
            val activityKeyPrefix = activity.toString()

            dialogs.entries
                .filter { it.key.startsWith(activityKeyPrefix) }
                .map { it.value }
                .toList()
                .forEach { dialog ->
                    runCatching { dialog.dismiss() }
                        .onFailure {  }
                }
        } finally {
            bulkDismissInProgress = false
        }
    }
}