package gd.app.musicplayer.playback.service

import android.app.Service
import android.content.Intent
import gd.app.musicplayer.playback.command.PlaybackCommandHandler
import gd.app.musicplayer.playback.command.PlaybackServiceActions

/**
 * Routes start-command intents for [MusicPlaybackService].
 *
 * This class intentionally owns only Android command orchestration:
 * - empty start-command handling
 * - foreground promotion policy
 * - dispatching into [PlaybackCommandHandler]
 *
 * Playback business logic still lives behind callbacks owned by the service layer.
 */
class PlaybackServiceRuntime(
    private val commandHandler: PlaybackCommandHandler,
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun handleEmptyStartCommand(startId: Int): Int
        fun promoteToForegroundForPlaybackCommand()
    }

    fun handleStartCommand(
        intent: Intent?,
        startId: Int
    ): Int {
        val action = intent?.action

        if (action.isNullOrBlank()) {
            return callbacks.handleEmptyStartCommand(startId)
        }

        if (action.requiresForegroundPromotion()) {
            callbacks.promoteToForegroundForPlaybackCommand()
        }

        val shouldContinue = commandHandler.handle(
            intent = intent,
            action = action
        )

        return if (shouldContinue) {
            Service.START_STICKY
        } else {
            Service.START_NOT_STICKY
        }
    }

    private fun String.requiresForegroundPromotion(): Boolean {
        return when (this) {
            PlaybackServiceActions.ACTION_PLAY_FROM_QUEUE,
            PlaybackServiceActions.ACTION_PLAY,
            PlaybackServiceActions.ACTION_PLAY_NEXT,
            PlaybackServiceActions.ACTION_REPLACE_QUEUE,
            PlaybackServiceActions.ACTION_TOGGLE_PLAY_PAUSE,
            PlaybackServiceActions.ACTION_NEXT,
            PlaybackServiceActions.ACTION_PREVIOUS,
            PlaybackServiceActions.ACTION_CHANGE_MUSIC_BY_INDEX -> true
            else -> false
        }
    }
}

