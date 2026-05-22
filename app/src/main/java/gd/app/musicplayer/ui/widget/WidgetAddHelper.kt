package gd.app.musicplayer.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.ui.common.base.BaseActivity

internal class WidgetAddHelper(
    private val activity: BaseActivity,
    private val onManualAddRequired: () -> Unit
) {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_WIDGET_ADD_SUCCESS) {
                ToastUtil.show(context, R.string.dlg_add_widget_success)
            }
        }
    }

    private var receiverRegistered = false

    fun register() {
        ensureReceiverRegistered()
    }

    fun requestAdd(item: WidgetProviderSpec) {
        if (shouldUseManualAddFlow()) {
            onManualAddRequired()
            return
        }

        val manager = AppWidgetManager.getInstance(activity)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !manager.isRequestPinAppWidgetSupported) {
            onManualAddRequired()
            return
        }

        ensureReceiverRegistered()

        val successIntent = Intent(ACTION_WIDGET_ADD_SUCCESS).setPackage(activity.packageName)
        val successCallback = PendingIntent.getBroadcast(
            activity,
            System.currentTimeMillis().toInt(),
            successIntent,
            pendingIntentFlags()
        )

        val pinned = manager.requestPinAppWidget(
            ComponentName(activity, item.providerClass),
            null,
            successCallback
        )

        if (!pinned) {
            onManualAddRequired()
        }
    }

    fun dispose() {
        if (!receiverRegistered) return
        activity.unregisterReceiver(receiver)
        receiverRegistered = false
    }

    private fun ensureReceiverRegistered() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            activity,
            receiver,
            IntentFilter(ACTION_WIDGET_ADD_SUCCESS),
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }

    private fun shouldUseManualAddFlow(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        return blockedManufacturers.any(manufacturer::contains)
    }

    private fun pendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    private companion object {
        const val ACTION_WIDGET_ADD_SUCCESS = "gd.app.musicplayer.action_widget_add_success"

        val blockedManufacturers = setOf(
            "xiaomi",
            "flyme",
            "vivo",
            "coolpad",
            "oppo"
        )
    }
}
