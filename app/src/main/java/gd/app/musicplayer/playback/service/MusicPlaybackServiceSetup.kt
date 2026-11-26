package gd.app.musicplayer.playback.service

import android.app.NotificationManager
import android.content.Context.NOTIFICATION_SERVICE
import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.media3.common.Player
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.playback.artwork.ArtworkLoader
import gd.app.musicplayer.playback.AudioFocusController
import gd.app.musicplayer.playback.player.PlaybackSessionMetadata
import gd.app.musicplayer.playback.PlaybackModeResolver
import gd.app.musicplayer.playback.notification.PlaybackNotificationController
import gd.app.musicplayer.playback.command.PlaybackCommandHandler
import gd.app.musicplayer.playback.state.PlaybackStatePublisher
import gd.app.musicplayer.playback.widget.PlaybackWidgetBridge
import gd.app.musicplayer.playback.effects.PlaybackTuningController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import gd.app.musicplayer.playback.artwork.CurrentArtworkController
import gd.app.musicplayer.playback.favorite.CurrentFavoriteController
import gd.app.musicplayer.playback.notification.NotificationCloseCallbacks
import gd.app.musicplayer.playback.notification.NotificationCloseController
import gd.app.musicplayer.playback.player.QueueAwareSessionPlayer
import gd.app.musicplayer.playback.progress.PlaybackProgressTicker
import gd.app.musicplayer.playback.queue.PlayerQueueController
import gd.app.musicplayer.playback.queue.QueueActionController
import gd.app.musicplayer.playback.queue.QueueMutationCallbacks
import gd.app.musicplayer.playback.state.PlaybackSnapshot
import gd.app.musicplayer.playback.state.PlaybackSnapshotManager
import gd.app.musicplayer.playback.restore.PlaybackRestoreManager
import gd.app.musicplayer.playback.shutdown.ShutdownCallbacks
import gd.app.musicplayer.playback.shutdown.ShutdownController
import gd.app.musicplayer.playback.shutdown.PlaybackShutdownCoordinator
import gd.app.musicplayer.playback.state.PlaybackStateOrchestrator
import gd.app.musicplayer.playback.state.PlaybackStateUpdateCoordinator
import gd.app.musicplayer.playback.state.PublishReason
import gd.app.musicplayer.playback.transition.TimedTransitionController
import gd.app.musicplayer.playback.desktop.DesktopLyricsOverlayController
import gd.app.musicplayer.playback.statusbar.StatusBarLyricsOverlayController
import gd.app.musicplayer.feature.widget.provider.WidgetPlaybackSnapshot

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

            override fun shutdownWidgetSnapshot(snapshot: PlaybackSnapshot?): WidgetPlaybackSnapshot {
                return if (snapshot != null) {
                    widgetBridge.map(snapshot)
                } else {
                    widgetBridge.map(playbackRuntimeStateStore.state.value)
                }
            }

            override fun updateWidgetsBlocking(snapshot: WidgetPlaybackSnapshot) {
                widgetBridge.updateBlocking(snapshot)
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
        resumeLoader = resumeLoader,
        playbackRuntimeStateStore = playbackRuntimeStateStore
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
        applicationScope = serviceScope,
        applyPlayerVolume = { resolvedVolume ->
            val fader = playbackFadeController.volumeFaderOrNull()
            if (fader != null) {
                fader.applyResolvedVolume()
            } else {
                player.volume = resolvedVolume
            }
        }
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
        },
        progressPlayerProvider = {
            if (
                isTimedTransitionControllerInitialized() &&
                timedTransitionController.isCrossfadeActive() &&
                isCrossfadePlayerInitialized()
            ) {
                crossfadePlayer
            } else {
                null
            }
        }
    )

    widgetBridge = PlaybackWidgetBridge(
        widgetUpdateCoordinator = widgetUpdateCoordinator,
        scope = serviceScope,
        playModeProvider = {
            latestSettingPreferences.playMode
        }
    )

    sessionPlayer = QueueAwareSessionPlayer(
        player = player,
        onPlay = { resumePlayback() },
        onPause = { pausePlayback() },
        onSeekNext = { playNext() },
        onSeekPrevious = { playPrevious() }
    )
    sessionMetadata = PlaybackSessionMetadata(
        scope = serviceScope,
        sessionPlayer = { sessionPlayer },
        currentMusic = { queue.getOrNull(currentIndex) },
        queueSize = { queue.size },
        currentIndex = { currentIndex }
    )
    media3Session = buildMedia3Session()
    // Register app-created sessions too: onGetSession is not called when the
    // app starts playback through its own service commands. Registration connects
    // Media3's notification controller and publishes its custom system actions.
    addSession(checkNotNull(media3Session))
    updateMedia3CommandButtons()

    artworkController = CurrentArtworkController(
        defaultArtwork = defaultArtwork,
        artworkLoader = artworkLoader,
        observeAlbumPictureUseCase = observeAlbumPictureUseCase,
        queueManager = queueManager,
        runtimeStateStore = playbackRuntimeStateStore,
        sessionMetadata = sessionMetadata,
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

    val notificationManager =
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    notificationController = PlaybackNotificationController(
        service = this,
        notificationManager = notificationManager,
        mediaSessionTokenProvider = {
            media3Session?.platformToken
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
                queue: List<Music>,
                currentIndex: Int,
                fromAutoTransition: Boolean
            ): Int? {
                return playbackModeResolver.resolveNextIndex(
                    queue = queue,
                    currentIndex = currentIndex,
                    fromAutoTransition = fromAutoTransition
                )
            }

            override fun resolvePreviousIndex(
                queue: List<Music>,
                currentIndex: Int,
                shouldRestartCurrent: Boolean
            ): Int? {
                return playbackModeResolver.resolvePreviousIndex(
                    queue = queue,
                    currentIndex = currentIndex,
                    shouldRestartCurrent = shouldRestartCurrent
                )
            }

            override fun onAutoTransitionReachedQueueEnd() {
                stopAtQueueStart()
            }

            override fun markPreviousNavigated() {
                playbackModeResolver.markPreviousNavigated()
            }

            override fun onQueueInitialized(queue: List<Music>, currentIndex: Int) {
                playbackModeResolver.onQueueInitialized(queue, currentIndex)
            }

            override fun onQueueCleared() {
                playbackModeResolver.onQueueCleared()
            }

            override fun onTracksAppended(queue: List<Music>, added: List<Music>) {
                playbackModeResolver.onTracksAppended(queue, added)
            }

            override fun onTracksInsertedForNext(added: List<Music>) {
                playbackModeResolver.onTracksInsertedForNext(added)
            }

            override fun onQueueMutated(queue: List<Music>, currentIndex: Int) {
                playbackModeResolver.onQueueMutated(queue, currentIndex)
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
    volumeFader = playbackFadeController.bind(
        player = player,
        targetVolume = playbackTuningController::resolveTargetPlaybackVolume
    )
    // Crossfade player / TimedTransitionController: lazy — original only allocates
    // secondary MediaPlayer when crossfade runs (u6.e.l / f15431r).

    shutdownController = ShutdownController(
        callbacks = object : ShutdownCallbacks {

            override fun beginShutdown() {
                playbackShuttingDown = true
                if (isProgressTickerInitialized()) progressTicker.stopTickerOnly()
            }

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

            override fun clearSessionMetadata() {
                if (isSessionMetadataInitialized()) {
                    sessionMetadata.clear()
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
                // Disconnect browser controllers too, so bound clients cannot hold Quit open.
                media3Session?.let { session -> removeSession(session); session.release() }
                media3Session = null
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

internal fun MusicPlaybackService.registerScreenOffReceiver() {
    serviceScope.launch {
        val enabled = settingPreferencesDataStore.getLockScreenEnabled()
        latestSettingPreferences = latestSettingPreferences.copy(
            lockscreen = latestSettingPreferences.lockscreen.copy(
                lockScreenEnabled = enabled
            )
        )
        lockScreenController.refresh(enabled)
        if (isPlayerInitialized()) {
            lockScreenController.setPlaying(isEffectivelyPlaying())
        }
    }
}

internal fun MusicPlaybackService.unregisterScreenOffReceiver() {
    lockScreenController.unregister()
}

internal fun MusicPlaybackService.isNightMode(configuration: Configuration): Boolean {
    return (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
