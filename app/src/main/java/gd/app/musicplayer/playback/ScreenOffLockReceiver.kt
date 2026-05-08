package gd.app.musicplayer.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import gd.app.musicplayer.ui.feature.lock.LockActivity

class ScreenOffLockReceiver(
    private val hasCurrentMusic: () -> Boolean,
    private val isLockScreenEnabled: () -> Boolean
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_SCREEN_OFF) return
        if (!hasCurrentMusic()) return
        if (!isLockScreenEnabled()) return
        LockActivity.start(context.applicationContext)
    }
}
