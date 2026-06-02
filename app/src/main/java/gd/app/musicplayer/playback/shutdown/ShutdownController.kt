package gd.app.musicplayer.playback.shutdown

class ShutdownController(
    private val callbacks: ShutdownCallbacks
) {

    fun shutdown(options: ShutdownOptions) {
        val shutdownSnapshot = callbacks.capturePlaybackSnapshot()

        if (!options.clearQueue && options.persistQueueSnapshotWhenKeepingQueue) {
            callbacks.persistForSnapshotPolicy(
                snapshot = shutdownSnapshot,
                persistQueue = true
            )
        }

        callbacks.resetPlaybackStatistics()
        callbacks.resetTimedTransition()
        callbacks.cancelVolumeFade()
        callbacks.stopAndClearPlayer()
        callbacks.abandonAudioFocus()

        if (options.clearQueue) {
            callbacks.clearArtworkState()
            callbacks.clearQueueState()
            callbacks.clearPersistedPlaybackState()

            if (options.clearPersistedQueue) {
                callbacks.clearPersistedQueue()
            }

            callbacks.clearNotificationSession()
            callbacks.markRestoreEmpty()
        }

        if (options.clearRuntimeState) {
            callbacks.resetRuntimeState()
        } else {
            callbacks.publishStateAfterShutdown(shutdownSnapshot)
        }

        if (options.removeNotification) {
            callbacks.removeNotification()
        }

        if (options.stopService) {
            callbacks.onServiceShouldStop()
        }
    }
}
