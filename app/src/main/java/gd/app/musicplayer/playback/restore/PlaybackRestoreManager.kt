package gd.app.musicplayer.playback.restore

import gd.app.musicplayer.core.common.dispatcher.AppDispatchers
import gd.app.musicplayer.core.datastore.PlaybackStatePreferenceStore
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import gd.app.musicplayer.playback.state.PlaybackRuntimeStateStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PlaybackRestoreManager(
    private val playbackQueueRepo: PlaybackQueueRepo,
    private val playbackStatePreferenceStore: PlaybackStatePreferenceStore,
    private val playbackRuntimeStateStore: PlaybackRuntimeStateStore,
    private val dispatchers: AppDispatchers
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
                    status = RestoreStatus.Restored
                    onRestored(restoredPlayback)
                }
            }.onFailure { error ->
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
        val restoredQueue = withContext(dispatchers.io) {
            playbackQueueRepo.getQueue()
        }

        playbackRuntimeStateStore.initializeIfNeeded()

        val progress = withContext(dispatchers.io) {
            playbackStatePreferenceStore.getMusicProgress()
        }

        return PlaybackRestoreResolver.resolve(
            queue = restoredQueue,
            progress = progress
        )
    }
}
