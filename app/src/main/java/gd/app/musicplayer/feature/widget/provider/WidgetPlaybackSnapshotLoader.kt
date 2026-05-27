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

        val runtimeTrack = runtimeState.currentTrack
            ?.takeIf { track ->
                queue.any { queued -> queued.id == track.id }
            }

        val restoredIndexByTrackId = restoredProgress
            ?.let { progress -> queue.indexOfFirst { queued -> queued.id == progress.trackId } }
            ?.takeIf { index -> index in queue.indices }

        val restoredIndex = restoredIndexByTrackId
            ?: restoredProgress?.currentIndex?.takeIf { index ->
                index in queue.indices
            }

        val currentTrack = if (serviceRunning) {
            runtimeTrack
                ?: restoredIndex?.let { index -> queue[index] }
        } else {
            restoredIndex?.let { index -> queue[index] }
                ?: runtimeTrack
        }

        val currentIndex = currentTrack
            ?.let { track ->
                queue.indexOfFirst { queued ->
                    queued.id == track.id
                }
            }
            ?: -1

        return WidgetPlaybackSnapshot(
            queue = queue,
            currentTrack = currentTrack,
            currentIndex = currentIndex,
            positionMs = if (serviceRunning && runtimeTrack != null) {
                runtimeState.positionMs
            } else if (restoredIndexByTrackId != null) {
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
