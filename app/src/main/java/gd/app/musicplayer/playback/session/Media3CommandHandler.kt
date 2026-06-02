package gd.app.musicplayer.playback.session

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

internal class Media3CommandHandler(
    private val host: Host
) {
    interface Host {
        fun toggleCurrentFavorite()
        fun cyclePlaybackMode()
        fun pauseAndPersistForNotificationClose()
        fun stopAfterCurrentTrackEnabled(): Boolean
        fun updateStopAfterCurrentTrackMode(enabled: Boolean)
        fun publishAllRuntimeState(forceNotification: Boolean)
    }

    fun handleCustomCommand(customCommand: SessionCommand): ListenableFuture<SessionResult> {
        return when (customCommand.customAction) {
            Media3Commands.TOGGLE_FAVORITE -> {
                host.toggleCurrentFavorite()
                success()
            }

            Media3Commands.CYCLE_PLAYBACK_MODE -> {
                host.cyclePlaybackMode()
                success()
            }

            Media3Commands.CLOSE_NOTIFICATION -> {
                host.pauseAndPersistForNotificationClose()
                success()
            }

            Media3Commands.STOP_AFTER_CURRENT -> {
                host.updateStopAfterCurrentTrackMode(!host.stopAfterCurrentTrackEnabled())
                host.publishAllRuntimeState(forceNotification = true)
                success()
            }

            else -> unsupported()
        }
    }

    private fun success(): ListenableFuture<SessionResult> {
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    @OptIn(UnstableApi::class)
    private fun unsupported(): ListenableFuture<SessionResult> {
        return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
    }
}
