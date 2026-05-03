package gd.app.musicplayer.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackServiceDispatcher @Inject constructor() {

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
                    action = MusicPlaybackService.ACTION_PLAY_FROM_QUEUE
                    putParcelableArrayListExtra(
                        MusicPlaybackService.EXTRA_QUEUE_ITEMS,
                        ArrayList(command.queue)
                    )
                    putExtra(MusicPlaybackService.EXTRA_INDEX, command.index)
                }

                is PlaybackCommand.Enqueue -> {
                    action = MusicPlaybackService.ACTION_ENQUEUE
                    putParcelableArrayListExtra(
                        MusicPlaybackService.EXTRA_QUEUE_ITEMS,
                        ArrayList(command.queue)
                    )
                }

                is PlaybackCommand.PlayNextItems -> {
                    action = MusicPlaybackService.ACTION_PLAY_NEXT
                    putParcelableArrayListExtra(
                        MusicPlaybackService.EXTRA_QUEUE_ITEMS,
                        ArrayList(command.queue)
                    )
                }

                is PlaybackCommand.ReplaceQueue -> {
                    action = MusicPlaybackService.ACTION_REPLACE_QUEUE
                    putParcelableArrayListExtra(
                        MusicPlaybackService.EXTRA_QUEUE_ITEMS,
                        ArrayList(command.queue)
                    )
                    putExtra(MusicPlaybackService.EXTRA_INDEX, command.index)
                }

                is PlaybackCommand.SeekTo -> {
                    action = MusicPlaybackService.ACTION_SEEK_TO
                    putExtra(
                        MusicPlaybackService.EXTRA_SEEK_POSITION_MS,
                        command.positionMs
                    )
                }

                is PlaybackCommand.SetStopAfterCurrentTrack -> {
                    action = MusicPlaybackService.ACTION_SET_STOP_AFTER_CURRENT_TRACK
                    putExtra(
                        MusicPlaybackService.EXTRA_STOP_AFTER_CURRENT_TRACK,
                        command.enabled
                    )
                }

                PlaybackCommand.TogglePlayPause -> {
                    action = MusicPlaybackService.ACTION_TOGGLE_PLAY_PAUSE
                }

                PlaybackCommand.Play -> {
                    action = MusicPlaybackService.ACTION_PLAY
                }

                PlaybackCommand.Pause -> {
                    action = MusicPlaybackService.ACTION_PAUSE
                }

                PlaybackCommand.Next -> {
                    action = MusicPlaybackService.ACTION_NEXT
                }

                PlaybackCommand.Previous -> {
                    action = MusicPlaybackService.ACTION_PREVIOUS
                }

                PlaybackCommand.ClearQueue -> {
                    action = MusicPlaybackService.ACTION_CLEAR_QUEUE
                }

                PlaybackCommand.Stop -> {
                    action = MusicPlaybackService.ACTION_STOP
                }

                PlaybackCommand.ApplyAudioEffects -> {
                    action = MusicPlaybackService.ACTION_APPLY_AUDIO_EFFECTS
                }

                PlaybackCommand.ApplyPlaybackTuning -> {
                    action = MusicPlaybackService.ACTION_APPLY_PLAYBACK_TUNING
                }

                PlaybackCommand.RefreshNotificationStyle -> {
                    action = MusicPlaybackService.ACTION_REFRESH_NOTIFICATION_STYLE
                }

                PlaybackCommand.RestartCurrentTrack -> {
                    action = MusicPlaybackService.ACTION_RESTART_CURRENT
                }

                PlaybackCommand.ChangeMode -> {
                    action = MusicPlaybackService.ACTION_CHANGE_MODE
                }

                PlaybackCommand.SetShuffleAllMode -> {
                    action = MusicPlaybackService.ACTION_MODE_RANDOM
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
            PlaybackCommand.TogglePlayPause,
            PlaybackCommand.Next,
            PlaybackCommand.Previous -> true

            is PlaybackCommand.Enqueue,
            is PlaybackCommand.SeekTo,
            is PlaybackCommand.SetStopAfterCurrentTrack,
            PlaybackCommand.Pause,
            PlaybackCommand.ClearQueue,
            PlaybackCommand.Stop,
            PlaybackCommand.ApplyAudioEffects,
            PlaybackCommand.ApplyPlaybackTuning,
            PlaybackCommand.RefreshNotificationStyle,
            PlaybackCommand.RestartCurrentTrack,
            PlaybackCommand.ChangeMode,
            PlaybackCommand.SetShuffleAllMode -> false
        }
    }
}
