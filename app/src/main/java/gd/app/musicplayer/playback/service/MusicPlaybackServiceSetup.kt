package gd.app.musicplayer.playback.service

import android.app.NotificationManager
import android.app.PendingIntent
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
import gd.app.musicplayer.playback.desktop.DesktopLyricsOverlayController
import gd.app.musicplayer.playback.effects.PlaybackTuningController
import gd.app.musicplayer.playback.effects.VolumeFader
import gd.app.musicplayer.playback.favorite.CurrentFavoriteController
import gd.app.musicplayer.playback.headset.ScreenOffLockReceiver
import gd.app.musicplayer.playback.notification.NotificationCloseCallbacks
import gd.app.musicplayer.playback.notification.NotificationCloseController
import gd.app.musicplayer.playback.notification.NotificationMediaSessionBridge
import gd.app.musicplayer.playback.notification.PlaybackNotificationController
import gd.app.musicplayer.playback.progress.PlaybackProgressTicker
import gd.app.musicplayer.playback.queue.PlayerQueueController
import gd.app.musicplayer.playback.queue.QueueActionController
import gd.app.musicplayer.playback.queue.QueueMutationCallbacks
import gd.app.musicplayer.playback.restore.PlaybackRestoreManager
import gd.app.musicplayer.playback.shutdown.PlaybackShutdownCoordinator
import gd.app.musicplayer.playback.shutdown.ShutdownCallbacks
import gd.app.musicplayer.playback.shutdown.ShutdownController
import gd.app.musicplayer.playback.state.PlaybackSnapshot
import gd.app.musicplayer.playback.state.PlaybackSnapshotManager
import gd.app.musicplayer.playback.state.PlaybackStateOrchestrator
import gd.app.musicplayer.playback.state.PlaybackStatePublisher
import gd.app.musicplayer.playback.state.PlaybackStateUpdateCoordinator
import gd.app.musicplayer.playback.state.PublishReason
import gd.app.musicplayer.playback.statusbar.StatusBarLyricsOverlayController
import gd.app.musicplayer.playback.transition.TimedTransitionController
import gd.app.musicplayer.ui.shell.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


internal fun MusicPlaybackService.prepareServiceBaseState() {
    MusicPlaybackService.isRunning = true
    isNightMode = isNightMode(resources.configuration)

    serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )

    defaultArtwork = BitmapFactory.decodeResource(
        resources,
        R.drawable.notify_default_album
    )

    // Obfuscated parity (y.n0 + y.S path intent): warm default track set ahead of play action
    // so empty-queue "play" can resolve queue immediately without waiting for first DB/Flow roundtrip.
    defaultTracksObserverJob = serviceScope.launch(dispatchers.io) {
        observeTracksUseCase(MusicSet.Tracks).collectLatest { tracks ->
            cachedDefaultTracks = tracks
            cachedPlayableDefaultTracks = tracks.filter { music ->
                music.toMediaItemOrNull() != null
            }
        }
    }
}

internal fun MusicPlaybackService.createLifecycleController(): PlaybackLifecycleController {
    return PlaybackLifecycleController(
        callbacks = PlaybackLifecycleCallbacks(this)
    )
}

internal fun MusicPlaybackService.createCommandHandler(): PlaybackCommandHandler {
    return PlaybackCommandHandler(
        payloadStore = commandPayloadStore,
        callbacks = PlaybackCommandCallbacks(
            service = this,
            serviceScope = serviceScope
        )
    )
}

internal fun MusicPlaybackService.createPlaybackServiceRuntime(): PlaybackServiceRuntime {
    return PlaybackServiceRuntime(
        commandHandler = commandHandler,
        callbacks = PlaybackServiceRuntimeCallbacks(this)
    )
}

internal fun MusicPlaybackService.createStateUpdateCoordinator(): PlaybackStateUpdateCoordinator {
    return PlaybackStateUpdateCoordinator(
        callbacks = object : PlaybackStateUpdateCoordinator.Callbacks {
            override fun stateOrchestratorOrNull(): PlaybackStateOrchestrator? {
                return if (isStateOrchestratorInitialized()) stateOrchestrator else null
            }

            override fun runtimeWidgetSnapshot(): WidgetPlaybackSnapshot {
                return playbackRuntimeStateStore.state.value.toWidgetPlaybackSnapshot()
            }

            override fun shutdownWidgetSnapshot(snapshot: PlaybackSnapshot?): WidgetPlaybackSnapshot {
                return snapshot?.toWidgetPlaybackSnapshot()
                    ?: playbackRuntimeStateStore.state.value.toWidgetPlaybackSnapshot()
            }

            override fun updateWidgets(snapshot: WidgetPlaybackSnapshot) {
                updateWidgetSnapshot(snapshot)
            }

            override fun updateWidgetsBlocking(snapshot: WidgetPlaybackSnapshot) {
                updateWidgetSnapshotBlocking(snapshot)
            }
        }
    )
}

internal fun MusicPlaybackService.createShutdownCoordinator(): PlaybackShutdownCoordinator {
    return PlaybackShutdownCoordinator(
        callbacks = object : PlaybackShutdownCoordinator.Callbacks {
            override fun clearIdleNotificationPolicy() {
                keepIdleNotification = false
            }

            override fun disableStopAfterCurrentTrack() {
                updateStopAfterCurrentTrackMode(false)
            }

            override fun markNotificationAsVisibleAgain() {
                notificationDismissedByUser = false
            }

            override fun shutdownControllerOrNull(): ShutdownController? {
                return if (isShutdownControllerInitialized()) shutdownController else null
            }
        }
    )
}

internal fun MusicPlaybackService.configureControllers() {

    restoreManager = PlaybackRestoreManager(
        playbackQueueRepo = playbackQueueRepo,
        playbackStatePreferenceStore = playbackStatePreferenceStore,
        playbackRuntimeStateStore = playbackRuntimeStateStore,
        dispatchers = dispatchers
    )

    playbackModeResolver = PlaybackModeResolver(
        settingsPreferenceOps = settingPreferencesDataStore,
        applicationScope = serviceScope
    )

    desktopLyricsController = DesktopLyricsOverlayController(
        context = applicationContext,
        scope = serviceScope,
        trackLyricPreferenceStore = trackLyricPreferenceStore,
        callbacks = object : DesktopLyricsOverlayController.Callbacks {
            override fun previous() {
                playPrevious()
            }

            override fun next() {
                playNext()
            }

            override fun togglePlayPause() {
                this@configureControllers.togglePlayPause()
            }

            override fun cyclePlaybackMode() {
                playbackModeResolver.cyclePlaybackMode()
                desktopLyricsController.renderPlaybackState(playbackRuntimeStateStore.state.value)
            }

            override fun toggleFavorite() {
                toggleCurrentFavorite()
            }

            override fun closeDesktopLyrics() {
                serviceScope.launch {
                    desktopLyricPreferenceStore.setVisible(false)
                }
            }

            override fun lockDesktopLyrics() {
                serviceScope.launch {
                    desktopLyricPreferenceStore.setLocked(true)
                }
            }

            override fun updatePreference(
                presetColorIndex: Int?,
                currentColorProgress: Int?,
                normalColorProgress: Int?,
                alpha: Float?,
                textSize: Int?,
                y: Int?
            ) {
                serviceScope.launch {
                    desktopLyricPreferenceStore.updatePreference(
                        presetColorIndex = presetColorIndex,
                        currentColorProgress = currentColorProgress,
                        normalColorProgress = normalColorProgress,
                        alpha = alpha,
                        textSize = textSize,
                        y = y
                    )
                }
            }

            override fun currentPlaybackMode(): Int {
                return playbackModeResolver.getPlaybackMode()
            }
        }
    )

    statusBarLyricsController = StatusBarLyricsOverlayController(
        context = applicationContext,
        scope = serviceScope,
        trackLyricPreferenceStore = trackLyricPreferenceStore,
        callbacks = object : StatusBarLyricsOverlayController.Callbacks {
            override fun togglePlayPause() {
                this@configureControllers.togglePlayPause()
            }
        }
    )

    playbackTuningController = PlaybackTuningController(
        player = player,
        playbackStatePreferenceStore = playbackStatePreferenceStore,
        settingPreferencesDataStore = settingPreferencesDataStore,
        soundEffectPreferences = soundEffectPreferences,
        stereoBalanceAudioProcessor = stereoBalanceAudioProcessor,
        extraStereoBalanceAudioProcessors = listOf(crossfadeStereoBalanceAudioProcessor),
        currentMusicProvider = {
            queue.getOrNull(currentIndex)
        },
        applicationScope = serviceScope
    )

    playerQueueController = PlayerQueueController(
        player = player,
        queueProvider = {
            queue
        }
    )

    artworkLoader = ArtworkLoader(
        context = this,
        defaultArtwork = defaultArtwork
    )

    screenOffLockReceiver = ScreenOffLockReceiver(
        hasCurrentMusic = {
            currentIndex in queue.indices
        },
        isLockScreenEnabled = {
            latestSettingPreferences.lockscreen.lockScreenEnabled
        }
    )

    audioFocusController = AudioFocusController(
        context = this,
        isPlaying = {
            player.isPlaying
        },
        pausePlayback = {
            pausePlayback(withFade = false)
        },
        resumePlayback = {
            resumePlayback()
        },
        isSimultaneousPlayEnabled = {
            latestSettingPreferences.audio.simultaneousPlayEnabled
        }
    )

    statePublisher = PlaybackStatePublisher(
        context = applicationContext,
        player = player,
        runtimeStateStore = playbackRuntimeStateStore,
        queueProvider = {
            queue
        },
        currentIndexProvider = {
            currentIndex
        }
    )

    notificationSessionBridge = NotificationMediaSessionBridge(
        context = this,
        player = player,
        queueProvider = {
            queue
        },
        currentIndexProvider = {
            currentIndex
        },
        isEffectivelyPlaying = {
            isEffectivelyPlaying()
        },
        headsetMediaButtonHandler = headsetMediaButtonHandler,
        callbacks = object : NotificationMediaSessionBridge.Callbacks {
            override fun play() = resumePlayback()
            override fun pause() = pausePlayback()
            override fun next() = playNext()
            override fun previous() = playPrevious()
            override fun seekTo(positionMs: Int) = this@configureControllers.seekTo(positionMs)
            override fun toggleFavorite() = handleNotificationFavoriteToggle()
            override fun setFavorite(isFavorite: Boolean) = setCurrentFavorite(isFavorite)
            override fun quit() = pauseAndPersistForNotificationClose()
            override fun stop() = stopPlaybackWithoutClearingQueue()
        }
    )

    notificationSessionBridge.updateQueue()
    notificationSessionBridge.updatePlaybackState()

    artworkController = CurrentArtworkController(
        defaultArtwork = defaultArtwork,
        artworkLoader = artworkLoader,
        observeAlbumPictureUseCase = observeAlbumPictureUseCase,
        queueManager = queueManager,
        runtimeStateStore = playbackRuntimeStateStore,
        notificationSessionBridge = notificationSessionBridge,
        callbacks = object : CurrentArtworkController.Callbacks {

            override fun onQueueArtworkChanged() {
                publishPlaybackState(
                    reason = PublishReason.ArtworkChanged,
                    forceNotification = true,
                    forceWidgetUpdate = true
                )
            }

            override fun onArtworkLoaded() {
                updateNotification(force = true)
            }

            override fun onArtworkCleared() {
                updateNotification(force = true)
            }
        }
    )

    media3Session = buildMedia3Session()
    updateMedia3CommandButtons()

    val notificationManager =
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    notificationController = PlaybackNotificationController(
        service = this,
        notificationManager = notificationManager,
        mediaSessionTokenProvider = {
            notificationSessionBridge.session.sessionToken
        },
        currentMusicProvider = {
            queue.getOrNull(currentIndex)
        },
        currentArtworkProvider = {
            if (isArtworkControllerInitialized()) {
                artworkController.currentArtwork
            } else {
                null
            }
        },
        artworkTrackIdProvider = {
            if (isArtworkControllerInitialized()) {
                artworkController.currentArtworkTrackId
            } else {
                NO_TRACK_ID
            }
        },
        isEffectivelyPlaying = {
            isEffectivelyPlaying()
        },
        isFavoriteProvider = {
            queue.getOrNull(currentIndex)?.playlistId == MusicSet.FAVORITES
        },
        desktopLyricsEnabledProvider = {
            latestDesktopLyricPreference.visible
        },
        notificationSettingsProvider = {
            latestSettingPreferences.notification
        }
    )

    stateOrchestrator = PlaybackStateOrchestrator(
        notificationSessionBridge = notificationSessionBridge,
        notificationController = notificationController,
        statePublisher = statePublisher,
        isEffectivelyPlayingProvider = {
            isEffectivelyPlaying()
        },
        syncCurrentIndexWithPlayer = {
            syncCurrentIndexWithPlayer()
        },
        notificationDismissedByUserProvider = {
            notificationDismissedByUser
        },
        setNotificationDismissedByUser = { dismissed ->
            notificationDismissedByUser = dismissed
        }
    )

    stateUpdateCoordinator = createStateUpdateCoordinator()

    favoriteController = CurrentFavoriteController(
        playlistRepo = playlistRepo,
        toggleFavoriteTrackUseCase = toggleFavoriteTrackUseCase,
        queueManager = queueManager,
        runtimeStateStore = playbackRuntimeStateStore,
        dispatchers = dispatchers,
        callbacks = object : CurrentFavoriteController.Callbacks {
            override fun onFavoriteChanged() {
                updateMedia3CommandButtons()

                stateOrchestrator.publishPlaybackState(
                    reason = PublishReason.FavoriteChanged,
                    forceNotification = true,
                    forceWidgetUpdate = true
                )
            }
        }
    )

    queueActionController = QueueActionController(
        queueManager = queueManager,
        playerQueueController = playerQueueController,
        callbacks = object : QueueMutationCallbacks {

            override fun currentTrackDurationMs(): Int {
                return queueManager.currentTrack?.duration ?: 0
            }

            override fun persistSessionFromCurrentStateAsync() {
                this@configureControllers.persistSessionFromCurrentStateAsync()
            }

            override fun persistCurrentTrackProgress(positionMs: Int) {
                this@configureControllers.persistCurrentTrackProgressAsync(positionMs)
            }

            override fun resumePlaybackInternal() {
                this@configureControllers.resumePlaybackInternal()
            }

            override fun resolveNextIndex(
                queueSize: Int,
                currentIndex: Int,
                fromAutoTransition: Boolean
            ): Int? {
                return playbackModeResolver.resolveNextIndex(
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
                return playbackModeResolver.resolvePreviousIndex(
                    queueSize = queueSize,
                    currentIndex = currentIndex,
                    shouldRestartCurrent = shouldRestartCurrent
                )
            }

            override fun onAutoTransitionReachedQueueEnd() {
                stopAtQueueStart()
            }

            override fun currentPlayerPositionIsAfterPreviousRestartWindow(): Boolean {
                return player.currentPosition > PREVIOUS_RESTART_WINDOW_MS
            }

            override fun seekCurrentToStart() {
                seekToInternal(0)
            }

            override fun resetPlaybackStatistics() {
                playbackStatsTracker.reset()
            }

            override fun resetTimedTransition() {
                resetTimedTransitionState()
            }

            override fun applyVolumeForPlaybackStart(playWhenReady: Boolean) {
                this@configureControllers.applyVolumeForPlaybackStart(playWhenReady)
            }

            override fun playerPlaybackStateIsNotIdle(): Boolean {
                return player.playbackState != Player.STATE_IDLE
            }

            override fun onReplaceWithEmptyQueue() {
                stopPlayback()
            }

            override fun onQueueBecameEmpty() {
                clearQueueKeepingNotification()
            }

            override fun markPlaybackRestored() {
                restoreManager.markRestored()
            }

            override fun requestAudioFocus(): Boolean {
                return audioFocusController.request()
            }

            override fun isEffectivelyPlaying(): Boolean {
                return this@configureControllers.isEffectivelyPlaying()
            }

            override fun currentPlayerPositionMs(): Long {
                return player.currentPosition.coerceAtLeast(0L)
            }

            override fun updateNotificationSessionQueue() {
                notificationSessionBridge.updateQueue()
            }

            override fun refreshArtworkAndSession(force: Boolean) {
                this@configureControllers.refreshArtworkAndSession(force)
            }

            override fun publishQueueChanged(forceNotification: Boolean) {
                publishPlaybackState(
                    reason = PublishReason.QueueChanged,
                    forceNotification = forceNotification
                )
            }

            override fun publishPlayerEvent(forceNotification: Boolean) {
                publishAllRuntimeState(
                    forceNotification = forceNotification
                )
            }
        },
        remapQueueIndex = remapQueueIndexUseCase::invoke
    )

    commandHandler = createCommandHandler()
    playbackServiceRuntime = createPlaybackServiceRuntime()

    playbackTuningController.applyPlaybackTuning()

    snapshotManager = PlaybackSnapshotManager(
        playbackQueueRepo = playbackQueueRepo,
        playbackStatePreferenceStore = playbackStatePreferenceStore,
        runtimeStateStore = playbackRuntimeStateStore,
        dispatchers = dispatchers
    )

    progressTicker = PlaybackProgressTicker(
        tickIntervalMs = PROGRESS_TICK_MS,
        callbacks = object : PlaybackProgressTicker.Callbacks {
            override fun onProgressTick() {
                handleProgressTick()
            }
        }
    )
    volumeFader = VolumeFader(
        player = player,
        scope = serviceScope,
        targetVolumeProvider = playbackTuningController::resolveTargetPlaybackVolume
    )
    crossfadeVolumeFader = VolumeFader(
        player = crossfadePlayer,
        scope = serviceScope,
        targetVolumeProvider = {
            playbackTuningController.resolveTargetPlaybackVolume(
                timedTransitionController.currentIncomingTrack()
            )
        }
    )

    timedTransitionController = TimedTransitionController(
        player = player,
        incomingPlayer = crossfadePlayer,
        playbackModeResolver = playbackModeResolver,
        volumeFader = volumeFader,
        incomingVolumeFader = crossfadeVolumeFader,
        queueProvider = {
            queue
        },
        currentIndexProvider = {
            currentIndex
        },
        preferencesProvider = {
            latestSettingPreferences
        },
        callbacks = object : TimedTransitionController.Callbacks {
            override fun onPlayNext(fromAutoTransition: Boolean) {
                playNextInternal(fromAutoTransition = fromAutoTransition)
            }

            override fun onCrossfadeCommit(
                nextIndex: Int,
                positionMs: Long
            ) {
                commitCrossfadeTransition(
                    nextIndex = nextIndex,
                    positionMs = positionMs
                )
            }
        }
    )

    shutdownController = ShutdownController(
        callbacks = object : ShutdownCallbacks {

            override fun capturePlaybackSnapshot(): PlaybackSnapshot {
                return this@configureControllers.capturePlaybackSnapshot()
            }

            override fun persistPlaybackSnapshotBlocking(
                snapshot: PlaybackSnapshot,
                persistQueue: Boolean
            ) {
                this@configureControllers.persistPlaybackSnapshotBlocking(
                    snapshot = snapshot,
                    persistQueue = persistQueue
                )
            }

            override fun resetPlaybackStatistics() {
                playbackStatsTracker.reset()
            }

            override fun resetTimedTransition() {
                resetTimedTransitionState()
            }

            override fun cancelVolumeFade() {
                if (isVolumeFaderInitialized()) {
                    volumeFader.cancel()
                }
            }

            override fun stopAndClearPlayer() {
                if (isPlayerInitialized()) {
                    player.pause()
                    player.stop()
                    player.clearMediaItems()
                }
            }

            override fun abandonAudioFocus() {
                if (isAudioFocusControllerInitialized()) {
                    audioFocusController.abandon()
                }
            }

            override fun clearArtworkState() {
                this@configureControllers.clearArtworkState()
            }

            override fun clearQueueState() {
                this@configureControllers.clearQueueState()
            }

            override fun clearPersistedPlaybackState() {
                this@configureControllers.clearPersistedPlaybackState()
            }

            override fun clearPersistedQueue() {
                clearPersistedQueueBlocking()
            }

            override fun clearNotificationSession() {
                if (isNotificationSessionBridgeInitialized()) {
                    notificationSessionBridge.clearMetadata()
                    notificationSessionBridge.clearQueue()
                }
            }

            override fun markRestoreEmpty() {
                restoreManager.markEmpty()
            }

            override fun resetRuntimeState() {
                stateOrchestrator.resetRuntimeState()
            }

            override fun publishStateAfterShutdown(snapshot: PlaybackSnapshot) {
                this@configureControllers.publishStateAfterShutdown(snapshot)
            }

            override fun removeNotification() {
                if (isNotificationControllerInitialized()) {
                    notificationController.stopForegroundAndRemove()
                }
            }

            override fun onServiceShouldStop() {
                MusicPlaybackService.isRunning = false
                stopSelf()
            }
        }
    )

    shutdownCoordinator = createShutdownCoordinator()

    notificationCloseController = NotificationCloseController(
        callbacks = object : NotificationCloseCallbacks {

            override fun capturePlaybackSnapshot(): PlaybackSnapshot {
                return this@configureControllers.capturePlaybackSnapshot()
            }

            override fun setNotificationDismissedByUser(dismissed: Boolean) {
                notificationDismissedByUser = dismissed
            }

            override fun syncQueueFromSnapshot(snapshot: PlaybackSnapshot) {
                setQueueState(
                    newQueue = snapshot.queue,
                    requestedIndex = snapshot.currentIndex
                )
            }

            override fun pausePlayerIfNeeded() {
                if (player.isPlaying || player.playWhenReady) {
                    player.pause()
                }
            }

            override fun persistPlaybackSnapshotBlocking(
                snapshot: PlaybackSnapshot,
                persistQueue: Boolean
            ) {
                this@configureControllers.persistPlaybackSnapshotBlocking(
                    snapshot = snapshot,
                    persistQueue = persistQueue
                )
            }

            override fun updateNotificationSessionPlaybackState() {
                notificationSessionBridge.updatePlaybackState()
            }

            override fun publishPausedSnapshot(snapshot: PlaybackSnapshot) {
                stateOrchestrator.publishSnapshot(
                    snapshot = snapshot,
                    isPlaying = false
                )
            }

            override fun removeNotification() {
                notificationController.stopForegroundAndRemove()
            }
        }
    )

}

internal fun MusicPlaybackService.buildMedia3Session(): MediaSession {
    val sessionActivity = PendingIntent.getActivity(
        this,
        MEDIA3_SESSION_ACTIVITY_REQUEST_CODE,
        Intent(this, MainActivity::class.java).apply {
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
        this,
        player
    )
        .setSessionActivity(sessionActivity)
        .setCallback(media3SessionCallback)
        .build()
}

internal fun Int.isMedia3TransportNavigationCommand(): Boolean {
    return this == Player.COMMAND_SEEK_TO_NEXT ||
            this == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
            this == Player.COMMAND_SEEK_TO_PREVIOUS ||
            this == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
}

@OptIn(UnstableApi::class)
internal fun MusicPlaybackService.updateMedia3CommandButtons() {
    val session = media3Session ?: return
    val buttons = buildMedia3CommandButtons()

    session.setCustomLayout(buttons)
    session.setMediaButtonPreferences(buttons)
}

internal fun MusicPlaybackService.buildMedia3CommandButtons(): List<CommandButton> {
    val favorite = queue.getOrNull(currentIndex)?.playlistId == MusicSet.FAVORITES

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
                    MEDIA3_COMMAND_TOGGLE_FAVORITE,
                    Bundle.EMPTY
                )
            )
            .setDisplayName(
                getString(
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
                    MEDIA3_COMMAND_CLOSE_NOTIFICATION,
                    Bundle.EMPTY
                )
            )
            .setDisplayName(getString(R.string.operation_stop))
            .build()
    )
}

internal fun MusicPlaybackService.registerScreenOffReceiver() {
    if (screenReceiverRegistered) return

    val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(
            screenOffLockReceiver,
            filter,
            RECEIVER_NOT_EXPORTED
        )
    } else {
        registerReceiver(
            screenOffLockReceiver,
            filter
        )
    }

    screenReceiverRegistered = true
}

internal fun MusicPlaybackService.unregisterScreenOffReceiver() {
    if (!screenReceiverRegistered) return

    runCatching {
        unregisterReceiver(screenOffLockReceiver)
    }

    screenReceiverRegistered = false
}

internal fun MusicPlaybackService.isNightMode(configuration: Configuration): Boolean {
    return (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
