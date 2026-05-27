package gd.app.musicplayer.playback.command
import android.content.Intent
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.PlaybackMode

class PlaybackCommandHandler(
    private val payloadStore: PlaybackCommandPayloadStore,
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
        fun toggleCurrentFavorite()
        fun setCurrentFavorite(isFavorite: Boolean)
        fun playIndex(index: Int)
        fun playFromQueue(queue: List<Music>, index: Int)
        fun enqueue(queue: List<Music>)
        fun playNextQueue(queue: List<Music>)
        fun replaceQueue(queue: List<Music>, index: Int)
        fun removeQueueItem(index: Int)
        fun moveQueueItem(fromIndex: Int, toIndex: Int)
        fun updateTrackMetadata(music: Music)
        fun updateTracksMetadata(music: List<Music>)
        fun seekTo(positionMs: Int)
        fun setStopAfterCurrentTrack(enabled: Boolean)
        fun applyAudioEffects()
        fun applyPlaybackTuning()
        fun setDesktopLyricsLocked(locked: Boolean)
        fun currentMusic(): Music?
    }

    fun handle(intent: Intent?, action: String?): Boolean {
        when (action) {
            PlaybackServiceActions.ACTION_REFRESH_NOTIFICATION_STYLE -> callbacks.refreshNotificationStyle()

            PlaybackServiceActions.ACTION_EXIT -> {
                callbacks.exitService()
                return false
            }

            PlaybackServiceActions.ACTION_QUIT -> {
                callbacks.pauseAndPersistForNotificationClose()
                return true
            }

            PlaybackServiceActions.ACTION_TOGGLE_PLAY_PAUSE -> callbacks.togglePlayPause()

            PlaybackServiceActions.ACTION_PLAY -> callbacks.resumePlayback()
            PlaybackServiceActions.ACTION_PAUSE -> callbacks.pausePlayback()
            PlaybackServiceActions.ACTION_NEXT -> callbacks.playNext()
            PlaybackServiceActions.ACTION_PREVIOUS -> callbacks.playPrevious()

            PlaybackServiceActions.ACTION_STOP -> callbacks.stopPlayback()
            PlaybackServiceActions.ACTION_CUSTOM_STOP -> callbacks.stopPlaybackWithoutClearingQueue()

            PlaybackServiceActions.ACTION_RESTART_CURRENT -> callbacks.restartCurrentTrack()
            PlaybackServiceActions.ACTION_CLEAR_QUEUE -> callbacks.clearQueueKeepingNotification()
            PlaybackServiceActions.ACTION_REMOVE_QUEUE_ITEM -> callbacks.removeQueueItem(
                intent?.getIntExtra(PlaybackServiceExtras.EXTRA_INDEX, -1) ?: -1
            )
            PlaybackServiceActions.ACTION_MOVE_QUEUE_ITEM -> callbacks.moveQueueItem(
                fromIndex = intent?.getIntExtra(PlaybackServiceExtras.EXTRA_FROM_INDEX, -1) ?: -1,
                toIndex = intent?.getIntExtra(PlaybackServiceExtras.EXTRA_TO_INDEX, -1) ?: -1
            )
            PlaybackServiceActions.ACTION_UPDATE_TRACK_METADATA -> {
                val music = intent.parcelableExtraCompat<Music>(PlaybackServiceExtras.EXTRA_TRACK)
                if (music != null) {
                    callbacks.updateTrackMetadata(music)
                }
            }
            PlaybackServiceActions.ACTION_UPDATE_TRACKS_METADATA -> {
                val tracks = intent.musicListExtraCompat(PlaybackServiceExtras.EXTRA_QUEUE_ITEMS)
                if (tracks.isNotEmpty()) {
                    callbacks.updateTracksMetadata(tracks)
                }
            }
            PlaybackServiceActions.ACTION_CHANGE_MODE -> callbacks.cyclePlaybackMode()
            PlaybackServiceActions.ACTION_MODE_RANDOM -> callbacks.setPlaybackMode(PlaybackMode.SHUFFLE_ALL)

            PlaybackServiceActions.ACTION_TOGGLE_FAVORITE -> callbacks.toggleCurrentFavorite()
            PlaybackServiceActions.ACTION_CUSTOM_FAVORITE -> callbacks.setCurrentFavorite(true)
            PlaybackServiceActions.ACTION_CUSTOM_UNFAVORITE -> callbacks.setCurrentFavorite(false)

            PlaybackServiceActions.ACTION_CHANGE_MUSIC_BY_INDEX -> handlePlayByIndex(intent)
            PlaybackServiceActions.ACTION_DESK_LRC_LOCK -> callbacks.setDesktopLyricsLocked(false)

            PlaybackServiceActions.ACTION_PLAY_FROM_QUEUE -> {
                val queue = resolveQueuePayload(intent)
                val index = intent?.getIntExtra(PlaybackServiceExtras.EXTRA_INDEX, 0) ?: 0
                if (queue.isNotEmpty()) callbacks.playFromQueue(queue, index)
            }

            PlaybackServiceActions.ACTION_ENQUEUE -> {
                val queue = resolveQueuePayload(intent)
                if (queue.isNotEmpty()) callbacks.enqueue(queue)
            }

            PlaybackServiceActions.ACTION_PLAY_NEXT -> {
                val queue = resolveQueuePayload(intent)
                if (queue.isNotEmpty()) callbacks.playNextQueue(queue)
            }

            PlaybackServiceActions.ACTION_REPLACE_QUEUE -> {
                val queue = resolveQueuePayload(intent)
                val index = intent?.getIntExtra(PlaybackServiceExtras.EXTRA_INDEX, 0) ?: 0
                callbacks.replaceQueue(queue, index)
            }

            PlaybackServiceActions.ACTION_SEEK_TO -> callbacks.seekTo(
                intent?.getIntExtra(PlaybackServiceExtras.EXTRA_SEEK_POSITION_MS, 0) ?: 0
            )

            PlaybackServiceActions.ACTION_SET_STOP_AFTER_CURRENT_TRACK -> callbacks.setStopAfterCurrentTrack(
                intent?.getBooleanExtra(PlaybackServiceExtras.EXTRA_STOP_AFTER_CURRENT_TRACK, false) ?: false
            )

            PlaybackServiceActions.ACTION_APPLY_AUDIO_EFFECTS -> callbacks.applyAudioEffects()
            PlaybackServiceActions.ACTION_APPLY_PLAYBACK_TUNING -> callbacks.applyPlaybackTuning()
        }
        return true
    }

    private fun handlePlayByIndex(intent: Intent?) {
        val parcelIndex = intent.parcelableExtraCompat<IndexActionData>(PlaybackServiceExtras.EXTRA_ACTION_DATA)?.index
        val extraIndex = intent?.getIntExtra(PlaybackServiceExtras.EXTRA_INDEX, -1) ?: -1
        val index = parcelIndex ?: extraIndex
        if (index >= 0) callbacks.playIndex(index)
    }

    private fun resolveQueuePayload(intent: Intent?): List<Music> {
        val token = intent?.getStringExtra(PlaybackServiceExtras.EXTRA_QUEUE_TOKEN)
        val stored = payloadStore.consumeQueue(token)
        if (stored.isNotEmpty()) return stored
        return intent.musicListExtraCompat(PlaybackServiceExtras.EXTRA_QUEUE_ITEMS)
    }

}
