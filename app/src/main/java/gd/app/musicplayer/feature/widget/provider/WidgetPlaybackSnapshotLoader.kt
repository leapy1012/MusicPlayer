package gd.app.musicplayer.feature.widget.provider

import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import gd.app.musicplayer.playback.PlaybackMode
import gd.app.musicplayer.playback.state.PlaybackRuntimeStateStore
import gd.app.musicplayer.playback.service.MusicPlaybackService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetPlaybackSnapshotLoader @Inject constructor(
    private val resumeLoader: gd.app.musicplayer.playback.restore.PlaybackResumeLoader,
    private val playbackRuntimeStateStore: PlaybackRuntimeStateStore,
    private val settingPreferencesDataStore: SettingPreferencesDataStore
) {

    suspend fun load(): WidgetPlaybackSnapshot {
        val initial = playbackRuntimeStateStore.state.value
        val saved = if (initial.currentTrack == null) resumeLoader.load() else null
        val runtimeState = playbackRuntimeStateStore.state.value
        val serviceRunning = MusicPlaybackService.isRunning
        val useRuntime = runtimeState.currentTrack != null && runtimeState.queue.isNotEmpty()
        val queue = if (useRuntime) runtimeState.queue else saved?.queue.orEmpty()
        val playMode = settingPreferencesDataStore.getPlayMode()

        val runtimeIndex = runtimeState.currentIndex
            .takeIf { index ->
                useRuntime && index in queue.indices
            }

        val restored = saved
        val restoredIndex = restored?.index

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
            positionMs = if (runtimeIndex != null) {
                runtimeState.positionMs
            } else if (restoredIndex != null) {
                restored?.positionMs ?: 0L
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
