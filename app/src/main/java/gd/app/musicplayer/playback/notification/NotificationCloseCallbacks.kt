package gd.app.musicplayer.playback.notification

import gd.app.musicplayer.playback.state.PlaybackSnapshot

interface NotificationCloseCallbacks {

    fun capturePlaybackSnapshot(): PlaybackSnapshot

    fun setNotificationDismissedByUser(dismissed: Boolean)

    fun syncQueueFromSnapshot(snapshot: PlaybackSnapshot)

    fun pausePlayerIfNeeded()

    fun persistForSnapshotPolicy(
        snapshot: PlaybackSnapshot,
        persistQueue: Boolean
    )

    fun updateNotificationSessionPlaybackState()

    fun publishPausedSnapshot(snapshot: PlaybackSnapshot)

    fun removeNotification()
}
