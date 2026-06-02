package gd.app.musicplayer.playback.service

internal class PlaybackSessionFlagsState {
    var screenReceiverRegistered: Boolean = false
    var keepIdleNotification: Boolean = false
    var stopAfterCurrentTrack: Boolean = false
    var notificationDismissedByUser: Boolean = false
    var lastSessionAutoSaveElapsedMs: Long = 0L

    fun reset() {
        screenReceiverRegistered = false
        keepIdleNotification = false
        stopAfterCurrentTrack = false
        notificationDismissedByUser = false
        lastSessionAutoSaveElapsedMs = 0L
    }
}
