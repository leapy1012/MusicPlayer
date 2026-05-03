package gd.app.musicplayer.playback

import gd.app.musicplayer.data.repo.PlaybackQueueRepo
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackStartupInitializer @Inject constructor(
    private val playbackSessionStore: PlaybackSessionStore,
    private val playbackQueueRepo: PlaybackQueueRepo,
    private val stateStore: PlaybackRuntimeStateStore
) {
    private var initialized = false

    suspend fun initialize() {
        if (initialized) return
        initialized = true

        val session = playbackSessionStore.getLastSession()
            ?: return

        val queue = playbackQueueRepo.getQueue()

        if (queue.isEmpty()) return

        val index = queue.indexOfFirst { music ->
            music.id == session.musicId
        }

        if (index == -1) return

        val music = queue[index]

        stateStore.setState(
            MusicPlaybackState(
                currentIndex = index,
                queue = queue,
                currentMusic = music,
                isPlaying = false,
                positionMs = session.positionMs.coerceAtLeast(0L),
                durationMs = music.duration.toLong()
            )
        )
    }
}
