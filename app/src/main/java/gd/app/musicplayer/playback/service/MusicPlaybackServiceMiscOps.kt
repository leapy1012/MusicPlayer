package gd.app.musicplayer.playback.service


internal fun MusicPlaybackService.updateNotification(force: Boolean = false) {
    dispatchPlaybackEvent(
        PlaybackEvent.NotificationUpdateRequested(force = force)
    )
}
