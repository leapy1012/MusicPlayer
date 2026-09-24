package gd.app.musicplayer.playback.widget

import gd.app.musicplayer.feature.widget.provider.WidgetPlaybackSnapshot
import gd.app.musicplayer.feature.widget.provider.WidgetUpdateCoordinator
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import gd.app.musicplayer.playback.state.PlaybackSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Owns widget snapshot mapping and push scheduling for playback service.
 */
class PlaybackWidgetBridge(
    private val widgetUpdateCoordinator: WidgetUpdateCoordinator,
    private val scope: CoroutineScope,
    private val playModeProvider: () -> Int
) {
    private var updateJob: Job? = null

    fun updateFromRuntimeState(state: MusicPlaybackState) {
        update(map(state))
    }

    fun update(snapshot: WidgetPlaybackSnapshot) {
        updateJob?.cancel()
        updateJob = scope.launch {
            withContext(NonCancellable) {
                widgetUpdateCoordinator.updateAll(snapshot)
            }
        }
    }

    fun updateBlocking(snapshot: WidgetPlaybackSnapshot) {
        updateJob?.cancel()
        runBlocking {
            withContext(NonCancellable) {
                widgetUpdateCoordinator.updateAll(snapshot)
            }
        }
    }

    fun cancelPending() {
        updateJob?.cancel()
        updateJob = null
    }

    fun map(state: MusicPlaybackState): WidgetPlaybackSnapshot {
        return WidgetPlaybackSnapshot(
            queue = state.queue,
            currentTrack = state.currentTrack,
            currentIndex = state.currentIndex,
            positionMs = state.positionMs,
            isPlaying = state.isPlaying,
            playMode = playModeProvider()
        )
    }

    fun map(snapshot: PlaybackSnapshot): WidgetPlaybackSnapshot {
        return WidgetPlaybackSnapshot(
            queue = snapshot.queue,
            currentTrack = snapshot.currentTrack,
            currentIndex = snapshot.currentIndex,
            positionMs = snapshot.positionMs,
            isPlaying = false,
            playMode = playModeProvider()
        )
    }
}
