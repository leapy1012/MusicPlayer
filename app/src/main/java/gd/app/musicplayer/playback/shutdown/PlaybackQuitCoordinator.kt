package gd.app.musicplayer.playback.shutdown

import android.os.Process
import gd.app.musicplayer.core.common.AppForegroundTracker
import gd.app.musicplayer.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import javax.inject.Inject
import javax.inject.Singleton

/** Original t5.a.b policy: explicit Quit waits for activity and playback-service destruction. */
@Singleton
class PlaybackQuitCoordinator @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private var serviceOwner: Any? = null
    private var serviceExit: (() -> Unit)? = null
    var isQuitRequested = false
        private set

    init {
        scope.launch {
            AppForegroundTracker.activeActivityCount.collect { finishQuitIfReady() }
        }
    }

    fun onServiceCreated(owner: Any, exit: () -> Unit) {
        serviceOwner = owner
        serviceExit = exit
        if (isQuitRequested) exit()
    }

    fun onServiceDestroyed(owner: Any) {
        if (serviceOwner === owner) {
            serviceOwner = null
            serviceExit = null
        }
        finishQuitIfReady()
    }

    fun requestQuit() {
        if (isQuitRequested) return
        isQuitRequested = true
        AppForegroundTracker.finishAllActivitiesAndRemoveTasks()
        serviceExit?.invoke()
        finishQuitIfReady()
    }

    private fun finishQuitIfReady() {
        if (isQuitRequested && serviceOwner == null && AppForegroundTracker.activeActivityCount.value == 0) {
            // Saving and service cleanup have finished; no elapsed-time assumption.
            Process.killProcess(Process.myPid())
        }
    }
}
