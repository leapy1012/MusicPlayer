package gd.app.musicplayer.playback.shutdown

import gd.app.musicplayer.playback.state.PlaybackSnapshot

interface ShutdownCallbacks {

    fun capturePlaybackSnapshot(): PlaybackSnapshot

    fun persistForSnapshotPolicy(
        snapshot: PlaybackSnapshot,
        persistQueue: Boolean
    )

    fun resetPlaybackStatistics()

    fun resetTimedTransition()

    fun cancelVolumeFade()

    fun stopAndClearPlayer()

    fun abandonAudioFocus()

    fun clearArtworkState()

    fun clearQueueState()

    fun clearPersistedPlaybackState()

    fun clearPersistedQueue()

    fun clearNotificationSession()

    fun markRestoreEmpty()

    fun resetRuntimeState()

    fun publishStateAfterShutdown(snapshot: PlaybackSnapshot)

    fun removeNotification()

    fun onServiceShouldStop()
}
