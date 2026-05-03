package gd.app.musicplayer.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import gd.app.musicplayer.ui.feature.lock.LockActivity
import gd.app.musicplayer.util.PreferenceUtil

class ScreenOffLockReceiver(
    private val hasCurrentMusic: () -> Boolean,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_SCREEN_OFF) return
        if (!hasCurrentMusic()) return
        if (!PreferenceUtil.getInstance(context).isLockScreenEnabled(false)) return
        LockActivity.start(context.applicationContext)
    }
}
