package gd.app.musicplayer.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

internal class WidgetAddHelper(
    private val activity: AppCompatActivity,
    private val onAddSuccess: () -> Unit,
    private val onManualAddRequired: () -> Unit
) {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_WIDGET_ADD_SUCCESS) {
                onAddSuccess()
            }
        }
    }

    private var receiverRegistered = false

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
            item.providerClass.name.hashCode(),
            successIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun shouldUseManualAddFlow(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        val display = Build.DISPLAY.orEmpty().lowercase()

        return manufacturer in blockedManufacturers ||
            brand in blockedManufacturers ||
            display.contains("flyme")
    }

    private companion object {
        const val ACTION_WIDGET_ADD_SUCCESS = "gd.app.musicplayer.action.WIDGET_ADD_SUCCESS"

        val blockedManufacturers = setOf(
            "xiaomi",
            "vivo",
            "coolpad",
            "oppo"
        )
    }
}
