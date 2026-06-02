package gd.app.musicplayer.playback.session

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class MusicMediaSessionCallback(
    private val scope: CoroutineScope,
    private val commandHandler: Media3CommandHandler,
    private val resumptionHandler: PlaybackResumptionHandler,
    private val transportTracker: Media3TransportCommandTracker,
    private val currentIndex: () -> Int,
    private val currentPositionMs: () -> Long,
    private val onMediaButtonKeyDown: (keyCode: Int) -> Unit,
    private val onPlayerInteractionFinishedAction: () -> Unit
) : MediaSession.Callback {

    @OptIn(UnstableApi::class)
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
            .buildUpon()
            .add(SessionCommand(Media3Commands.TOGGLE_FAVORITE, Bundle.EMPTY))
            .add(SessionCommand(Media3Commands.CYCLE_PLAYBACK_MODE, Bundle.EMPTY))
            .add(SessionCommand(Media3Commands.STOP_AFTER_CURRENT, Bundle.EMPTY))
            .add(SessionCommand(Media3Commands.CLOSE_NOTIFICATION, Bundle.EMPTY))
            .build()

        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(commands)
            .build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        return commandHandler.handleCustomCommand(customCommand)
    }

    @OptIn(UnstableApi::class)
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        scope.launch {
            runCatching {
                resumptionHandler.resolvePlaybackResumption(isForPlayback)
            }.onSuccess(future::set)
                .onFailure(future::setException)
        }
        return future
    }

    @Suppress("DEPRECATION")
    override fun onPlayerCommandRequest(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        playerCommand: Int
    ): Int {
        transportTracker.onPlayerCommandRequest(
            playerCommand = playerCommand,
            currentIndex = currentIndex(),
            currentPositionMs = currentPositionMs()
        )
        return SessionResult.RESULT_SUCCESS
    }

    override fun onMediaButtonEvent(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        intent: Intent
    ): Boolean {
        val event = intent.mediaButtonKeyEventOrNull()
        if (event?.action != KeyEvent.ACTION_DOWN) return false
        onMediaButtonKeyDown(event.keyCode)
        return true
    }

    override fun onPlayerInteractionFinished(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        playerCommands: Player.Commands
    ) {
        onPlayerInteractionFinishedAction()
    }

    private fun Intent.mediaButtonKeyEventOrNull(): KeyEvent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }
    }
}
