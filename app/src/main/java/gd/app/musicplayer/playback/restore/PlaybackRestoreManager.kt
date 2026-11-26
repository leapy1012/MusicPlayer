package gd.app.musicplayer.playback.restore

import gd.app.musicplayer.playback.state.PlaybackRuntimeStateStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlaybackRestoreManager(
    private val resumeLoader: PlaybackResumeLoader,
    private val playbackRuntimeStateStore: PlaybackRuntimeStateStore
) {

    private val restoreMutex = Mutex()

    @Volatile
    var status: RestoreStatus = RestoreStatus.NotStarted
        private set

    suspend fun ensureRestored(
        onRestored: suspend (RestoredPlayback) -> Unit,
        onEmpty: suspend () -> Unit,
        onFailed: suspend (Throwable) -> Unit
    ) {
        if (status.isFinished) return

        restoreMutex.withLock {
            if (status.isFinished) return

            status = RestoreStatus.Restoring

            runCatching {
                restore()
            }.onSuccess { restoredPlayback ->
                if (restoredPlayback == null) {
                    status = RestoreStatus.Empty
                    onEmpty()
                } else {
                    onRestored(restoredPlayback)
                    status = RestoreStatus.Restored
                }
            }.onFailure { error ->
                if (error is CancellationException) {
                    status = RestoreStatus.NotStarted
                    throw error
                }
                status = RestoreStatus.Failed
                playbackRuntimeStateStore.initializeIfNeeded()
                onFailed(error)
            }
        }
    }

    fun markRestored() {
        status = RestoreStatus.Restored
    }

    fun markEmpty() {
        status = RestoreStatus.Empty
    }

    fun markFailed() {
        status = RestoreStatus.Failed
    }

    private suspend fun restore(): RestoredPlayback? {
        val restored = resumeLoader.load()
        playbackRuntimeStateStore.initializeIfNeeded()
        return restored
    }
}
