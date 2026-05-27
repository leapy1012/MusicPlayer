package gd.app.musicplayer.playback

import gd.app.musicplayer.playback.state.PlaybackRuntimeStateStore
import gd.app.musicplayer.core.datastore.PlaybackStatePreferenceStore
import gd.app.musicplayer.core.common.util.ShakeDetector
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackStartupInitializer @Inject constructor(
    private val playbackQueueRepo: PlaybackQueueRepo,
    private val playbackStatePreferenceStore: PlaybackStatePreferenceStore,
    private val stateStore: PlaybackRuntimeStateStore,
    private val shakeDetector: ShakeDetector
) {
    private val mutex = Mutex()

    suspend fun initialize() {
        shakeDetector.initialize()

        mutex.withLock {
            // If service already published live state, do nothing.
            if (stateStore.hasActiveState()) {
                return
            }

            val restoredState = buildRestoredState()

            if (restoredState == null) {
                stateStore.initializeIfNeeded()
            } else {
                stateStore.setState(restoredState)
            }
        }
    }

    private suspend fun buildRestoredState(): MusicPlaybackState? {
        val queue = playbackQueueRepo.getQueue()
        if (queue.isEmpty()) return null

        val restoredStart = resolveRestoredPlaybackStart(queue) ?: return null

        val music = queue.getOrNull(restoredStart.index) ?: return null

        return MusicPlaybackState(
            initialized = true,
            queue = queue,
            currentIndex = restoredStart.index,
            currentTrack = music,
            isPlaying = false,
            positionMs = restoredStart.positionMs,
            durationMs = music.duration.toLong(),
            audioSessionId = -1
        )
    }

    private suspend fun resolveRestoredPlaybackStart(
        queue: List<Music>
    ): RestoredPlaybackStart? {
        val progress = playbackStatePreferenceStore.getMusicProgress()
        val progressIndex = if (progress.currentIndex in queue.indices) {
            progress.currentIndex
        } else {
            queue.indexOfFirst { it.id == progress.trackId }
                .takeIf { it >= 0 }
                ?: return null
        }

        return RestoredPlaybackStart(
            index = progressIndex,
            positionMs = progress.progressMs.toLong().coerceAtLeast(0L)
        )
    }

    private data class RestoredPlaybackStart(
        val index: Int,
        val positionMs: Long
    )
}
