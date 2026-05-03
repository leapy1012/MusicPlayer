package gd.app.musicplayer.playback

data class NotificationRenderState(
    val trackId: Long,
    val favorite: Boolean,
    val playing: Boolean,
    val artworkTrackId: Long,
    val artworkReady: Boolean,
)
