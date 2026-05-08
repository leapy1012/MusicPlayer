package gd.app.musicplayer.playback

import gd.app.musicplayer.data.repository.PlaybackQueueRepo
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class PlaybackStartupInitializer @Inject constructor(
    private val playbackSessionStore: PlaybackSessionStore,
    private val playbackQueueRepo: PlaybackQueueRepo,
    private val stateStore: PlaybackRuntimeStateStore
) {
    private val mutex = Mutex()
    private var initialized = false

    suspend fun initialize() {
        mutex.withLock {
            if (initialized) return

            // If service already published live state, do nothing.
            if (stateStore.hasActiveState()) {
                initialized = true
                return
            }

            val restoredState = buildRestoredState()

            if (restoredState == null) {
                stateStore.initializeIfNeeded()
            } else {
                stateStore.setState(restoredState)
            }

            initialized = true
        }
    }

    private suspend fun buildRestoredState(): MusicPlaybackState? {
        val session = playbackSessionStore.getLastSession()
            ?: return null

        val queue = playbackQueueRepo.getQueue()
        if (queue.isEmpty()) return null

        val index = queue.indexOfFirst { it.id == session.musicId }
            .takeIf { it >= 0 }
            ?: session.currentIndex.coerceIn(0, queue.lastIndex)

        val music = queue.getOrNull(index) ?: return null

        return MusicPlaybackState(
            initialized = true,
            currentIndex = index,
            currentTrack = music,
            isPlaying = false,
            positionMs = session.positionMs.coerceAtLeast(0L),
            durationMs = music.duration.toLong(),
            audioSessionId = -1
        )
    }
}