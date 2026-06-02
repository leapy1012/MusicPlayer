package gd.app.musicplayer.playback.service

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

internal class PlaybackJobState {
    var resumeJob: Job? = null
    var defaultQueueRestoreJob: Job? = null
    var defaultTracksObserverJob: Job? = null
    var widgetUpdateJob: Job? = null
    var notificationCoalesceJob: Job? = null

    fun scheduleNotificationUpdate(
        scope: CoroutineScope,
        force: Boolean,
        delayMs: Long,
        onUpdate: (force: Boolean) -> Unit
    ) {
        if (force) {
            notificationCoalesceJob?.cancel()
            onUpdate(true)
            return
        }

        notificationCoalesceJob?.cancel()
        notificationCoalesceJob = scope.launch {
            delay(delayMs)
            onUpdate(false)
        }
    }

    fun cancelAndClearAll() {
        resumeJob?.cancel()
        resumeJob = null

        defaultQueueRestoreJob?.cancel()
        defaultQueueRestoreJob = null

        defaultTracksObserverJob?.cancel()
        defaultTracksObserverJob = null

        widgetUpdateJob?.cancel()
        widgetUpdateJob = null

        notificationCoalesceJob?.cancel()
        notificationCoalesceJob = null
    }
}
