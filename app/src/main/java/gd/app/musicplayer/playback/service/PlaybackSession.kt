package gd.app.musicplayer.playback.service

import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.toMediaItemOrNull
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.feature.widget.provider.WidgetPlaybackSnapshot
import gd.app.musicplayer.playback.AudioFocusController
import gd.app.musicplayer.playback.PlaybackModeResolver
import gd.app.musicplayer.playback.artwork.ArtworkLoader
import gd.app.musicplayer.playback.artwork.CurrentArtworkController
import gd.app.musicplayer.playback.command.PlaybackCommandHandler
import gd.app.musicplayer.playback.effects.PlaybackTuningController
import gd.app.musicplayer.playback.favorite.CurrentFavoriteController
import gd.app.musicplayer.playback.headset.ScreenOffLockReceiver
import gd.app.musicplayer.playback.notification.NotificationMediaSessionBridge
import gd.app.musicplayer.playback.notification.PlaybackNotificationController
import gd.app.musicplayer.playback.effects.VolumeFader
import gd.app.musicplayer.playback.progress.PlaybackProgressTicker
import gd.app.musicplayer.playback.queue.PlayerQueueController
import gd.app.musicplayer.playback.queue.QueueActionController
import gd.app.musicplayer.playback.queue.QueueMutationCallbacks
import gd.app.musicplayer.playback.shutdown.PlaybackShutdownCoordinator
import gd.app.musicplayer.playback.shutdown.ShutdownController
import gd.app.musicplayer.playback.shutdown.ShutdownOptions
import gd.app.musicplayer.playback.session.Media3CommandHandler
import gd.app.musicplayer.playback.session.Media3Commands
import gd.app.musicplayer.playback.session.Media3TransportCommandTracker
import gd.app.musicplayer.playback.session.MusicMediaSessionCallback
import gd.app.musicplayer.playback.session.PlaybackResumptionHandler
import gd.app.musicplayer.playback.state.PlaybackSnapshot
import gd.app.musicplayer.playback.state.PlaybackStateOrchestrator
import gd.app.musicplayer.playback.state.PlaybackStatePublisher
import gd.app.musicplayer.playback.state.PlaybackStateUpdateCoordinator
import gd.app.musicplayer.playback.restore.PlaybackRestoreManager
import gd.app.musicplayer.playback.state.PublishReason
import gd.app.musicplayer.playback.state.PlaybackSnapshotManager
import gd.app.musicplayer.playback.transition.TimedTransitionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import gd.app.musicplayer.ui.shell.MainActivity

internal class PlaybackSession(
    private val service: MusicPlaybackService
) {

    fun start() {
        prepareBaseState()
        initializeMedia3SessionDelegates()
        service.lifecycleController = createLifecycleController()
        service.lifecycleController.onCreate()
    }

    fun mediaSession(): MediaSession? {
        return service.media3Session
    }

    fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean
    ) {
        val notificationController = service.notificationControllerOrNull() ?: return
        if (startInForegroundRequired) {
            service.sessionFlags.notificationDismissedByUser = false
            notificationController.ensureForegroundStarted()
        } else {
            service.updateNotification()
        }
    }

    fun onStartCommand(
        intent: Intent?,
        startId: Int
    ): Int {
        return service.playbackServiceRuntimeOrNull()?.handleStartCommand(
            intent = intent,
            startId = startId
        ) ?: service.handleEmptyStartCommand(startId)
    }

    fun onTaskRemoved() {
        service.lifecycleControllerOrNull()?.onTaskRemoved()
    }

    fun onConfigurationChanged(configuration: Configuration) {
        service.lifecycleControllerOrNull()?.onConfigurationChanged(configuration)
    }

    fun stop() {
        service.lifecycleControllerOrNull()?.onDestroy()
    }

    private fun prepareBaseState() {
        MusicPlaybackService.isRunning = true
        service.runtimeCacheState.isNightMode = isNightMode(
            service.resources.configuration
        )

        service.serviceScope = CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate
        )

        service.defaultArtwork = BitmapFactory.decodeResource(
            service.resources,
            R.drawable.notify_default_album
        )

        service.jobState.defaultTracksObserverJob =
            service.serviceScope.launch(service.dispatchers.io) {
                service.observeTracksUseCase(MusicSet.Tracks).collectLatest { tracks ->
                    service.startupState.cachedDefaultTracks = tracks
                    service.startupState.cachedPlayableDefaultTracks = tracks.filter { music ->
                        music.toMediaItemOrNull() != null
                    }
                }
            }
    }

    private fun createLifecycleController(): PlaybackLifecycleController {
        return PlaybackLifecycleController(
            callbacks = MusicPlaybackServiceLifecycleActions(service)
        )
    }

    internal fun createCommandHandler(): PlaybackCommandHandler {
        return PlaybackCommandHandler(
            payloadStore = service.commandPayloadStore,
            callbacks = MusicPlaybackServiceActions(
                service = service,
                serviceScope = service.serviceScope
            )
        )
    }

    internal fun createPlaybackServiceRuntime(): PlaybackServiceRuntime {
        return PlaybackServiceRuntime(
            commandHandler = service.commandHandler,
            callbacks = MusicPlaybackServiceRuntimeActions(service)
        )
    }

    internal fun createStateUpdateCoordinator(): PlaybackStateUpdateCoordinator {
        return PlaybackStateUpdateCoordinator(
            callbacks = object : PlaybackStateUpdateCoordinator.Callbacks {
                override fun stateOrchestratorOrNull(): PlaybackStateOrchestrator? {
                    return service.stateOrchestratorOrNull()
                }

                override fun runtimeWidgetSnapshot(): WidgetPlaybackSnapshot {
                    return service.runtimeWidgetSnapshot()
                }

                override fun shutdownWidgetSnapshot(snapshot: PlaybackSnapshot?): WidgetPlaybackSnapshot {
                    return service.shutdownWidgetSnapshot(snapshot)
                }

                override fun updateWidgets(snapshot: WidgetPlaybackSnapshot) {
                    service.updateWidgetSnapshot(snapshot)
                }

                override fun updateWidgetsBlocking(snapshot: WidgetPlaybackSnapshot) {
                    service.updateWidgetSnapshotBlocking(snapshot)
                }
            }
        )
    }

    internal fun createShutdownCoordinator(): PlaybackShutdownCoordinator {
        return PlaybackShutdownCoordinator(
            callbacks = object : PlaybackShutdownCoordinator.Callbacks {
                override fun prepareForShutdown(options: ShutdownOptions) {
                    service.sessionFlags.keepIdleNotification =
                        options == ShutdownOptions.ClearQueueKeepingNotification
                    service.updateStopAfterCurrentTrackMode(false)
                    service.sessionFlags.notificationDismissedByUser = false
                }

                override fun finalizeShutdown(options: ShutdownOptions) {
                    if (options == ShutdownOptions.ClearQueueKeepingNotification) {
                        service.notificationControllerOrNull()?.run {
                            stopForegroundDetached()
                            update(force = true)
                        }
                    }
                }

                override fun shutdownControllerOrNull(): ShutdownController? {
                    return service.shutdownControllerOrNull()
                }
            }
        )
    }

    internal fun setupCoreControllers() {
        service.restoreManager = PlaybackRestoreManager(
            playbackQueueRepo = service.playbackQueueRepo,
            playbackStatePreferenceStore = service.playbackStatePreferenceStore,
            playbackRuntimeStateStore = service.playbackRuntimeStateStore,
            dispatchers = service.dispatchers
        )

        service.playbackModeResolver = PlaybackModeResolver(
            settingsPreferenceOps = service.settingPreferencesDataStore,
            applicationScope = service.serviceScope
        )

        service.playbackTuningController = PlaybackTuningController(
            player = service.player,
            playbackStatePreferenceStore = service.playbackStatePreferenceStore,
            settingPreferencesDataStore = service.settingPreferencesDataStore,
            soundEffectPreferences = service.soundEffectPreferences,
            stereoBalanceAudioProcessor = service.stereoBalanceAudioProcessor,
            extraStereoBalanceAudioProcessors = listOf(
                service.crossfadeStereoBalanceAudioProcessor
            ),
            currentMusicProvider = {
                service.queue.getOrNull(service.currentIndex)
            },
            applicationScope = service.serviceScope
        )

        service.playerQueueController = PlayerQueueController(
            player = service.player,
            queueProvider = {
                service.queue
            }
        )

        service.artworkLoader = ArtworkLoader(
            context = service,
            defaultArtwork = service.defaultArtwork
        )

        service.screenOffLockReceiver = ScreenOffLockReceiver(
            hasCurrentMusic = {
                service.currentIndex in service.queue.indices
            },
            isLockScreenEnabled = {
                service.runtimeCacheState.latestSettingPreferences.lockscreen.lockScreenEnabled
            }
        )

        service.audioFocusController = AudioFocusController(
            context = service,
            isPlaying = {
                service.player.isPlaying
            },
            pausePlayback = {
                service.pausePlayback(withFade = false)
            },
            resumePlayback = {
                service.resumePlayback()
            },
            isSimultaneousPlayEnabled = {
                service.runtimeCacheState.latestSettingPreferences.audio.simultaneousPlayEnabled
            }
        )

        service.statePublisher = PlaybackStatePublisher(
            player = service.player,
            runtimeStateStore = service.playbackRuntimeStateStore,
            queueProvider = {
                service.queue
            },
            currentIndexProvider = {
                service.currentIndex
            }
        )
    }

    internal fun setupSessionStateAndFavorites() {
        service.notificationSessionBridge = NotificationMediaSessionBridge(
            context = service,
            player = service.player,
            queueProvider = {
                service.queue
            },
            currentIndexProvider = {
                service.currentIndex
            },
            isEffectivelyPlaying = {
                service.isEffectivelyPlaying()
            },
            headsetMediaButtonHandler = service.headsetMediaButtonHandler,
            callbacks = object : NotificationMediaSessionBridge.Callbacks {
                override fun play() = service.resumePlayback()
                override fun pause() = service.pausePlayback()
                override fun next() = service.playNext()
                override fun previous() = service.playPrevious()
                override fun seekTo(positionMs: Int) = service.seekTo(positionMs)
                override fun toggleFavorite() = service.handleNotificationFavoriteToggle()
                override fun setFavorite(isFavorite: Boolean) =
                    service.setCurrentFavorite(isFavorite)
                override fun quit() = service.pauseAndPersistForNotificationClose()
                override fun stop() = service.stopPlaybackWithoutClearingQueue()
            }
        )

        service.notificationSessionBridge.updateQueue()
        service.notificationSessionBridge.updatePlaybackState()

        service.artworkController = CurrentArtworkController(
            defaultArtwork = service.defaultArtwork,
            artworkLoader = service.artworkLoader,
            observeAlbumPictureUseCase = service.observeAlbumPictureUseCase,
            queueManager = service.queueManager,
            runtimeStateStore = service.playbackRuntimeStateStore,
            notificationSessionBridge = service.notificationSessionBridge,
            callbacks = object : CurrentArtworkController.Callbacks {
                override fun onQueueArtworkChanged() {
                    service.publishPlaybackState(
                        reason = PublishReason.ArtworkChanged,
                        forceNotification = true,
                        forceWidgetUpdate = true
                    )
                }

                override fun onArtworkLoaded() {
                    service.updateNotification(force = true)
                }

                override fun onArtworkCleared() {
                    service.updateNotification(force = true)
                }
            }
        )

        service.media3Session = buildMedia3Session()
        updateMedia3CommandButtons()

        val notificationManager =
            service.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        service.notificationController = PlaybackNotificationController(
            service = service,
            notificationManager = notificationManager,
            mediaSessionTokenProvider = {
                service.notificationSessionBridge.session.sessionToken
            },
            currentMusicProvider = {
                service.queue.getOrNull(service.currentIndex)
            },
            currentArtworkProvider = {
                service.artworkControllerOrNull()?.currentArtwork
            },
            artworkTrackIdProvider = {
                service.artworkControllerOrNull()?.currentArtworkTrackId ?: NO_TRACK_ID
            },
            isEffectivelyPlaying = {
                service.isEffectivelyPlaying()
            },
            isFavoriteProvider = {
                service.queue.getOrNull(service.currentIndex)?.playlistId == MusicSet.FAVORITES
            },
            desktopLyricsEnabledProvider = {
                service.runtimeCacheState.latestDesktopLyricPreference.visible
            },
            notificationSettingsProvider = {
                service.runtimeCacheState.latestSettingPreferences.notification
            }
        )

        service.stateOrchestrator = PlaybackStateOrchestrator(
            notificationSessionBridge = service.notificationSessionBridge,
            notificationController = service.notificationController,
            statePublisher = service.statePublisher,
            isEffectivelyPlayingProvider = {
                service.isEffectivelyPlaying()
            },
            syncCurrentIndexWithPlayer = {
                service.syncCurrentIndexWithPlayer()
            },
            notificationDismissedByUserProvider = {
                service.sessionFlags.notificationDismissedByUser
            },
            setNotificationDismissedByUser = { dismissed ->
                service.sessionFlags.notificationDismissedByUser = dismissed
            }
        )

        service.stateUpdateCoordinator = createStateUpdateCoordinator()
        service.playbackEventDispatcher = createPlaybackEventDispatcher()

        service.favoriteController = CurrentFavoriteController(
            playlistRepo = service.playlistRepo,
            toggleFavoriteTrackUseCase = service.toggleFavoriteTrackUseCase,
            queueManager = service.queueManager,
            runtimeStateStore = service.playbackRuntimeStateStore,
            dispatchers = service.dispatchers,
            callbacks = object : CurrentFavoriteController.Callbacks {
                override fun onFavoriteChanged() {
                    updateMedia3CommandButtons()

                    service.publishPlaybackState(
                        reason = PublishReason.FavoriteChanged,
                        forceNotification = true,
                        forceWidgetUpdate = true
                    )
                }
            }
        )
    }

    private fun createPlaybackEventDispatcher(): PlaybackEventDispatcher {
        return PlaybackEventDispatcher(
            callbacks = object : PlaybackEventDispatcher.Callbacks {
                override fun publishAllRuntimeState(forceNotification: Boolean) {
                    service.publishAllRuntimeStateDirect(forceNotification)
                }

                override fun publishPlaybackState(
                    reason: PublishReason,
                    forceNotification: Boolean,
                    forceWidgetUpdate: Boolean
                ) {
                    service.publishPlaybackStateDirect(
                        reason = reason,
                        forceNotification = forceNotification,
                        forceWidgetUpdate = forceWidgetUpdate
                    )
                }

                override fun publishStateAfterShutdown(snapshot: PlaybackSnapshot?) {
                    service.publishStateAfterShutdownDirect(snapshot)
                }

                override fun onQueueMetadataChanged() {
                    service.notificationSessionBridge.updateQueue()
                    service.refreshArtworkAndSession(force = true)
                    service.persistFor(
                        PersistenceEvent.QueueMutation(
                            snapshot = service.capturePlaybackSnapshot()
                        )
                    )
                    service.publishPlaybackStateDirect(
                        reason = PublishReason.QueueChanged,
                        forceNotification = true,
                        forceWidgetUpdate = true
                    )
                }

                override fun updateNotification(force: Boolean) {
                    service.updateNotificationDirect(force = force)
                }
            }
        )
    }

    internal fun setupQueueAndRuntimeControllers() {
        service.queueActionController = QueueActionController(
            queueManager = service.queueManager,
            playerQueueController = service.playerQueueController,
            callbacks = object : QueueMutationCallbacks {
                override fun currentTrackDurationMs(): Int {
                    return service.queueManager.currentTrack?.duration ?: 0
                }

                override fun persistForSeek(positionMs: Int) {
                    service.persistFor(
                        PersistenceEvent.TrackProgress(
                            track = service.queueManager.currentTrack,
                            positionMs = positionMs.toLong().coerceAtLeast(0L),
                            currentIndex = service.queueManager.currentIndex
                        )
                    )
                }

                override fun resumePlaybackInternal() {
                    service.resumePlaybackInternal()
                }

                override fun resolveNextIndex(
                    queueSize: Int,
                    currentIndex: Int,
                    fromAutoTransition: Boolean
                ): Int? {
                    return service.playbackModeResolver.resolveNextIndex(
                        queueSize = queueSize,
                        currentIndex = currentIndex,
                        fromAutoTransition = fromAutoTransition
                    )
                }

                override fun resolvePreviousIndex(
                    queueSize: Int,
                    currentIndex: Int,
                    shouldRestartCurrent: Boolean
                ): Int? {
                    return service.playbackModeResolver.resolvePreviousIndex(
                        queueSize = queueSize,
                        currentIndex = currentIndex,
                        shouldRestartCurrent = shouldRestartCurrent
                    )
                }

                override fun onAutoTransitionReachedQueueEnd() {
                    service.stopAtQueueStart()
                }

                override fun currentPlayerPositionIsAfterPreviousRestartWindow(): Boolean {
                    return service.player.currentPosition > PREVIOUS_RESTART_WINDOW_MS
                }

                override fun seekCurrentToStart() {
                    service.seekToInternal(0)
                }

                override fun resetPlaybackStatistics() {
                    service.playbackStatsTracker.reset()
                }

                override fun resetTimedTransition() {
                    service.resetTimedTransitionState()
                }

                override fun applyVolumeForPlaybackStart(playWhenReady: Boolean) {
                    service.applyVolumeForPlaybackStart(playWhenReady)
                }

                override fun playerPlaybackStateIsNotIdle(): Boolean {
                    return service.player.playbackState != Player.STATE_IDLE
                }

                override fun onReplaceWithEmptyQueue() {
                    service.stopPlayback()
                }

                override fun onQueueBecameEmpty() {
                    service.clearQueueKeepingNotification()
                }

                override fun markPlaybackRestored() {
                    service.restoreManager.markRestored()
                }

                override fun requestAudioFocus(): Boolean {
                    return service.audioFocusController.request()
                }

                override fun isEffectivelyPlaying(): Boolean {
                    return service.isEffectivelyPlaying()
                }

                override fun currentPlayerPositionMs(): Long {
                    return service.player.currentPosition.coerceAtLeast(0L)
                }

                override fun updateNotificationSessionQueue() {
                    service.notificationSessionBridge.updateQueue()
                }

                override fun refreshArtworkAndSession(force: Boolean) {
                    service.refreshArtworkAndSession(force)
                }

                override fun publishQueueChanged(forceNotification: Boolean) {
                    service.publishPlaybackState(
                        reason = PublishReason.QueueChanged,
                        forceNotification = forceNotification
                    )
                }

                override fun publishPlayerEvent(forceNotification: Boolean) {
                    service.publishAllRuntimeState(
                        forceNotification = forceNotification
                    )
                }
            },
            remapQueueIndex = service.remapQueueIndexUseCase::invoke
        )

        service.commandHandler = createCommandHandler()
        service.playbackServiceRuntime = createPlaybackServiceRuntime()

        service.playbackTuningController.applyPlaybackTuning()

        service.snapshotManager = PlaybackSnapshotManager(
            playbackQueueRepo = service.playbackQueueRepo,
            playbackStatePreferenceStore = service.playbackStatePreferenceStore,
            runtimeStateStore = service.playbackRuntimeStateStore,
            dispatchers = service.dispatchers
        )

        service.progressTicker = PlaybackProgressTicker(
            tickIntervalMs = PROGRESS_TICK_MS,
            callbacks = object : PlaybackProgressTicker.Callbacks {
                override fun onProgressTick() {
                    service.handleProgressTick()
                }
            }
        )

        service.volumeFader = VolumeFader(
            player = service.player,
            scope = service.serviceScope,
            targetVolumeProvider = service.playbackTuningController::resolveTargetPlaybackVolume
        )

        service.crossfadeVolumeFader = VolumeFader(
            player = service.crossfadePlayer,
            scope = service.serviceScope,
            targetVolumeProvider = {
                service.playbackTuningController.resolveTargetPlaybackVolume(
                    service.timedTransitionController.currentIncomingTrack()
                )
            }
        )

        service.timedTransitionController = TimedTransitionController(
            player = service.player,
            incomingPlayer = service.crossfadePlayer,
            playbackModeResolver = service.playbackModeResolver,
            volumeFader = service.volumeFader,
            incomingVolumeFader = service.crossfadeVolumeFader,
            queueProvider = {
                service.queue
            },
            currentIndexProvider = {
                service.currentIndex
            },
            preferencesProvider = {
                service.runtimeCacheState.latestSettingPreferences
            },
            callbacks = object : TimedTransitionController.Callbacks {
                override fun onPlayNext(fromAutoTransition: Boolean) {
                    service.playNextInternal(fromAutoTransition = fromAutoTransition)
                }

                override fun onCrossfadeCommit(
                    nextIndex: Int,
                    positionMs: Long
                ) {
                    service.commitCrossfadeTransition(
                        nextIndex = nextIndex,
                        positionMs = positionMs
                    )
                }
            }
        )
    }

    internal fun buildMedia3Session(): MediaSession {
        val sessionActivity = PendingIntent.getActivity(
            service,
            MEDIA3_SESSION_ACTIVITY_REQUEST_CODE,
            Intent(service, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(
                    MainActivity.EXTRA_EXPAND_PLAYER,
                    true
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return MediaSession.Builder(
            service,
            service.player
        )
            .setSessionActivity(sessionActivity)
            .setCallback(service.media3SessionCallback)
            .build()
    }

    @OptIn(UnstableApi::class)
    internal fun updateMedia3CommandButtons() {
        val session = service.media3Session ?: return
        val buttons = buildMedia3CommandButtons()

        session.setCustomLayout(buttons)
        session.setMediaButtonPreferences(buttons)
    }

    internal fun registerScreenOffReceiver() {
        if (service.sessionFlags.screenReceiverRegistered) return

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            service.registerReceiver(
                service.screenOffLockReceiver,
                filter,
                RECEIVER_NOT_EXPORTED
            )
        } else {
            service.registerReceiver(
                service.screenOffLockReceiver,
                filter
            )
        }

        service.sessionFlags.screenReceiverRegistered = true
    }

    internal fun unregisterScreenOffReceiver() {
        if (!service.sessionFlags.screenReceiverRegistered) return

        runCatching {
            service.unregisterReceiver(service.screenOffLockReceiver)
        }

        service.sessionFlags.screenReceiverRegistered = false
    }

    internal fun isNightMode(configuration: Configuration): Boolean {
        return (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun buildMedia3CommandButtons(): List<CommandButton> {
        val favorite = service.queue
            .getOrNull(service.currentIndex)
            ?.playlistId == MusicSet.FAVORITES

        return listOf(
            CommandButton.Builder(
                if (favorite) {
                    CommandButton.ICON_HEART_FILLED
                } else {
                    CommandButton.ICON_HEART_UNFILLED
                }
            )
                .setSessionCommand(
                    SessionCommand(
                        Media3Commands.TOGGLE_FAVORITE,
                        Bundle.EMPTY
                    )
                )
                .setDisplayName(
                    service.getString(
                        if (favorite) {
                            R.string.music_unfavorite
                        } else {
                            R.string.favorite
                        }
                    )
                )
                .build(),
            CommandButton.Builder(CommandButton.ICON_STOP)
                .setSessionCommand(
                    SessionCommand(
                        Media3Commands.CLOSE_NOTIFICATION,
                        Bundle.EMPTY
                    )
                )
                .setDisplayName(service.getString(R.string.operation_stop))
                .build()
        )
    }

    private fun initializeMedia3SessionDelegates() {
        service.media3CommandHandler = Media3CommandHandler(
            host = object : Media3CommandHandler.Host {
                override fun toggleCurrentFavorite() {
                    service.toggleCurrentFavorite()
                }

                override fun cyclePlaybackMode() {
                    service.playbackModeResolverOrNull()?.cyclePlaybackMode()
                }

                override fun pauseAndPersistForNotificationClose() {
                    service.pauseAndPersistForNotificationClose()
                }

                override fun stopAfterCurrentTrackEnabled(): Boolean {
                    return service.sessionFlags.stopAfterCurrentTrack
                }

                override fun updateStopAfterCurrentTrackMode(enabled: Boolean) {
                    service.updateStopAfterCurrentTrackMode(enabled)
                }

                override fun publishAllRuntimeState(forceNotification: Boolean) {
                    service.publishAllRuntimeState(forceNotification)
                }
            }
        )

        service.playbackResumptionHandler = PlaybackResumptionHandler(
            host = object : PlaybackResumptionHandler.Host {
                override suspend fun ensurePlaybackRestored() {
                    service.ensurePlaybackRestored()
                }

                override fun requestAudioFocus(): Boolean {
                    return service.audioFocusControllerOrNull()?.request() == true
                }

                override fun queue() = service.queue

                override fun currentIndex() = service.currentIndex

                override fun resolveCurrentSnapshotPositionMs(): Long {
                    return service.resolveCurrentSnapshotPositionMs()
                }
            }
        )

        service.media3TransportCommandTracker = Media3TransportCommandTracker(
            state = service.media3TransportState,
            captureSnapshot = service::capturePlaybackSnapshot
        )

        service.media3SessionCallbackDelegate = MusicMediaSessionCallback(
            scope = service.serviceScope,
            commandHandler = service.media3CommandHandler,
            resumptionHandler = service.playbackResumptionHandler,
            transportTracker = service.media3TransportCommandTracker,
            currentIndex = { service.currentIndex },
            currentPositionMs = { service.playerOrNull()?.currentPosition ?: 0L },
            onMediaButtonKeyDown = service.headsetMediaButtonHandler::handle,
            onPlayerInteractionFinishedAction = service::handleMedia3PlayerInteractionFinished
        )
    }
}
