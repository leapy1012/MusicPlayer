package gd.app.musicplayer.playback

import gd.app.musicplayer.playback.state.PlaybackRuntimeStateStore
import gd.app.musicplayer.core.common.util.ShakeDetector
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackStartupInitializer @Inject constructor(
    private val resumeLoader: gd.app.musicplayer.playback.restore.PlaybackResumeLoader,
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

            val beforeRead = stateStore.initializationToken()
            val restoredState = try { buildRestoredState() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { null }

            if (restoredState == null) {
                stateStore.initializeIfNeeded()
            } else {
                stateStore.initializeFrom(beforeRead, restoredState)
            }
        }
    }

    private suspend fun buildRestoredState(): MusicPlaybackState? {
        val restoredStart = resumeLoader.load() ?: return null
        val queue = restoredStart.queue
        val music = queue[restoredStart.index]

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

}
