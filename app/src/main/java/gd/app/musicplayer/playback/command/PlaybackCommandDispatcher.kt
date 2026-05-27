package gd.app.musicplayer.playback.command
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import gd.app.musicplayer.playback.service.MusicPlaybackService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackCommandDispatcher @Inject constructor(
    private val payloadStore: PlaybackCommandPayloadStore
) {

    fun dispatch(context: Context, command: PlaybackCommand) {
        val appContext = context.applicationContext
        val intent = command.toIntent(appContext)

        if (command.requiresForegroundStart()) {
            ContextCompat.startForegroundService(appContext, intent)
        } else {
            appContext.startService(intent)
        }
    }

    private fun PlaybackCommand.toIntent(context: Context): Intent {
        return Intent(context, MusicPlaybackService::class.java).apply {
            when (val command = this@toIntent) {
                is PlaybackCommand.PlayFromQueue -> {
                    action = PlaybackServiceActions.ACTION_PLAY_FROM_QUEUE
                    putExtra(
                        PlaybackServiceExtras.EXTRA_QUEUE_TOKEN,
                        payloadStore.putQueue(command.queue)
                    )
                    putExtra(PlaybackServiceExtras.EXTRA_INDEX, command.index)
                }

                is PlaybackCommand.Enqueue -> {
                    action = PlaybackServiceActions.ACTION_ENQUEUE
                    putExtra(
                        PlaybackServiceExtras.EXTRA_QUEUE_TOKEN,
                        payloadStore.putQueue(command.queue)
                    )
                }

                is PlaybackCommand.PlayNextItems -> {
                    action = PlaybackServiceActions.ACTION_PLAY_NEXT
                    putExtra(
                        PlaybackServiceExtras.EXTRA_QUEUE_TOKEN,
                        payloadStore.putQueue(command.queue)
                    )
                }

                is PlaybackCommand.ReplaceQueue -> {
                    action = PlaybackServiceActions.ACTION_REPLACE_QUEUE
                    putExtra(
                        PlaybackServiceExtras.EXTRA_QUEUE_TOKEN,
                        payloadStore.putQueue(command.queue)
                    )
                    putExtra(PlaybackServiceExtras.EXTRA_INDEX, command.index)
                }

                is PlaybackCommand.SeekTo -> {
                    action = PlaybackServiceActions.ACTION_SEEK_TO
                    putExtra(
                        PlaybackServiceExtras.EXTRA_SEEK_POSITION_MS,
                        command.positionMs
                    )
                }

                is PlaybackCommand.SetStopAfterCurrentTrack -> {
                    action = PlaybackServiceActions.ACTION_SET_STOP_AFTER_CURRENT_TRACK
                    putExtra(
                        PlaybackServiceExtras.EXTRA_STOP_AFTER_CURRENT_TRACK,
                        command.enabled
                    )
                }

                is PlaybackCommand.RemoveQueueItem -> {
                    action = PlaybackServiceActions.ACTION_REMOVE_QUEUE_ITEM
                    putExtra(PlaybackServiceExtras.EXTRA_INDEX, command.index)
                }

                is PlaybackCommand.MoveQueueItem -> {
                    action = PlaybackServiceActions.ACTION_MOVE_QUEUE_ITEM
                    putExtra(PlaybackServiceExtras.EXTRA_FROM_INDEX, command.fromIndex)
                    putExtra(PlaybackServiceExtras.EXTRA_TO_INDEX, command.toIndex)
                }

                is PlaybackCommand.RefreshEditedTrack -> {
                    action = PlaybackServiceActions.ACTION_UPDATE_TRACK_METADATA
                    putExtra(PlaybackServiceExtras.EXTRA_TRACK, command.track)
                }

                is PlaybackCommand.RefreshEditedTracks -> {
                    action = PlaybackServiceActions.ACTION_UPDATE_TRACKS_METADATA
                    putParcelableArrayListExtra(
                        PlaybackServiceExtras.EXTRA_QUEUE_ITEMS,
                        ArrayList(command.tracks)
                    )
                }

                PlaybackCommand.TogglePlayPause -> {
                    action = PlaybackServiceActions.ACTION_TOGGLE_PLAY_PAUSE
                }

                PlaybackCommand.Play -> {
                    action = PlaybackServiceActions.ACTION_PLAY
                }

                PlaybackCommand.Pause -> {
                    action = PlaybackServiceActions.ACTION_PAUSE
                }

                is PlaybackCommand.PlayIndex -> {
                    action = PlaybackServiceActions.ACTION_CHANGE_MUSIC_BY_INDEX
                    putExtra(PlaybackServiceExtras.EXTRA_INDEX, command.index)
                }

                PlaybackCommand.Next -> {
                    action = PlaybackServiceActions.ACTION_NEXT
                }

                PlaybackCommand.Previous -> {
                    action = PlaybackServiceActions.ACTION_PREVIOUS
                }

                PlaybackCommand.ClearQueue -> {
                    action = PlaybackServiceActions.ACTION_CLEAR_QUEUE
                }

                PlaybackCommand.Stop -> {
                    action = PlaybackServiceActions.ACTION_STOP
                }

                PlaybackCommand.ApplyAudioEffects -> {
                    action = PlaybackServiceActions.ACTION_APPLY_AUDIO_EFFECTS
                }

                PlaybackCommand.ApplyPlaybackTuning -> {
                    action = PlaybackServiceActions.ACTION_APPLY_PLAYBACK_TUNING
                }

                PlaybackCommand.RefreshNotificationStyle -> {
                    action = PlaybackServiceActions.ACTION_REFRESH_NOTIFICATION_STYLE
                }

                PlaybackCommand.RestartCurrentTrack -> {
                    action = PlaybackServiceActions.ACTION_RESTART_CURRENT
                }

                PlaybackCommand.ChangeMode -> {
                    action = PlaybackServiceActions.ACTION_CHANGE_MODE
                }

                PlaybackCommand.SetShuffleAllMode -> {
                    action = PlaybackServiceActions.ACTION_MODE_RANDOM
                }

                PlaybackCommand.ToggleFavorite -> {
                    action = PlaybackServiceActions.ACTION_TOGGLE_FAVORITE
                }
            }
        }
    }

    private fun PlaybackCommand.requiresForegroundStart(): Boolean {
        return when (this) {
            is PlaybackCommand.PlayFromQueue,
            is PlaybackCommand.PlayNextItems,
            is PlaybackCommand.ReplaceQueue,
            PlaybackCommand.Play,
            is PlaybackCommand.PlayIndex,
            PlaybackCommand.TogglePlayPause,
            PlaybackCommand.Next,
            PlaybackCommand.Previous -> true

            is PlaybackCommand.Enqueue,
            is PlaybackCommand.SeekTo,
            is PlaybackCommand.SetStopAfterCurrentTrack,
            is PlaybackCommand.RemoveQueueItem,
            is PlaybackCommand.MoveQueueItem,
            is PlaybackCommand.RefreshEditedTrack,
            is PlaybackCommand.RefreshEditedTracks,
            PlaybackCommand.Pause,
            PlaybackCommand.ClearQueue,
            PlaybackCommand.Stop,
            PlaybackCommand.ApplyAudioEffects,
            PlaybackCommand.ApplyPlaybackTuning,
            PlaybackCommand.RefreshNotificationStyle,
            PlaybackCommand.RestartCurrentTrack,
            PlaybackCommand.ChangeMode,
            PlaybackCommand.SetShuffleAllMode,
            PlaybackCommand.ToggleFavorite -> false
        }
    }
}
