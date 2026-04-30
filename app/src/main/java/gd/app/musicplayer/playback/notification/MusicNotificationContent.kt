package gd.app.musicplayer.playback.notification

import android.app.PendingIntent
import android.content.Context
import android.media.session.MediaSession

interface MusicNotificationContent {

    fun getSmallIconRes(): Int

    fun isPlaying(): Boolean

    fun createNextIntent(context: Context): PendingIntent

    fun createPreviousIntent(context: Context): PendingIntent

    fun isDesktopLyricsEnabled(): Boolean

    fun getDefaultAlbumArtRes(
        viewType: Int,
        useNightArtwork: Boolean,
    ): Int

    fun getTitle(): String

    fun getArtistName(): String

    fun createFavoriteIntent(context: Context): PendingIntent

    fun createPlayPauseIntent(context: Context): PendingIntent

    fun getAlbumName(): String

    fun createContentIntent(context: Context): PendingIntent

    fun createDesktopLyricsIntent(context: Context): PendingIntent

    fun isFavorite(): Boolean

    fun getAlbumArt(viewType: Int): NotificationAlbumArtwork

    fun createStopIntent(context: Context): PendingIntent

    fun getMediaSessionToken(): MediaSession.Token?
}
