package gd.app.musicplayer.playback.notification

class NotificationCloseController(
    private val callbacks: NotificationCloseCallbacks
) {

    fun pauseAndCloseNotification() {
        val snapshot = callbacks.capturePlaybackSnapshot()

        callbacks.setNotificationDismissedByUser(true)

        if (
            snapshot.queue.isNotEmpty() &&
            snapshot.currentIndex in snapshot.queue.indices
        ) {
            callbacks.syncQueueFromSnapshot(snapshot)
        }

        callbacks.pausePlayerIfNeeded()

        callbacks.persistPlaybackSnapshotBlocking(
            snapshot = snapshot,
            persistQueue = true
        )

        callbacks.updateNotificationSessionPlaybackState()

        callbacks.publishPausedSnapshot(snapshot)

        callbacks.removeNotification()
    }
}
