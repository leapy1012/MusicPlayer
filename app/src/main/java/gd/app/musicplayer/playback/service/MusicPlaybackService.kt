package gd.app.musicplayer.playback.service

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.core.common.dispatcher.AppDispatchers
import gd.app.musicplayer.core.common.extension.toMediaItemOrNull
import gd.app.musicplayer.core.database.dao.MusicDao
import gd.app.musicplayer.core.datastore.DesktopLyricPreference
import gd.app.musicplayer.core.datastore.DesktopLyricPreferenceStore
import gd.app.musicplayer.core.datastore.PlaybackStatePreferenceStore
import gd.app.musicplayer.core.datastore.SettingPreferences
import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import gd.app.musicplayer.core.datastore.SoundEffectPreferences
import gd.app.musicplayer.core.datastore.StatusBarLyricPreferenceStore
import gd.app.musicplayer.core.datastore.TrackLyricPreferenceStore
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import gd.app.musicplayer.domain.repository.PlaylistRepo
import gd.app.musicplayer.domain.usecase.library.ObserveAlbumPictureUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import gd.app.musicplayer.domain.usecase.playlist.ToggleFavoriteTrackUseCase
import gd.app.musicplayer.feature.widget.provider.WidgetUpdateCoordinator
import gd.app.musicplayer.playback.AudioFocusController
import gd.app.musicplayer.playback.PlaybackModeResolver
import gd.app.musicplayer.playback.PlaybackStatsTracker
import gd.app.musicplayer.playback.artwork.ArtworkLoader
import gd.app.musicplayer.playback.artwork.CurrentArtworkController
import gd.app.musicplayer.playback.command.IndexActionData
import gd.app.musicplayer.playback.command.PlaybackCommandHandler
import gd.app.musicplayer.playback.command.PlaybackCommandPayloadStore
import gd.app.musicplayer.playback.command.PlaybackServiceActions
import gd.app.musicplayer.playback.command.PlaybackServiceExtras
import gd.app.musicplayer.playback.command.PlaybackServiceStarter
import gd.app.musicplayer.playback.desktop.DesktopLyricsOverlayController
import gd.app.musicplayer.playback.effects.AudioEffectsManager
import gd.app.musicplayer.playback.effects.PlaybackTuningController
import gd.app.musicplayer.playback.effects.StereoBalanceAudioProcessor
import gd.app.musicplayer.playback.effects.VolumeFader
import gd.app.musicplayer.playback.favorite.CurrentFavoriteController
import gd.app.musicplayer.playback.headset.HeadsetMediaButtonHandler
import gd.app.musicplayer.playback.headset.ScreenOffLockReceiver
import gd.app.musicplayer.playback.notification.NotificationCloseController
import gd.app.musicplayer.playback.notification.NotificationMediaSessionBridge
import gd.app.musicplayer.playback.notification.PlaybackNotificationController
import gd.app.musicplayer.playback.player.MusicPlayerFactory
import gd.app.musicplayer.playback.player.PlaybackEngine
import gd.app.musicplayer.playback.player.PlayerEventHandler
import gd.app.musicplayer.playback.progress.PlaybackProgressTicker
import gd.app.musicplayer.playback.queue.PlaybackQueueManager
import gd.app.musicplayer.playback.queue.PlayerQueueController
import gd.app.musicplayer.playback.queue.QueueActionController
import gd.app.musicplayer.playback.restore.PlaybackRestoreManager
import gd.app.musicplayer.playback.shutdown.PlaybackShutdownCoordinator
import gd.app.musicplayer.playback.shutdown.ShutdownController
import gd.app.musicplayer.playback.state.PlaybackRuntimeStateStore
import gd.app.musicplayer.playback.state.PlaybackSnapshot
import gd.app.musicplayer.playback.state.PlaybackSnapshotManager
import gd.app.musicplayer.playback.state.PlaybackStateOrchestrator
import gd.app.musicplayer.playback.state.PlaybackStatePublisher
import gd.app.musicplayer.playback.state.PlaybackStateUpdateCoordinator
import gd.app.musicplayer.playback.statusbar.StatusBarLyricsOverlayController
import gd.app.musicplayer.playback.transition.TimedTransitionController
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MusicPlaybackService : MediaSessionService() {

    // ---------------------------------------------------------------------
    // Injected dependencies
    // ---------------------------------------------------------------------

    @Inject lateinit var musicPlayerFactory: MusicPlayerFactory
    @Inject lateinit var queueManager: PlaybackQueueManager
    @Inject lateinit var playbackRuntimeStateStore: PlaybackRuntimeStateStore
    @Inject lateinit var playbackQueueRepo: PlaybackQueueRepo
    @Inject lateinit var playlistRepo: PlaylistRepo
    @Inject lateinit var desktopLyricPreferenceStore: DesktopLyricPreferenceStore
    @Inject lateinit var statusBarLyricPreferenceStore: StatusBarLyricPreferenceStore
    @Inject lateinit var trackLyricPreferenceStore: TrackLyricPreferenceStore
    @Inject lateinit var playbackStatePreferenceStore: PlaybackStatePreferenceStore
    @Inject lateinit var settingPreferencesDataStore: SettingPreferencesDataStore
    @Inject lateinit var headsetMediaButtonHandler: HeadsetMediaButtonHandler
    @Inject lateinit var audioEffectsManager: AudioEffectsManager
    @Inject lateinit var soundEffectPreferences: SoundEffectPreferences
    @Inject lateinit var dispatchers: AppDispatchers
    @Inject lateinit var observeTracksUseCase: ObserveTracksUseCase
    @Inject lateinit var observeAlbumPictureUseCase: ObserveAlbumPictureUseCase
    @Inject lateinit var toggleFavoriteTrackUseCase: ToggleFavoriteTrackUseCase
    @Inject lateinit var musicDao: MusicDao
    @Inject lateinit var playbackStatsTracker: PlaybackStatsTracker
    @Inject lateinit var widgetUpdateCoordinator: WidgetUpdateCoordinator
    @Inject lateinit var commandPayloadStore: PlaybackCommandPayloadStore

    // ---------------------------------------------------------------------
    // Runtime collaborators
    // ---------------------------------------------------------------------

    internal lateinit var snapshotManager: PlaybackSnapshotManager
    internal lateinit var restoreManager: PlaybackRestoreManager
    internal lateinit var playerEventHandler: PlayerEventHandler
    internal lateinit var playbackEngine: PlaybackEngine
    internal lateinit var timedTransitionController: TimedTransitionController
    internal lateinit var favoriteController: CurrentFavoriteController
    internal lateinit var artworkController: CurrentArtworkController
    internal lateinit var stateOrchestrator: PlaybackStateOrchestrator
    internal lateinit var stateUpdateCoordinator: PlaybackStateUpdateCoordinator
    internal lateinit var playerQueueController: PlayerQueueController
    internal lateinit var queueActionController: QueueActionController
    internal lateinit var shutdownController: ShutdownController
    internal lateinit var shutdownCoordinator: PlaybackShutdownCoordinator
    internal lateinit var notificationCloseController: NotificationCloseController
    internal lateinit var desktopLyricsController: DesktopLyricsOverlayController
    internal lateinit var statusBarLyricsController: StatusBarLyricsOverlayController

    internal lateinit var player: ExoPlayer
    internal lateinit var crossfadePlayer: ExoPlayer
    internal lateinit var serviceScope: CoroutineScope
    internal lateinit var defaultArtwork: Bitmap

    internal lateinit var audioFocusController: AudioFocusController
    internal lateinit var artworkLoader: ArtworkLoader
    internal lateinit var commandHandler: PlaybackCommandHandler
    internal lateinit var playbackServiceRuntime: PlaybackServiceRuntime
    internal lateinit var lifecycleController: PlaybackLifecycleController
    internal lateinit var notificationSessionBridge: NotificationMediaSessionBridge
    internal lateinit var notificationController: PlaybackNotificationController
    internal lateinit var playbackModeResolver: PlaybackModeResolver
    internal lateinit var playbackTuningController: PlaybackTuningController
    internal lateinit var screenOffLockReceiver: ScreenOffLockReceiver
    internal lateinit var statePublisher: PlaybackStatePublisher
    internal lateinit var stereoBalanceAudioProcessor: StereoBalanceAudioProcessor
    internal lateinit var crossfadeStereoBalanceAudioProcessor: StereoBalanceAudioProcessor
    internal lateinit var volumeFader: VolumeFader
    internal lateinit var crossfadeVolumeFader: VolumeFader
    internal lateinit var progressTicker: PlaybackProgressTicker

    internal var media3Session: MediaSession? = null

    // ---------------------------------------------------------------------
    // Runtime state
    // ---------------------------------------------------------------------

    internal var resumeJob: Job? = null
    internal var defaultQueueRestoreJob: Job? = null
    internal var widgetUpdateJob: Job? = null

    @Volatile
    internal var pendingResumeAfterDefaultQueue = false

    internal var screenReceiverRegistered = false
    internal var keepIdleNotification = false
    internal var stopAfterCurrentTrack = false
    internal var notificationDismissedByUser = false
    internal var lastSessionAutoSaveElapsedMs = 0L

    internal var pendingMedia3TransportCommand = NO_PLAYER_COMMAND
    internal var pendingMedia3TransportStartIndex = NO_INDEX
    internal var pendingMedia3TransportStartPositionMs = 0L
    internal var pendingMedia3StopSnapshot: PlaybackSnapshot? = null

    internal var correctingMediaItemTransition = false
    internal var suppressNextCrossfadeCommitTransition = false
    internal var pendingRestoreTrackId: Long? = null
    internal var isNightMode = false

    @Volatile
    internal var latestSettingPreferences = SettingPreferences()

    @Volatile
    internal var latestDesktopLyricPreference = DesktopLyricPreference()

    internal val queue: List<Music>
        get() = queueManager.queue

    internal val currentIndex: Int
        get() = queueManager.currentIndex

    // ---------------------------------------------------------------------
    // Android service lifecycle
    // ---------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()

        prepareServiceBaseState()

        lifecycleController = createLifecycleController()
        lifecycleController.onCreate()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? {
        return media3Session
    }

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean
    ) {
        if (!isNotificationControllerInitialized()) return

        if (startInForegroundRequired) {
            notificationDismissedByUser = false
            notificationController.ensureForegroundStarted()
        } else {
            updateNotification()
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        super.onStartCommand(
            intent,
            flags,
            startId
        )

        return if (isPlaybackServiceRuntimeInitialized()) {
            playbackServiceRuntime.handleStartCommand(
                intent = intent,
                startId = startId
            )
        } else {
            handleEmptyStartCommand(startId)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (isLifecycleControllerInitialized()) {
            lifecycleController.onTaskRemoved()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (isLifecycleControllerInitialized()) {
            lifecycleController.onConfigurationChanged(newConfig)
        }
    }

    override fun onDestroy() {
        if (isLifecycleControllerInitialized()) {
            lifecycleController.onDestroy()
        }

        super.onDestroy()
    }

    // ---------------------------------------------------------------------
    // Media3 session callback
    // ---------------------------------------------------------------------

    @OptIn(UnstableApi::class)
    internal val media3SessionCallback: MediaSession.Callback = @UnstableApi
    object : MediaSession.Callback {

        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult
                .DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(SessionCommand(MEDIA3_COMMAND_TOGGLE_FAVORITE, Bundle.EMPTY))
                .add(SessionCommand(MEDIA3_COMMAND_CYCLE_PLAYBACK_MODE, Bundle.EMPTY))
                .add(SessionCommand(MEDIA3_COMMAND_STOP_AFTER_CURRENT, Bundle.EMPTY))
                .add(SessionCommand(MEDIA3_COMMAND_CLOSE_NOTIFICATION, Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult
                .AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return when (customCommand.customAction) {
                MEDIA3_COMMAND_TOGGLE_FAVORITE -> {
                    toggleCurrentFavorite()
                    successSessionResult()
                }

                MEDIA3_COMMAND_CYCLE_PLAYBACK_MODE -> {
                    playbackModeResolver.cyclePlaybackMode()
                    successSessionResult()
                }

                MEDIA3_COMMAND_CLOSE_NOTIFICATION -> {
                    pauseAndPersistForNotificationClose()
                    successSessionResult()
                }

                MEDIA3_COMMAND_STOP_AFTER_CURRENT -> {
                    updateStopAfterCurrentTrackMode(!stopAfterCurrentTrack)
                    publishAllRuntimeState(forceNotification = true)
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
            when {
                playerCommand.isMedia3TransportNavigationCommand() -> {
                    pendingMedia3TransportCommand = playerCommand
                    pendingMedia3TransportStartIndex = currentIndex
                    pendingMedia3TransportStartPositionMs =
                        player.currentPosition.coerceAtLeast(0L)
                }

                playerCommand == Player.COMMAND_STOP -> {
                    pendingMedia3StopSnapshot = capturePlaybackSnapshot()
                }
            }

            return SessionResult.RESULT_SUCCESS
        }

        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()

            serviceScope.launch {
                runCatching {
                    resolvePlaybackResumption(isForPlayback)
                }.onSuccess { result ->
                    future.set(result)
                }.onFailure { error ->
                    future.setException(error)
                }
            }

            return future
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

            headsetMediaButtonHandler.handle(event.keyCode)
            return true
        }

        override fun onPlayerInteractionFinished(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            playerCommands: Player.Commands
        ) {
            handleMedia3PlayerInteractionFinished()
        }
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

    @OptIn(UnstableApi::class)
    private suspend fun resolvePlaybackResumption(
        isForPlayback: Boolean
    ): MediaSession.MediaItemsWithStartPosition {
        ensurePlaybackRestored()

        if (isForPlayback && !audioFocusController.request()) {
            throw IllegalStateException("Audio focus request was denied.")
        }

        val mediaItems = queue.mapNotNull { music ->
            music.toMediaItemOrNull()
        }

        if (mediaItems.isEmpty()) {
            throw UnsupportedOperationException("No restorable media items.")
        }

        val startIndex = currentIndex.coerceIn(
            minimumValue = 0,
            maximumValue = mediaItems.lastIndex
        )

        return MediaSession.MediaItemsWithStartPosition(
            mediaItems,
            startIndex,
            resolveCurrentSnapshotPositionMs()
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

    // ---------------------------------------------------------------------
    // Lateinit guards for extracted service extension files
    // ---------------------------------------------------------------------
    //
    // Kotlin only allows ::property.isInitialized access inside the class
    // that owns the lateinit backing field. Extracted extension files should
    // call these helpers instead of checking ::property.isInitialized directly.

    internal fun isSnapshotManagerInitialized(): Boolean =
        ::snapshotManager.isInitialized

    internal fun isRestoreManagerInitialized(): Boolean =
        ::restoreManager.isInitialized

    internal fun isPlayerEventHandlerInitialized(): Boolean =
        ::playerEventHandler.isInitialized

    internal fun isPlaybackEngineInitialized(): Boolean =
        ::playbackEngine.isInitialized

    internal fun isTimedTransitionControllerInitialized(): Boolean =
        ::timedTransitionController.isInitialized

    internal fun isFavoriteControllerInitialized(): Boolean =
        ::favoriteController.isInitialized

    internal fun isArtworkControllerInitialized(): Boolean =
        ::artworkController.isInitialized

    internal fun isStateOrchestratorInitialized(): Boolean =
        ::stateOrchestrator.isInitialized

    internal fun isStateUpdateCoordinatorInitialized(): Boolean =
        ::stateUpdateCoordinator.isInitialized

    internal fun isPlayerQueueControllerInitialized(): Boolean =
        ::playerQueueController.isInitialized

    internal fun isQueueActionControllerInitialized(): Boolean =
        ::queueActionController.isInitialized

    internal fun isShutdownControllerInitialized(): Boolean =
        ::shutdownController.isInitialized

    internal fun isShutdownCoordinatorInitialized(): Boolean =
        ::shutdownCoordinator.isInitialized

    internal fun isNotificationCloseControllerInitialized(): Boolean =
        ::notificationCloseController.isInitialized

    internal fun isDesktopLyricsControllerInitialized(): Boolean =
        ::desktopLyricsController.isInitialized

    internal fun isStatusBarLyricsControllerInitialized(): Boolean =
        ::statusBarLyricsController.isInitialized

    internal fun isPlayerInitialized(): Boolean =
        ::player.isInitialized

    internal fun isCrossfadePlayerInitialized(): Boolean =
        ::crossfadePlayer.isInitialized

    internal fun isServiceScopeInitialized(): Boolean =
        ::serviceScope.isInitialized

    internal fun isDefaultArtworkInitialized(): Boolean =
        ::defaultArtwork.isInitialized

    internal fun isAudioFocusControllerInitialized(): Boolean =
        ::audioFocusController.isInitialized

    internal fun isArtworkLoaderInitialized(): Boolean =
        ::artworkLoader.isInitialized

    internal fun isCommandHandlerInitialized(): Boolean =
        ::commandHandler.isInitialized

    internal fun isPlaybackServiceRuntimeInitialized(): Boolean =
        ::playbackServiceRuntime.isInitialized

    internal fun isLifecycleControllerInitialized(): Boolean =
        ::lifecycleController.isInitialized

    internal fun isNotificationSessionBridgeInitialized(): Boolean =
        ::notificationSessionBridge.isInitialized

    internal fun isNotificationControllerInitialized(): Boolean =
        ::notificationController.isInitialized

    internal fun isPlaybackModeResolverInitialized(): Boolean =
        ::playbackModeResolver.isInitialized

    internal fun isPlaybackTuningControllerInitialized(): Boolean =
        ::playbackTuningController.isInitialized

    internal fun isScreenOffLockReceiverInitialized(): Boolean =
        ::screenOffLockReceiver.isInitialized

    internal fun isStatePublisherInitialized(): Boolean =
        ::statePublisher.isInitialized

    internal fun isStereoBalanceAudioProcessorInitialized(): Boolean =
        ::stereoBalanceAudioProcessor.isInitialized

    internal fun isCrossfadeStereoBalanceAudioProcessorInitialized(): Boolean =
        ::crossfadeStereoBalanceAudioProcessor.isInitialized

    internal fun isVolumeFaderInitialized(): Boolean =
        ::volumeFader.isInitialized

    internal fun isCrossfadeVolumeFaderInitialized(): Boolean =
        ::crossfadeVolumeFader.isInitialized

    internal fun isProgressTickerInitialized(): Boolean =
        ::progressTicker.isInitialized

    internal fun isPlaybackRuntimeStateStoreInitialized(): Boolean =
        ::playbackRuntimeStateStore.isInitialized

    internal fun isWidgetUpdateCoordinatorInitialized(): Boolean =
        ::widgetUpdateCoordinator.isInitialized

    internal fun isAudioEffectsManagerInitialized(): Boolean =
        ::audioEffectsManager.isInitialized

    companion object {

        @Volatile
        var isRunning: Boolean = false
            internal set

        internal const val NO_INDEX = -1
        internal const val NO_TRACK_ID = Long.MIN_VALUE
        internal const val NO_PLAYER_COMMAND = -1

        internal const val MEDIA3_COMMAND_TOGGLE_FAVORITE =
            "gd.app.musicplayer.media3.TOGGLE_FAVORITE"
        internal const val MEDIA3_COMMAND_CYCLE_PLAYBACK_MODE =
            "gd.app.musicplayer.media3.CYCLE_PLAYBACK_MODE"
        internal const val MEDIA3_COMMAND_STOP_AFTER_CURRENT =
            "gd.app.musicplayer.media3.STOP_AFTER_CURRENT"
        internal const val MEDIA3_COMMAND_CLOSE_NOTIFICATION =
            "gd.app.musicplayer.media3.CLOSE_NOTIFICATION"
    }
}