package gd.app.musicplayer.ui.widget.provider

import gd.app.musicplayer.data.local.preference.PlaybackStatePreferenceStore
import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import gd.app.musicplayer.playback.PlaybackMode
import gd.app.musicplayer.playback.PlaybackRuntimeStateStore
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
        val queue = runtimeState.queue.ifEmpty {
            playbackQueueRepo.getQueue()
        }
        val restoredProgress = if (runtimeState.currentTrack == null) {
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

        val currentTrack = runtimeTrack
            ?: restoredIndex?.let { index -> queue[index] }

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
            positionMs = if (runtimeTrack != null) {
                runtimeState.positionMs
            } else if (restoredIndexByTrackId != null) {
                restoredProgress?.progressMs?.toLong()?.coerceAtLeast(0L) ?: 0L
            } else {
                0L
            },
            isPlaying = runtimeState.isPlaying,
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
