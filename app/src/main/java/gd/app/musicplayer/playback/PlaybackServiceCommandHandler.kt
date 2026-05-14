package gd.app.musicplayer.playback

import android.content.Intent
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.service.MusicPlaybackService

class PlaybackServiceCommandHandler(
    private val service: MusicPlaybackService,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun refreshNotificationStyle()
        fun exitService()
        fun pauseAndPersistForNotificationClose()
        fun togglePlayPause()
        fun resumePlayback()
        fun pausePlayback()
        fun playNext()
        fun playPrevious()
        fun stopPlayback()
        fun stopPlaybackWithoutClearingQueue()
        fun restartCurrentTrack()
        fun clearQueueKeepingNotification()
        fun cyclePlaybackMode()
        fun setPlaybackMode(mode: Int)
        fun toggleFavorite(music: Music)
        fun setFavorite(music: Music, favorited: Boolean)
        fun playIndex(index: Int)
        fun playFromQueue(queue: List<Music>, index: Int)
        fun enqueue(queue: List<Music>)
        fun playNextQueue(queue: List<Music>)
        fun replaceQueue(queue: List<Music>, index: Int)
        fun seekTo(positionMs: Int)
        fun setStopAfterCurrentTrack(enabled: Boolean)
        fun applyAudioEffects()
        fun applyPlaybackTuning()
        fun setDesktopLyricsLocked(locked: Boolean)
        fun currentMusic(): Music?
    }

    fun handle(intent: Intent?, action: String?): Boolean {
        when (action) {
            MusicPlaybackService.ACTION_REFRESH_NOTIFICATION_STYLE -> callbacks.refreshNotificationStyle()

            MusicPlaybackService.ACTION_EXIT -> {
                callbacks.exitService()
                return false
            }

            MusicPlaybackService.ACTION_QUIT -> {
                callbacks.pauseAndPersistForNotificationClose()
                return true
            }

            MusicPlaybackService.ACTION_TOGGLE_PLAY_PAUSE -> callbacks.togglePlayPause()

            MusicPlaybackService.ACTION_PLAY -> callbacks.resumePlayback()
            MusicPlaybackService.ACTION_PAUSE -> callbacks.pausePlayback()
            MusicPlaybackService.ACTION_NEXT -> callbacks.playNext()
            MusicPlaybackService.ACTION_PREVIOUS -> callbacks.playPrevious()

            MusicPlaybackService.ACTION_STOP -> callbacks.stopPlayback()
            MusicPlaybackService.ACTION_CUSTOM_STOP -> callbacks.stopPlaybackWithoutClearingQueue()

            MusicPlaybackService.ACTION_RESTART_CURRENT -> callbacks.restartCurrentTrack()
            MusicPlaybackService.ACTION_CLEAR_QUEUE -> callbacks.clearQueueKeepingNotification()
            MusicPlaybackService.ACTION_CHANGE_MODE -> callbacks.cyclePlaybackMode()
            MusicPlaybackService.ACTION_MODE_RANDOM -> callbacks.setPlaybackMode(PlaybackMode.SHUFFLE_ALL)

            MusicPlaybackService.ACTION_TOGGLE_FAVORITE -> callbacks.currentMusic()?.let(callbacks::toggleFavorite)
            MusicPlaybackService.ACTION_CUSTOM_FAVORITE -> callbacks.currentMusic()?.let { music ->
                callbacks.setFavorite(music, true)
            }
            MusicPlaybackService.ACTION_CUSTOM_UNFAVORITE -> callbacks.currentMusic()?.let { music ->
                callbacks.setFavorite(music, false)
            }

            MusicPlaybackService.ACTION_CHANGE_MUSIC_BY_INDEX -> handlePlayByIndex(intent)
            MusicPlaybackService.ACTION_DESK_LRC_LOCK -> callbacks.setDesktopLyricsLocked(false)

            MusicPlaybackService.ACTION_PLAY_FROM_QUEUE -> {
                val queue = intent.musicListExtraCompat(MusicPlaybackService.EXTRA_QUEUE_ITEMS)
                val index = intent?.getIntExtra(MusicPlaybackService.EXTRA_INDEX, 0) ?: 0
                if (queue.isNotEmpty()) callbacks.playFromQueue(queue, index)
            }

            MusicPlaybackService.ACTION_ENQUEUE -> {
                val queue = intent.musicListExtraCompat(MusicPlaybackService.EXTRA_QUEUE_ITEMS)
                if (queue.isNotEmpty()) callbacks.enqueue(queue)
            }

            MusicPlaybackService.ACTION_PLAY_NEXT -> {
                val queue = intent.musicListExtraCompat(MusicPlaybackService.EXTRA_QUEUE_ITEMS)
                if (queue.isNotEmpty()) callbacks.playNextQueue(queue)
            }

            MusicPlaybackService.ACTION_REPLACE_QUEUE -> {
                val queue = intent.musicListExtraCompat(MusicPlaybackService.EXTRA_QUEUE_ITEMS)
                val index = intent?.getIntExtra(MusicPlaybackService.EXTRA_INDEX, 0) ?: 0
                callbacks.replaceQueue(queue, index)
            }

            MusicPlaybackService.ACTION_SEEK_TO -> callbacks.seekTo(
                intent?.getIntExtra(MusicPlaybackService.EXTRA_SEEK_POSITION_MS, 0) ?: 0
            )

            MusicPlaybackService.ACTION_SET_STOP_AFTER_CURRENT_TRACK -> callbacks.setStopAfterCurrentTrack(
                intent?.getBooleanExtra(MusicPlaybackService.EXTRA_STOP_AFTER_CURRENT_TRACK, false) ?: false
            )

            MusicPlaybackService.ACTION_APPLY_AUDIO_EFFECTS -> callbacks.applyAudioEffects()
            MusicPlaybackService.ACTION_APPLY_PLAYBACK_TUNING -> callbacks.applyPlaybackTuning()
        }
        return true
    }

    private fun handlePlayByIndex(intent: Intent?) {
        val parcelIndex = intent.parcelableExtraCompat<IndexActionData>(MusicPlaybackService.EXTRA_ACTION_DATA)?.index
        val extraIndex = intent?.getIntExtra(MusicPlaybackService.EXTRA_INDEX, -1) ?: -1
        val index = parcelIndex ?: extraIndex
        if (index >= 0) callbacks.playIndex(index)
    }

}
