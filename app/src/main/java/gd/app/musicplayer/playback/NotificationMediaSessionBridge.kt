package gd.app.musicplayer.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.view.KeyEvent
import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.playback.service.MusicPlaybackService
import gd.app.musicplayer.ui.shell.MainActivity

class NotificationMediaSessionBridge(
    private val context: Context,
    private val player: ExoPlayer,
    private val queueProvider: () -> List<Music>,
    private val currentIndexProvider: () -> Int,
    private val isEffectivelyPlaying: () -> Boolean,
    private val headsetMediaButtonHandler: HeadsetMediaButtonHandler,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun play()
        fun pause()
        fun next()
        fun previous()
        fun seekTo(positionMs: Int)
        fun toggleFavorite()
        fun setFavorite(isFavorite: Boolean)
        fun quit()
        fun stop()
    }

    val session: MediaSession = createSession()

    fun release() = session.release()

    fun updateMetadata(music: Music, artwork: Bitmap) {
        val queue = queueProvider()
        val currentIndex = currentIndexProvider()
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, music.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, music.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, music.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, music.duration.toLong())
                .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, (currentIndex + 1).toLong())
                .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, queue.size.toLong())
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, artwork)
                .build()
        )
    }

    fun clearMetadata() = session.setMetadata(null)

    fun updateQueue() {
        val sessionQueue = queueProvider().mapIndexed { index, music ->
            MediaSession.QueueItem(
                MediaDescription.Builder()
                    .setMediaId(music.id.toString())
                    .setTitle(music.title)
                    .setSubtitle(music.artist)
                    .setDescription(music.album)
                    .build(),
                index.toLong()
            )
        }
        session.setQueue(sessionQueue)
        session.setQueueTitle(context.getString(R.string.music_player))
    }

    fun clearQueue() = session.setQueue(emptyList())

    fun updatePlaybackState() {
        val queue = queueProvider()
        val currentIndex = currentIndexProvider()
        val hasTrack = currentIndex in queue.indices
        val playing = isEffectivelyPlaying()
        val state = when {
            !hasTrack -> PlaybackState.STATE_STOPPED
            playing -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        val position = runCatching { player.currentPosition }.getOrDefault(0L)
        val builder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP
            )
            .setState(state, position, if (playing) 1f else 0f)

        val currentMusic = queue.getOrNull(currentIndex)
        if (currentMusic != null) {
            val favorite = currentMusic.playlistId == MusicSet.FAVORITES
            builder.addCustomAction(
                PlaybackState.CustomAction.Builder(
                    if (favorite) MusicPlaybackService.ACTION_CUSTOM_UNFAVORITE else MusicPlaybackService.ACTION_CUSTOM_FAVORITE,
                    context.getString(if (favorite) R.string.music_unfavorite else R.string.favorite),
                    if (favorite) R.drawable.vector_notify_favorite else R.drawable.vector_notify_unfavorite
                ).build()
            )
            builder.addCustomAction(
                PlaybackState.CustomAction.Builder(
                    MusicPlaybackService.ACTION_CUSTOM_STOP,
                    context.getString(R.string.operation_stop),
                    R.drawable.vector_notify_close
                ).build()
            )
        }
        session.setPlaybackState(builder.build())
    }

    private fun createSession(): MediaSession {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val mediaButtonIntent = Intent(context, MediaButtonReceiver::class.java).apply {
            action = Intent.ACTION_MEDIA_BUTTON
        }
        val mediaButtonPendingIntent = PendingIntent.getBroadcast(context, 0, mediaButtonIntent, flags)
        val sessionActivity = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(MainActivity.EXTRA_EXPAND_PLAYER, true)
            },
            flags
        )

        return MediaSession(context, MEDIA_SESSION_TAG).apply {
            setMediaButtonReceiver(mediaButtonPendingIntent)
            setSessionActivity(sessionActivity)
            isActive = true
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = callbacks.play()
                override fun onPause() = callbacks.pause()
                override fun onSkipToNext() = callbacks.next()
                override fun onSkipToPrevious() = callbacks.previous()
                override fun onSeekTo(pos: Long) = callbacks.seekTo(pos.toInt())
                override fun onCustomAction(action: String, extras: Bundle?) = handleSessionAction(action)
                override fun onCommand(command: String, args: Bundle?, cb: ResultReceiver?) = handleSessionAction(command)
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }
                    if (event?.action != KeyEvent.ACTION_DOWN) return true
                    headsetMediaButtonHandler.handle(event.keyCode)
                    return true
                }
            })
        }
    }

    private fun handleSessionAction(action: String) {
        when (action) {
            MusicPlaybackService.ACTION_CUSTOM_FAVORITE -> callbacks.setFavorite(true)
            MusicPlaybackService.ACTION_CUSTOM_UNFAVORITE -> callbacks.setFavorite(false)
            MusicPlaybackService.ACTION_TOGGLE_FAVORITE -> callbacks.toggleFavorite()
            MusicPlaybackService.ACTION_QUIT -> callbacks.quit()
            MusicPlaybackService.ACTION_CUSTOM_STOP -> callbacks.quit()
            MusicPlaybackService.ACTION_STOP -> callbacks.stop()
        }
    }

    companion object {
        private const val MEDIA_SESSION_TAG = "MusicPlaybackServiceSession"
    }
}
