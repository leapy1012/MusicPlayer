package gd.app.musicplayer.playback.service

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.core.common.dispatcher.AppDispatchers
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
import gd.app.musicplayer.domain.usecase.playback.RemapQueueIndexUseCase
import gd.app.musicplayer.domain.usecase.playlist.ToggleFavoriteTrackUseCase
import gd.app.musicplayer.feature.widget.provider.WidgetUpdateCoordinator
import gd.app.musicplayer.playback.AudioFocusController
import gd.app.musicplayer.playback.PlaybackModeResolver
import gd.app.musicplayer.playback.PlaybackStatsTracker
import gd.app.musicplayer.playback.artwork.ArtworkLoader
import gd.app.musicplayer.playback.artwork.CurrentArtworkController
import gd.app.musicplayer.playback.command.PlaybackCommandHandler
import gd.app.musicplayer.playback.command.PlaybackCommandPayloadStore
import gd.app.musicplayer.playback.desktop.DesktopLyricsOverlayController
import gd.app.musicplayer.playback.effects.AudioEffectsManager
import gd.app.musicplayer.playback.effects.PlaybackTuningController
import gd.app.musicplayer.playback.effects.StereoBalanceAudioProcessor
import gd.app.musicplayer.playback.effects.VolumeFader
import gd.app.musicplayer.playback.favorite.CurrentFavoriteController
import gd.app.musicplayer.playback.headset.HeadsetMediaButtonHandler
import gd.app.musicplayer.playback.lock.LockScreenController
import gd.app.musicplayer.playback.notification.NotificationCloseController
import gd.app.musicplayer.playback.player.PlaybackSessionMetadata
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
import gd.app.musicplayer.playback.widget.PlaybackWidgetBridge
import gd.app.musicplayer.playback.state.PlaybackStateUpdateCoordinator
import gd.app.musicplayer.playback.statusbar.StatusBarLyricsOverlayController
import gd.app.musicplayer.playback.transition.TimedTransitionController
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

@AndroidEntryPoint
class MusicPlaybackService : MediaLibraryService() {

    // ---------------------------------------------------------------------
    // Injected dependencies
    // ---------------------------------------------------------------------

    @Inject lateinit var quitCoordinator: gd.app.musicplayer.playback.shutdown.PlaybackQuitCoordinator
    @Inject lateinit var musicPlayerFactory: MusicPlayerFactory
    @Inject lateinit var processPlayerHolder: gd.app.musicplayer.playback.ProcessPlayerHolder
    @Inject lateinit var queueManager: PlaybackQueueManager
    @Inject lateinit var playbackRuntimeStateStore: PlaybackRuntimeStateStore
    @Inject lateinit var resumeLoader: gd.app.musicplayer.playback.restore.PlaybackResumeLoader
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
    @Inject lateinit var remapQueueIndexUseCase: RemapQueueIndexUseCase
    @Inject lateinit var musicDao: MusicDao
    @Inject lateinit var playbackStatsTracker: PlaybackStatsTracker
    @Inject lateinit var widgetUpdateCoordinator: WidgetUpdateCoordinator
    @Inject lateinit var commandPayloadStore: PlaybackCommandPayloadStore
    @Inject lateinit var lockScreenController: LockScreenController
    @Inject lateinit var playbackFadeController: gd.app.musicplayer.playback.effects.PlaybackFadeController

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
    internal lateinit var sessionPlayer: gd.app.musicplayer.playback.player.QueueAwareSessionPlayer
    internal lateinit var sessionMetadata: PlaybackSessionMetadata
    internal lateinit var notificationController: PlaybackNotificationController
    internal lateinit var playbackModeResolver: PlaybackModeResolver
    internal lateinit var playbackTuningController: PlaybackTuningController
    internal lateinit var statePublisher: PlaybackStatePublisher
    internal lateinit var widgetBridge: PlaybackWidgetBridge
    internal lateinit var stereoBalanceAudioProcessor: StereoBalanceAudioProcessor
    internal lateinit var crossfadeStereoBalanceAudioProcessor: StereoBalanceAudioProcessor
    internal lateinit var volumeFader: VolumeFader
    internal lateinit var crossfadeVolumeFader: VolumeFader
    internal lateinit var progressTicker: PlaybackProgressTicker

    internal var media3Session: MediaLibrarySession? = null
    internal val playbackRequests = PlaybackRequestGate()
    internal var playbackErrorJob: Job? = null
    internal val failedPlaybackIds = mutableSetOf<Long>()

    // ---------------------------------------------------------------------
    // Runtime state
    // ---------------------------------------------------------------------

    internal var resumeJob: Job? = null
    internal var playbackShuttingDown = false

    @Volatile
    internal var deferForcedStartupUiUpdates = false
    @Volatile

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
        quitCoordinator.onServiceCreated(this) { exitService() }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaLibrarySession? {
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
        quitCoordinator.onServiceDestroyed(this)
    }

    // ---------------------------------------------------------------------
    // Media3 session callback
    // ---------------------------------------------------------------------

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

    internal fun isSessionMetadataInitialized(): Boolean =
        ::sessionMetadata.isInitialized

    internal fun isNotificationControllerInitialized(): Boolean =
        ::notificationController.isInitialized

    internal fun isPlaybackModeResolverInitialized(): Boolean =
        ::playbackModeResolver.isInitialized

    internal fun isPlaybackTuningControllerInitialized(): Boolean =
        ::playbackTuningController.isInitialized

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

    internal fun isWidgetBridgeInitialized(): Boolean =
        ::widgetBridge.isInitialized

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
