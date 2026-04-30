package gd.app.musicplayer.playback.notification

import android.app.PendingIntent
import android.content.Context
import android.media.session.MediaSession
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.playback.MusicPlaybackService

class DefaultMusicNotificationContent(
    private val music: Music?,
    private val playing: Boolean,
    private val desktopLyricsEnabled: Boolean,
    private val albumArt: NotificationAlbumArtwork,
    private val contentIntent: PendingIntent,
    private val actionIntentFactory: (String, Int) -> PendingIntent,
    private val mediaSessionToken: MediaSession.Token?,
) : MusicNotificationContent {

    override fun getSmallIconRes(): Int = R.drawable.notify_icon

    override fun isPlaying(): Boolean = playing

    override fun createNextIntent(context: Context): PendingIntent =
        actionIntentFactory(MusicPlaybackService.ACTION_NEXT, REQUEST_NEXT)

    override fun createPreviousIntent(context: Context): PendingIntent =
        actionIntentFactory(MusicPlaybackService.ACTION_PREVIOUS, REQUEST_PREVIOUS)

    override fun isDesktopLyricsEnabled(): Boolean = desktopLyricsEnabled

    override fun getDefaultAlbumArtRes(viewType: Int, useNightArtwork: Boolean): Int =
        if (useNightArtwork) R.drawable.notify_default_album_night else R.drawable.notify_default_album

    override fun getTitle(): String = music?.title?.takeIf { it.isNotBlank() } ?: contextTitleFallback

    override fun getArtistName(): String = music?.artist?.takeIf { it.isNotBlank() } ?: contextArtistFallback

    override fun createFavoriteIntent(context: Context): PendingIntent =
        actionIntentFactory(MusicPlaybackService.ACTION_TOGGLE_FAVORITE, REQUEST_FAVORITE)

    override fun createPlayPauseIntent(context: Context): PendingIntent =
        actionIntentFactory(MusicPlaybackService.ACTION_TOGGLE_PLAY_PAUSE, REQUEST_PLAY_PAUSE)

    override fun getAlbumName(): String = music?.album.orEmpty()

    override fun createContentIntent(context: Context): PendingIntent = contentIntent

    override fun createDesktopLyricsIntent(context: Context): PendingIntent =
        actionIntentFactory(MusicPlaybackService.ACTION_DESK_LRC_LOCK, REQUEST_DESK_LRC_LOCK)

    override fun isFavorite(): Boolean = music?.playlistId == MusicSet.FAVORITES_ID

    override fun getAlbumArt(viewType: Int): NotificationAlbumArtwork = albumArt

    override fun createStopIntent(context: Context): PendingIntent =
        actionIntentFactory(MusicPlaybackService.ACTION_QUIT, REQUEST_STOP)

    override fun getMediaSessionToken(): MediaSession.Token? = mediaSessionToken

    companion object {
        private const val REQUEST_PREVIOUS = 21
        private const val REQUEST_PLAY_PAUSE = 22
        private const val REQUEST_NEXT = 23
        private const val REQUEST_STOP = 24
        private const val REQUEST_FAVORITE = 26
        private const val REQUEST_DESK_LRC_LOCK = 27

        private const val contextTitleFallback = "Music"
        private const val contextArtistFallback = "Artist"
    }
}
