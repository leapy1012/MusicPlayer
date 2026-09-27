package gd.app.musicplayer.core.designsystem.dialog

import android.app.Activity
import android.content.DialogInterface
import java.util.concurrent.ConcurrentHashMap

internal object DialogRegistry {

    private val dialogs = ConcurrentHashMap<String, BaseDialog>()
    private val alertDialogs = ConcurrentHashMap<String, DialogInterface>()
    private var bulkDismissInProgress = false

    fun register(dialog: BaseDialog) {
        dialogs[dialog.dialogKey] = dialog
    }

    fun unregister(dialogKey: String) {
        if (!bulkDismissInProgress) {
            dialogs.remove(dialogKey)
        }
    }

    fun registerAlert(activity: Activity, dialog: DialogInterface) {
        alertDialogs[alertKey(activity, dialog)] = dialog
    }

    fun unregisterAlert(activity: Activity, dialog: DialogInterface) {
        if (!bulkDismissInProgress) {
            alertDialogs.remove(alertKey(activity, dialog))
        }
    }

    fun dismiss(dialogKey: String) {
        val dialog = dialogs.remove(dialogKey) ?: return
        runCatching { dialog.dismiss() }
    }

    fun dismiss(activity: Activity, config: BaseDialog.Config) {
        dismiss(config.cacheKey(activity))
    }

    fun dismissAll() {
        dismissTrackedDialogs(dialogs.values.toList(), alertDialogs.values.toList())
    }

    fun dismissAll(activity: Activity) {
        val activityKeyPrefix = activity.toString()
        val baseMatches = dialogs.entries
            .filter { it.key.startsWith(activityKeyPrefix) }
            .map { it.value }
        val alertMatches = alertDialogs.entries
            .filter { it.key.startsWith(activityKeyPrefix) }
            .map { it.value }
        dismissTrackedDialogs(baseMatches, alertMatches)
    }

    private fun dismissTrackedDialogs(
        base: Collection<BaseDialog>,
        alerts: Collection<DialogInterface>
    ) {
        if (base.isEmpty() && alerts.isEmpty()) return
        try {
            bulkDismissInProgress = true
            base.forEach { dialog ->
                runCatching { dialog.dismiss() }
            }
            alerts.forEach { dialog ->
                runCatching { dialog.dismiss() }
            }
        } finally {
            bulkDismissInProgress = false
            dialogs.keys.removeAll { key -> base.any { it.dialogKey == key } }
            alertDialogs.keys.removeAll { key ->
                alerts.any { alert -> key.endsWith(alert.hashCode().toString()) }
            }
            // Safer cleanup: drop dismissed entries
            dialogs.entries.removeIf { !it.value.isShowing }
            alertDialogs.clear()
        }
    }

    private fun alertKey(activity: Activity, dialog: DialogInterface): String {
        return "${activity}_${dialog.hashCode()}"
    }
}
