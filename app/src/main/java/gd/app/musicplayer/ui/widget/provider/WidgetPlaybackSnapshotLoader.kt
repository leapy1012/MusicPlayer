package gd.app.musicplayer.ui.widget.provider

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
    private val settingPreferencesDataStore: SettingPreferencesDataStore
) {

    suspend fun load(): WidgetPlaybackSnapshot {
        val queue = playbackQueueRepo.getQueue()
        val runtimeState = playbackRuntimeStateStore.state.value
        val currentTrack = runtimeState.currentTrack
        val playMode = settingPreferencesDataStore.getPlayMode()

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
            positionMs = runtimeState.positionMs,
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