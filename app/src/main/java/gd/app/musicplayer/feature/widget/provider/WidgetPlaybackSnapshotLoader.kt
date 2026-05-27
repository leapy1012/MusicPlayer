package gd.app.musicplayer.feature.widget.provider

import gd.app.musicplayer.core.datastore.PlaybackStatePreferenceStore
import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import gd.app.musicplayer.playback.PlaybackMode
import gd.app.musicplayer.playback.state.PlaybackRuntimeStateStore
import gd.app.musicplayer.playback.service.MusicPlaybackService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetPlaybackSnapshotLoader @Inject constructor(
    private val playbackQueueRepo: PlaybackQueueRepo,
    private val playbackRuntimeStateStore: PlaybackRuntimeStateStore,
    private val playbackStatePreferenceStore: PlaybackStatePreferenceStore,
    private val settingPreferencesDataStore: SettingPreferencesDataStore
) {

    suspend fun load(): WidgetPlaybackSnapshot {
        val runtimeState = playbackRuntimeStateStore.state.value
        val serviceRunning = MusicPlaybackService.isRunning

        val queue = if (serviceRunning) {
            runtimeState.queue.ifEmpty {
                playbackQueueRepo.getQueue()
            }
        } else {
            playbackQueueRepo.getQueue()
        }

        val restoredProgress = if (!serviceRunning || runtimeState.currentTrack == null) {
            playbackStatePreferenceStore.getMusicProgress()
        } else {
            null
        }
        val playMode = settingPreferencesDataStore.getPlayMode()

        val runtimeIndex = runtimeState.currentIndex
            .takeIf { index ->
                serviceRunning && index in queue.indices
            }

        val restoredIndex = restoredProgress
            ?.let { progress ->
                queue.indexOfFirst { music -> music.id == progress.trackId }
                    .takeIf { it >= 0 }
            }

        val currentIndex = when {
            runtimeIndex != null -> runtimeIndex
            restoredIndex != null -> restoredIndex
            else -> -1
        }
        val currentTrack = queue.getOrNull(currentIndex)

        return WidgetPlaybackSnapshot(
            queue = queue,
            currentTrack = currentTrack,
            currentIndex = currentIndex,
            positionMs = if (serviceRunning && runtimeIndex != null) {
                runtimeState.positionMs
            } else if (restoredIndex != null) {
                restoredProgress?.progressMs?.toLong()?.coerceAtLeast(0L) ?: 0L
            } else {
                0L
            },
            isPlaying = serviceRunning && runtimeState.isPlaying,
            playMode = playMode
        )
    }

    companion object {
        val EMPTY = WidgetPlaybackSnapshot(
            queue = emptyList(),
            currentTrack = null,
            currentIndex = -1,
            positionMs = 0L,
            isPlaying = false,
            playMode = PlaybackMode.ORDER
        )
    }
}
