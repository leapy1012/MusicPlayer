package gd.app.musicplayer.playback.notification

import gd.app.musicplayer.playback.state.PlaybackSnapshot

interface NotificationCloseCallbacks {

    fun capturePlaybackSnapshot(): PlaybackSnapshot

    fun setNotificationDismissedByUser(dismissed: Boolean)

    fun syncQueueFromSnapshot(snapshot: PlaybackSnapshot)

    fun pausePlayerIfNeeded()

    fun persistPlaybackSnapshotBlocking(
        snapshot: PlaybackSnapshot,
        persistQueue: Boolean
    )


    fun publishPausedSnapshot(snapshot: PlaybackSnapshot)

    fun removeNotification()
}
