package gd.app.musicplayer.playback.service

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.core.common.dispatcher.AppDispatchers
import gd.app.musicplayer.core.database.dao.MusicDao
import gd.app.musicplayer.core.datastore.DesktopLyricPreferenceStore
import gd.app.musicplayer.core.datastore.PlaybackStatePreferenceStore
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
import gd.app.musicplayer.playback.session.Media3Commands
import gd.app.musicplayer.playback.session.Media3CommandHandler
import gd.app.musicplayer.playback.session.Media3TransportCommandTracker
import gd.app.musicplayer.playback.session.MusicMediaSessionCallback
import gd.app.musicplayer.playback.session.PlaybackResumptionHandler
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

@AndroidEntryPoint
class MusicPlaybackService : MediaSessionService() {

    @Inject internal lateinit var sessionFactory: PlaybackSessionFactory

    private var playbackSession: PlaybackSession? = null

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
    @Inject lateinit var remapQueueIndexUseCase: RemapQueueIndexUseCase
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
    internal lateinit var playbackEventDispatcher: PlaybackEventDispatcher
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
    internal lateinit var media3CommandHandler: Media3CommandHandler
    internal lateinit var playbackResumptionHandler: PlaybackResumptionHandler
    internal lateinit var media3TransportCommandTracker: Media3TransportCommandTracker
    internal lateinit var media3SessionCallbackDelegate: MusicMediaSessionCallback

    internal var media3Session: MediaSession? = null

    // ---------------------------------------------------------------------
    // Runtime state
    // ---------------------------------------------------------------------

    internal val jobState = PlaybackJobState()
    internal val startupState = PlaybackStartupState()
    internal val media3TransportState = Media3TransportState()
    internal val sessionFlags = PlaybackSessionFlagsState()
    internal val runtimeCacheState = PlaybackRuntimeCacheState()

    internal val queue: List<Music>
        get() = queueManager.queue

    internal val currentIndex: Int
        get() = queueManager.currentIndex

    // ---------------------------------------------------------------------
    // Android service lifecycle
    // ---------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        playbackSession = sessionFactory.create(this).also { session ->
            session.start()
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? {
        return playbackSession?.mediaSession()
    }

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean
    ) {
        playbackSession?.onUpdateNotification(
            session = session,
            startInForegroundRequired = startInForegroundRequired
        )
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

        return playbackSession?.onStartCommand(
            intent = intent,
            startId = startId
        ) ?: handleEmptyStartCommand(startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        playbackSession?.onTaskRemoved()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        playbackSession?.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        playbackSession?.stop()
        playbackSession = null
        super.onDestroy()
    }

    @OptIn(UnstableApi::class)
    internal val media3SessionCallback: MediaSession.Callback
        get() = media3SessionCallbackDelegate

    // ---------------------------------------------------------------------
    // Lateinit guards for extracted service extension files
    // ---------------------------------------------------------------------
    //
    // Kotlin only allows ::property.isInitialized access inside the class
    // that owns the lateinit backing field. Extracted extension files should
    // call these helpers instead of checking ::property.isInitialized directly.

    internal fun playerOrNull(): ExoPlayer? =
        if (::player.isInitialized) player else null

    internal fun crossfadePlayerOrNull(): ExoPlayer? =
        if (::crossfadePlayer.isInitialized) crossfadePlayer else null

    internal fun playerEventHandlerOrNull(): PlayerEventHandler? =
        if (::playerEventHandler.isInitialized) playerEventHandler else null

    internal fun playbackEngineOrNull(): PlaybackEngine? =
        if (::playbackEngine.isInitialized) playbackEngine else null

    internal fun audioFocusControllerOrNull(): AudioFocusController? =
        if (::audioFocusController.isInitialized) audioFocusController else null

    internal fun timedTransitionControllerOrNull(): TimedTransitionController? =
        if (::timedTransitionController.isInitialized) timedTransitionController else null

    internal fun volumeFaderOrNull(): VolumeFader? =
        if (::volumeFader.isInitialized) volumeFader else null

    internal fun crossfadeVolumeFaderOrNull(): VolumeFader? =
        if (::crossfadeVolumeFader.isInitialized) crossfadeVolumeFader else null

    internal fun artworkControllerOrNull(): CurrentArtworkController? =
        if (::artworkController.isInitialized) artworkController else null

    internal fun artworkLoaderOrNull(): ArtworkLoader? =
        if (::artworkLoader.isInitialized) artworkLoader else null

    internal fun audioEffectsManagerOrNull(): AudioEffectsManager? =
        if (::audioEffectsManager.isInitialized) audioEffectsManager else null

    internal fun serviceScopeOrNull(): CoroutineScope? =
        if (::serviceScope.isInitialized) serviceScope else null

    internal fun lifecycleControllerOrNull(): PlaybackLifecycleController? =
        if (::lifecycleController.isInitialized) lifecycleController else null

    internal fun playbackServiceRuntimeOrNull(): PlaybackServiceRuntime? =
        if (::playbackServiceRuntime.isInitialized) playbackServiceRuntime else null

    internal fun playbackRuntimeStateStoreOrNull(): PlaybackRuntimeStateStore? =
        if (::playbackRuntimeStateStore.isInitialized) playbackRuntimeStateStore else null

    internal fun stateOrchestratorOrNull(): PlaybackStateOrchestrator? =
        if (::stateOrchestrator.isInitialized) stateOrchestrator else null

    internal fun stateUpdateCoordinatorOrNull(): PlaybackStateUpdateCoordinator? =
        if (::stateUpdateCoordinator.isInitialized) stateUpdateCoordinator else null

    internal fun playbackEventDispatcherOrNull(): PlaybackEventDispatcher? =
        if (::playbackEventDispatcher.isInitialized) playbackEventDispatcher else null

    internal fun shutdownControllerOrNull(): ShutdownController? =
        if (::shutdownController.isInitialized) shutdownController else null

    internal fun notificationControllerOrNull(): PlaybackNotificationController? =
        if (::notificationController.isInitialized) notificationController else null

    internal fun notificationSessionBridgeOrNull(): NotificationMediaSessionBridge? =
        if (::notificationSessionBridge.isInitialized) notificationSessionBridge else null

    internal fun widgetUpdateCoordinatorOrNull(): WidgetUpdateCoordinator? =
        if (::widgetUpdateCoordinator.isInitialized) widgetUpdateCoordinator else null

    internal fun queueActionControllerOrNull(): QueueActionController? =
        if (::queueActionController.isInitialized) queueActionController else null

    internal fun playerQueueControllerOrNull(): PlayerQueueController? =
        if (::playerQueueController.isInitialized) playerQueueController else null

    internal fun favoriteControllerOrNull(): CurrentFavoriteController? =
        if (::favoriteController.isInitialized) favoriteController else null

    internal fun notificationCloseControllerOrNull(): NotificationCloseController? =
        if (::notificationCloseController.isInitialized) notificationCloseController else null

    internal fun shutdownCoordinatorOrNull(): PlaybackShutdownCoordinator? =
        if (::shutdownCoordinator.isInitialized) shutdownCoordinator else null

    internal fun playbackModeResolverOrNull(): PlaybackModeResolver? =
        if (::playbackModeResolver.isInitialized) playbackModeResolver else null

    internal fun playbackTuningControllerOrNull(): PlaybackTuningController? =
        if (::playbackTuningController.isInitialized) playbackTuningController else null

    internal fun progressTickerOrNull(): PlaybackProgressTicker? =
        if (::progressTicker.isInitialized) progressTicker else null

    internal fun desktopLyricsControllerOrNull(): DesktopLyricsOverlayController? =
        if (::desktopLyricsController.isInitialized) desktopLyricsController else null

    internal fun statusBarLyricsControllerOrNull(): StatusBarLyricsOverlayController? =
        if (::statusBarLyricsController.isInitialized) statusBarLyricsController else null

    internal fun playbackSessionOrNull(): PlaybackSession? = playbackSession

    companion object {

        @Volatile
        var isRunning: Boolean = false
            internal set
    }
}
