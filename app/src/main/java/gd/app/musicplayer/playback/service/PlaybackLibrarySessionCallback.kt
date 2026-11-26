package gd.app.musicplayer.playback.service

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaLibraryService.LibraryParams
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import gd.app.musicplayer.playback.command.PlaybackServiceActions
import gd.app.musicplayer.playback.service.MusicPlaybackService.Companion.MEDIA3_COMMAND_TOGGLE_FAVORITE
import gd.app.musicplayer.playback.service.MusicPlaybackService.Companion.MEDIA3_COMMAND_CYCLE_PLAYBACK_MODE
import gd.app.musicplayer.playback.service.MusicPlaybackService.Companion.MEDIA3_COMMAND_STOP_AFTER_CURRENT
import gd.app.musicplayer.playback.service.MusicPlaybackService.Companion.MEDIA3_COMMAND_CLOSE_NOTIFICATION
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Platform protocol adapter; transport and restoration stay in the service coordinator. */
@OptIn(UnstableApi::class)
internal class PlaybackLibrarySessionCallback(private val service: MusicPlaybackService) :
    MediaLibrarySession.Callback {
    @OptIn(UnstableApi::class)
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val sessionCommands = MediaSession.ConnectionResult
            .DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            .buildUpon()
            .add(SessionCommand(MEDIA3_COMMAND_TOGGLE_FAVORITE, Bundle.EMPTY))
            .add(SessionCommand(MEDIA3_COMMAND_CYCLE_PLAYBACK_MODE, Bundle.EMPTY))
            .add(SessionCommand(MEDIA3_COMMAND_STOP_AFTER_CURRENT, Bundle.EMPTY))
            .add(SessionCommand(MEDIA3_COMMAND_CLOSE_NOTIFICATION, Bundle.EMPTY))
            .add(SessionCommand(PlaybackServiceActions.ACTION_PREVIOUS, Bundle.EMPTY))
            .add(SessionCommand(PlaybackServiceActions.ACTION_NEXT, Bundle.EMPTY))
            .build()

        val playerCommands = MediaSession.ConnectionResult
            .DEFAULT_PLAYER_COMMANDS
            .buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    session.isMediaNotificationController(controller)) {
                    remove(Player.COMMAND_SEEK_TO_NEXT)
                    remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                    remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                }
            }.build()

        return MediaSession.ConnectionResult
            .AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .setAvailablePlayerCommands(playerCommands)
            .build()
    }

    @OptIn(UnstableApi::class)
    override fun onSetMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<androidx.media3.common.MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return libraryFuture { service.resolvePlaybackResumption() }
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        return when (customCommand.customAction) {
            PlaybackServiceActions.ACTION_PREVIOUS -> {
                service.playPrevious()
                successSessionResult()
            }
            PlaybackServiceActions.ACTION_NEXT -> {
                service.playNext()
                successSessionResult()
            }
            MEDIA3_COMMAND_TOGGLE_FAVORITE -> {
                service.toggleCurrentFavorite()
                successSessionResult()
            }

            MEDIA3_COMMAND_CYCLE_PLAYBACK_MODE -> {
                service.playbackModeResolver.cyclePlaybackMode()
                successSessionResult()
            }

            MEDIA3_COMMAND_CLOSE_NOTIFICATION -> {
                service.exitService()
                successSessionResult()
            }

            MEDIA3_COMMAND_STOP_AFTER_CURRENT -> {
                service.updateStopAfterCurrentTrackMode(!service.stopAfterCurrentTrack)
                service.publishAllRuntimeState(forceNotification = true)
                successSessionResult()
            }

            else -> unsupportedSessionResult()
        }
    }

    @Suppress("DEPRECATION")
    override fun onPlayerCommandRequest(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        playerCommand: Int
    ): Int {
        // Single-track ExoPlayer has no timeline next/prev — route like original BassPlayer.
        when (playerCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                service.playNext()
                return SessionResult.RESULT_INFO_SKIPPED
            }

            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                service.playPrevious()
                return SessionResult.RESULT_INFO_SKIPPED
            }

            Player.COMMAND_STOP -> {
                service.cancelPendingPlayback()
                service.pendingMedia3StopSnapshot = service.capturePlaybackSnapshot()
            }
        }

        return SessionResult.RESULT_SUCCESS
    }

    @OptIn(UnstableApi::class)
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return libraryFuture { service.resolvePlaybackResumption() }
    }

    override fun onMediaButtonEvent(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        intent: Intent
    ): Boolean {
        val event = intent.mediaButtonKeyEventOrNull()

        if (event?.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        service.headsetMediaButtonHandler.handle(event.keyCode)
        return true
    }

    override fun onPlayerInteractionFinished(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        playerCommands: Player.Commands
    ) {
        service.handleMedia3PlayerInteractionFinished()
    }
    private fun successSessionResult(): ListenableFuture<SessionResult> {
        return Futures.immediateFuture(
            SessionResult(SessionResult.RESULT_SUCCESS)
        )
    }

    @OptIn(UnstableApi::class)
    private fun unsupportedSessionResult(): ListenableFuture<SessionResult> {
        return Futures.immediateFuture(
            SessionResult(SessionError.ERROR_NOT_SUPPORTED)
        )
    }

    private fun Intent.mediaButtonKeyEventOrNull(): KeyEvent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(
                Intent.EXTRA_KEY_EVENT,
                KeyEvent::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }
    }

    override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
        params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(rootItem(), params))

    private fun rootItem(): MediaItem = MediaItem.Builder().setMediaId(ROOT)
        .setMediaMetadata(MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()).build()

    override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
        parentId: String, page: Int, pageSize: Int, params: LibraryParams?):
        ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = libraryFuture {
        if (parentId != ROOT || page < 0 || pageSize <= 0) {
            LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        } else {
            val recent = if (page == 0) service.recentPlaybackItem() else null
            LibraryResult.ofItemList(listOfNotNull(recent), params)
        }
    }

    override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
        mediaId: String): ListenableFuture<LibraryResult<MediaItem>> = libraryFuture {
        val item = if (mediaId == ROOT) rootItem() else service.recentPlaybackItem()
        if (item?.mediaId == mediaId) LibraryResult.ofItem(item, null)
        else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
    }

    private fun <T> libraryFuture(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        val job = service.serviceScope.launch {
            try { future.set(block()) }
            catch (cancelled: CancellationException) { future.cancel(false); throw cancelled }
            catch (error: Exception) { future.setException(error) }
        }
        future.addListener({ if (future.isCancelled) job.cancel() }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
        job.invokeOnCompletion { if (!future.isDone) future.cancel(false) }
        return future
    }

    private companion object { const val ROOT = "recent" }
}
