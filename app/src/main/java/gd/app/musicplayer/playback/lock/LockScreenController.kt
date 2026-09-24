package gd.app.musicplayer.playback.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.core.datastore.defaultLockScreenEnabled
import gd.app.musicplayer.feature.lock.LockActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lock-screen playing controller matching Music Player 8.1.5 `y6.j0`.
 *
 * - Registers [Intent.ACTION_SCREEN_ON] only while the lock-screen preference is on.
 * - Arms from play-state ([setPlaying]); opens [LockActivity] on wake when armed.
 */
@Singleton
class LockScreenController @Inject constructor(
    @param:ApplicationContext private val appContext: Context
) {
    @Volatile
    private var armedPlaying: Boolean = false

    private var screenOnReceiver: BroadcastReceiver? = null

    private val lock = Any()

    fun setPlaying(playing: Boolean) {
        armedPlaying = playing
    }

    /**
     * Unregister then register when [lockScreenEnabled] — original `j0.c()`.
     */
    fun refresh(lockScreenEnabled: Boolean) {
        synchronized(lock) {
            unregisterLocked()
            if (lockScreenEnabled) {
                registerLocked()
            }
        }
    }

    fun unregister() {
        synchronized(lock) {
            unregisterLocked()
        }
    }

    private fun registerLocked() {
        if (screenOnReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                if (intent?.action != Intent.ACTION_SCREEN_ON) return
                if (!armedPlaying) return
                LockActivity.start(appContext)
            }
        }

        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                receiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(receiver, filter)
        }
        screenOnReceiver = receiver
    }

    private fun unregisterLocked() {
        val receiver = screenOnReceiver ?: return
        runCatching {
            appContext.unregisterReceiver(receiver)
        }
        screenOnReceiver = null
    }

    companion object {
        /** Original `w7.v.y0` default: enabled only below API 29. */
        fun defaultEnabled(): Boolean = defaultLockScreenEnabled()
    }
}
