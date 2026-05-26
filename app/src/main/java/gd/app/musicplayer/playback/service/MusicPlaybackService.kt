package gd.app.musicplayer.playback.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.SystemClock
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.AppForegroundTracker
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
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import gd.app.musicplayer.domain.repository.PlaylistRepo
import gd.app.musicplayer.domain.usecase.library.ObserveAlbumPictureUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import gd.app.musicplayer.domain.usecase.playlist.ToggleFavoriteTrackUseCase
import gd.app.musicplayer.playback.ArtworkLoader
import gd.app.musicplayer.playback.AudioEffectsManager
import gd.app.musicplayer.playback.AudioFocusController
import gd.app.musicplayer.playback.HeadsetMediaButtonHandler
import gd.app.musicplayer.playback.IndexActionData
import gd.app.musicplayer.playback.NotificationMediaSessionBridge
import gd.app.musicplayer.playback.PlaybackModeResolver
import gd.app.musicplayer.playback.PlaybackNotificationController
import gd.app.musicplayer.playback.PlaybackCommandPayloadStore
import gd.app.musicplayer.playback.PlaybackRuntimeStateStore
import gd.app.musicplayer.playback.PlaybackServiceCommandHandler
import gd.app.musicplayer.playback.PlaybackStatePublisher
import gd.app.musicplayer.playback.PlaybackStatsTracker
import gd.app.musicplayer.playback.PlaybackTuningController
import gd.app.musicplayer.playback.ScreenOffLockReceiver
import gd.app.musicplayer.playback.StereoBalanceAudioProcessor
import gd.app.musicplayer.playback.SleepTimerManager
import gd.app.musicplayer.playback.VolumeFader
import gd.app.musicplayer.ui.shell.MainActivity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import gd.app.musicplayer.playback.artwork.CurrentArtworkController
import gd.app.musicplayer.playback.favorite.CurrentFavoriteController
import gd.app.musicplayer.playback.notification.NotificationCloseCallbacks
import gd.app.musicplayer.playback.notification.NotificationCloseController
import gd.app.musicplayer.playback.player.MusicPlayerFactory
import gd.app.musicplayer.playback.player.PlayerEventHandler
import gd.app.musicplayer.playback.progress.PlaybackProgressTicker
import gd.app.musicplayer.playback.queue.PlaybackQueueManager
import gd.app.musicplayer.playback.queue.PlayerQueueController
import gd.app.musicplayer.playback.queue.QueueActionController
import gd.app.musicplayer.playback.queue.QueueMutationCallbacks
import gd.app.musicplayer.playback.state.PlaybackSnapshot
import gd.app.musicplayer.playback.state.PlaybackSnapshotManager
import gd.app.musicplayer.playback.restore.PlaybackRestoreManager
import gd.app.musicplayer.playback.restore.RestoreStatus
import gd.app.musicplayer.playback.shutdown.ShutdownCallbacks
import gd.app.musicplayer.playback.shutdown.ShutdownController
import gd.app.musicplayer.playback.shutdown.ShutdownOptions
import gd.app.musicplayer.playback.state.PlaybackStateOrchestrator
import gd.app.musicplayer.playback.state.PublishReason
import gd.app.musicplayer.playback.transition.TimedTransitionController
import gd.app.musicplayer.playback.desktop.DesktopLyricsOverlayController
import gd.app.musicplayer.playback.statusbar.StatusBarLyricsOverlayController
import gd.app.musicplayer.feature.widget.provider.WidgetUpdateCoordinator
import gd.app.musicplayer.feature.widget.provider.WidgetPlaybackSnapshot
import kotlinx.coroutines.NonCancellable


@AndroidEntryPoint
class MusicPlaybackService : MediaSessionService() {

    @Inject
    lateinit var musicPlayerFactory: MusicPlayerFactory

    @Inject
    lateinit var queueManager: PlaybackQueueManager

    private lateinit var snapshotManager: PlaybackSnapshotManager
    private lateinit var restoreManager: PlaybackRestoreManager
    private lateinit var playerEventHandler: PlayerEventHandler
    private lateinit var timedTransitionController: TimedTransitionController
    private lateinit var favoriteController: CurrentFavoriteController
    private lateinit var artworkController: CurrentArtworkController
    private lateinit var stateOrchestrator: PlaybackStateOrchestrator
    private lateinit var playerQueueController: PlayerQueueController
    private lateinit var queueActionController: QueueActionController
    private lateinit var shutdownController: ShutdownController
    private lateinit var notificationCloseController: NotificationCloseController
    private lateinit var desktopLyricsController: DesktopLyricsOverlayController
    private lateinit var statusBarLyricsController: StatusBarLyricsOverlayController

    @Inject
    lateinit var playbackRuntimeStateStore: PlaybackRuntimeStateStore

    @Inject
    lateinit var playbackQueueRepo: PlaybackQueueRepo

    @Inject
    lateinit var playlistRepo: PlaylistRepo

    @Inject
    lateinit var desktopLyricPreferenceStore: DesktopLyricPreferenceStore

    @Inject
    lateinit var statusBarLyricPreferenceStore: StatusBarLyricPreferenceStore

    @Inject
    lateinit var trackLyricPreferenceStore: TrackLyricPreferenceStore

    @Inject
    lateinit var playbackStatePreferenceStore: PlaybackStatePreferenceStore

    @Inject
    lateinit var settingPreferencesDataStore: SettingPreferencesDataStore

    @Inject
    lateinit var headsetMediaButtonHandler: HeadsetMediaButtonHandler

    @Inject
    lateinit var audioEffectsManager: AudioEffectsManager

    @Inject
    lateinit var soundEffectPreferences: SoundEffectPreferences

    @Inject
    lateinit var dispatchers: AppDispatchers

    @Inject
    lateinit var observeTracksUseCase: ObserveTracksUseCase

    @Inject
    lateinit var observeAlbumPictureUseCase: ObserveAlbumPictureUseCase

    @Inject
    lateinit var toggleFavoriteTrackUseCase: ToggleFavoriteTrackUseCase

    @Inject
    lateinit var musicDao: MusicDao

    @Inject
    lateinit var playbackStatsTracker: PlaybackStatsTracker

    @Inject
    lateinit var widgetUpdateCoordinator: WidgetUpdateCoordinator

    @Inject
    lateinit var commandPayloadStore: PlaybackCommandPayloadStore

    private lateinit var player: ExoPlayer
    private lateinit var crossfadePlayer: ExoPlayer
    private lateinit var serviceScope: CoroutineScope
    private lateinit var defaultArtwork: Bitmap

    private lateinit var audioFocusController: AudioFocusController
    private lateinit var artworkLoader: ArtworkLoader
    private lateinit var commandHandler: PlaybackServiceCommandHandler
    private lateinit var notificationSessionBridge: NotificationMediaSessionBridge
    private lateinit var notificationController: PlaybackNotificationController
    private lateinit var playbackModeResolver: PlaybackModeResolver
    private lateinit var playbackTuningController: PlaybackTuningController
    private lateinit var screenOffLockReceiver: ScreenOffLockReceiver
    private lateinit var statePublisher: PlaybackStatePublisher
    private lateinit var stereoBalanceAudioProcessor: StereoBalanceAudioProcessor
    private lateinit var crossfadeStereoBalanceAudioProcessor: StereoBalanceAudioProcessor
    private lateinit var volumeFader: VolumeFader
    private lateinit var crossfadeVolumeFader: VolumeFader

    private var media3Session: MediaSession? = null

    private lateinit var progressTicker: PlaybackProgressTicker

    private var resumeJob: Job? = null
    private var defaultQueueRestoreJob: Job? = null
    @Volatile
    private var pendingResumeAfterDefaultQueue = false
    private var screenReceiverRegistered = false

    private val queue: List<Music>
        get() = queueManager.queue

    private val currentIndex: Int
        get() = queueManager.currentIndex


    private var keepIdleNotification = false
    private var stopAfterCurrentTrack = false
    private var notificationDismissedByUser = false
    private var lastSessionAutoSaveElapsedMs = 0L
    private var pendingMedia3TransportCommand = NO_PLAYER_COMMAND
    private var pendingMedia3TransportStartIndex = NO_INDEX
    private var pendingMedia3TransportStartPositionMs = 0L
    private var pendingMedia3StopSnapshot: PlaybackSnapshot? = null
    private var widgetUpdateJob: Job? = null
    private var correctingMediaItemTransition = false
    private var suppressNextCrossfadeCommitTransition = false
    private var pendingRestoreTrackId: Long? = null
    private var isNightMode = false

    @Volatile
    private var latestSettingPreferences = SettingPreferences()

    @Volatile
    private var latestDesktopLyricPreference = DesktopLyricPreference()

    override fun onCreate() {
        super.onCreate()

        isRunning = true
        isNightMode = isNightMode(resources.configuration)

        serviceScope = CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate
        )

        defaultArtwork = BitmapFactory.decodeResource(
            resources,
            R.drawable.notify_default_album
        )

        configurePlayer()
        configureControllers()
        observePreferences()
        observeCurrentTrackArtwork()
        observeCurrentTrackFavorite()
        SleepTimerManager.setPlaybackActiveProvider(::isEffectivelyPlaying)

        notificationController.createNotificationChannel()

        restoreLastSessionIntoRuntimeStateIfNeeded()
        registerScreenOffReceiver()

        progressTicker.start()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? {
        return media3Session
    }

    override fun onUpdateNotification(
        session: androidx.media3.session.MediaSession,
        startInForegroundRequired: Boolean
    ) {
        if (!::notificationController.isInitialized) return

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

        val action = intent?.action ?: return handleEmptyStartCommand(startId)

        if (action.requiresImmediateForegroundPromotion()) {
            notificationDismissedByUser = false
            notificationController.ensureForegroundStarted()
        }

        val shouldContinue = commandHandler.handle(
            intent = intent,
            action = action
        )

        if (!shouldContinue) {
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        handleAppTaskRemoved()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val newNightMode = isNightMode(newConfig)

        if (newNightMode != isNightMode) {
            isNightMode = newNightMode
            updateNotification(force = true)
        }

        if (::desktopLyricsController.isInitialized) {
            desktopLyricsController.onConfigurationChanged()
        }
        if (::statusBarLyricsController.isInitialized) {
            statusBarLyricsController.onConfigurationChanged()
        }
    }

    override fun onDestroy() {
        if (::progressTicker.isInitialized) {
            progressTicker.shutdown()
        }

        resumeJob?.cancel()
        resumeJob = null
        defaultQueueRestoreJob?.cancel()
        defaultQueueRestoreJob = null
        pendingResumeAfterDefaultQueue = false
        if (::artworkController.isInitialized) {
            artworkController.stopObserving()
        }
        if (::favoriteController.isInitialized) {
            favoriteController.stopObserving()
        }

        if (::volumeFader.isInitialized) {
            volumeFader.cancel()
        }
        if (::crossfadeVolumeFader.isInitialized) {
            crossfadeVolumeFader.cancel()
        }

        if (::artworkController.isInitialized) {
            artworkController.clear()
        } else if (::artworkLoader.isInitialized) {
            artworkLoader.clear()
        }

        media3Session?.release()
        media3Session = null

        if (::notificationSessionBridge.isInitialized) {
            notificationSessionBridge.release()
        }

        if (::desktopLyricsController.isInitialized) {
            desktopLyricsController.destroy()
        }
        if (::statusBarLyricsController.isInitialized) {
            statusBarLyricsController.destroy()
        }

        if (::audioEffectsManager.isInitialized) {
            audioEffectsManager.release()
        }

        if (::timedTransitionController.isInitialized) {
            timedTransitionController.release()
        }

        if (::player.isInitialized) {
            if (::playerEventHandler.isInitialized) {
                player.removeListener(playerEventHandler)
            }

            player.release()
        }
        if (::crossfadePlayer.isInitialized) {
            crossfadePlayer.release()
        }

        if (::audioFocusController.isInitialized) {
            audioFocusController.abandon()
        }

        unregisterScreenOffReceiver()

        if (::serviceScope.isInitialized) {
            serviceScope.cancel()
        }

        SleepTimerManager.setPlaybackActiveProvider(null)

        isRunning = false

        super.onDestroy()
    }

    private fun handleEmptyStartCommand(startId: Int): Int {
        val hasQueueItem = currentIndex in queue.indices

        if (!isEffectivelyPlaying() && !hasQueueItem) {
            isRunning = false
            stopSelf(startId)
            return START_NOT_STICKY
        }

        publishPlaybackState(
            reason = PublishReason.UserAction,
            forceNotification = false
        )

        return START_STICKY
    }

    private fun handleAppTaskRemoved() {
        if (isEffectivelyPlaying()) {
            publishPlaybackState(
                reason = PublishReason.UserAction,
                forceNotification = true
            )
            return
        }

        // Mark as not running before shutdown publishes widget updates so loaders
        // prefer persisted playback snapshot and avoid transient stale runtime state.
        isRunning = false
        shutdownPlayback(ShutdownOptions.TaskRemovedWhenPaused)
    }

    private fun observePreferences() {
        observeSettingPreferences()
        observeDesktopLyricPreference()
        observeStatusBarLyricPreference()
        observeAudioEffectPreferences()
    }

    private fun observeCurrentTrackArtwork() {
        if (::artworkController.isInitialized) {
            artworkController.observe(serviceScope)
        }
    }

    private fun observeCurrentTrackFavorite() {
        if (::favoriteController.isInitialized) {
            favoriteController.observe(serviceScope)
        }
    }

    private fun observeSettingPreferences() {
        settingPreferencesDataStore.observeSettingPreferences()
            .onEach { preferences ->
                latestSettingPreferences = preferences
            }
            .launchIn(serviceScope)
    }

    private fun observeDesktopLyricPreference() {
        desktopLyricPreferenceStore.desktopLyricPreference
            .onEach { preference ->
                latestDesktopLyricPreference = preference
                if (::desktopLyricsController.isInitialized) {
                    desktopLyricsController.renderPreference(preference)
                }
                updateNotification(force = true)
            }
            .launchIn(serviceScope)

        AppForegroundTracker.isForeground
            .onEach { isForeground ->
                if (::desktopLyricsController.isInitialized) {
                    desktopLyricsController.renderAppForeground(isForeground)
                }
            }
            .launchIn(serviceScope)

        playbackRuntimeStateStore.state
            .onEach { state ->
                if (::desktopLyricsController.isInitialized) {
                    desktopLyricsController.renderPlaybackState(state)
                }
            }
            .launchIn(serviceScope)
    }

    private fun observeStatusBarLyricPreference() {
        statusBarLyricPreferenceStore.preference
            .onEach { preference ->
                if (::statusBarLyricsController.isInitialized) {
                    statusBarLyricsController.renderPreference(preference)
                }
            }
            .launchIn(serviceScope)

        playbackRuntimeStateStore.state
            .onEach { state ->
                if (::statusBarLyricsController.isInitialized) {
                    statusBarLyricsController.renderPlaybackState(state)
                }
            }
            .launchIn(serviceScope)
    }

    private fun observeAudioEffectPreferences() {
        combine(
            soundEffectPreferences.equalizerPreference,
            soundEffectPreferences.soundEffectSettings
        ) { equalizerPreference, soundEffectSettings ->
            equalizerPreference to soundEffectSettings
        }
            .distinctUntilChanged()
            .onEach {
                if (::player.isInitialized) {
                    applyAudioEffectsFromPreferences()
                }
            }
            .launchIn(serviceScope)
    }

    private fun configurePlayer() {
        stereoBalanceAudioProcessor = StereoBalanceAudioProcessor()
        crossfadeStereoBalanceAudioProcessor = StereoBalanceAudioProcessor()

        player = musicPlayerFactory.create(
            context = this,
            stereoBalanceAudioProcessor = stereoBalanceAudioProcessor
        )
        crossfadePlayer = musicPlayerFactory.create(
            context = this,
            stereoBalanceAudioProcessor = crossfadeStereoBalanceAudioProcessor
        )
        crossfadePlayer.volume = 0f

        playerEventHandler = PlayerEventHandler(
            callbacks = object : PlayerEventHandler.Callbacks {

                override fun onPlayerReady() {
                    applyAudioEffectsFromPreferences()
                    publishAllRuntimeState()
                }

                override fun onTrackEnded() {
                    handleTrackEnded()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        applyAudioEffectsFromPreferences()

                        playbackStatsTracker.onTrackStarted(
                            music = queue.getOrNull(currentIndex)
                        )
                    }

                    publishAllRuntimeState(forceNotification = true)
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
                    if (
                        playWhenReady &&
                        ::audioFocusController.isInitialized &&
                        !audioFocusController.request()
                    ) {
                        player.pause()
                        return
                    }

                    publishAllRuntimeState(forceNotification = true)
                }

                override fun onMediaItemTransition(reason: Int) {
                    handleMediaItemTransition(reason)
                }

                override fun onPlayerError(error: PlaybackException) {
                    playNextInternal()
                }
            }
        )

        player.addListener(playerEventHandler)
    }


    private fun handleMediaItemTransition(reason: Int) {
        if (
            suppressNextCrossfadeCommitTransition &&
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
        ) {
            suppressNextCrossfadeCommitTransition = false
            return
        }

        if (maybeCorrectExternalMediaItemTransition(reason)) {
            return
        }

        val playerIndex = player.currentMediaItemIndex

        if (playerIndex in queue.indices) {
            queueManager.updateCurrentIndex(playerIndex)
        }

        resetTimedTransitionState()
        if (::volumeFader.isInitialized) {
            volumeFader.applyResolvedVolume()
        } else {
            playbackTuningController.applyResolvedPlayerVolume()
        }
        refreshArtworkAndSession(force = true)

        if (player.isPlaying) {
            playbackStatsTracker.onTrackStarted(
                music = queue.getOrNull(currentIndex),
                force = true
            )
        }

        persistSessionFromCurrentStateAsync()
        updateMedia3CommandButtons()
        publishAllRuntimeState(forceNotification = true)
    }

    private fun maybeCorrectExternalMediaItemTransition(reason: Int): Boolean {
        if (correctingMediaItemTransition) {
            correctingMediaItemTransition = false
            clearPendingMedia3TransportCommand()
            return false
        }

        val playerIndex = player.currentMediaItemIndex

        val expectedIndex = when {
            isPendingMedia3TransportTransition(reason) -> {
                resolvePendingMedia3TransportTargetIndex()
            }

            reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> {
                playbackModeResolver.resolveNextIndex(
                    queueSize = queue.size,
                    currentIndex = currentIndex,
                    fromAutoTransition = true
                )
            }

            else -> return false
        }

        clearPendingMedia3TransportCommand()

        if (expectedIndex == null) {
            stopAtQueueStart()
            return true
        }

        if (expectedIndex !in queue.indices || playerIndex == expectedIndex) {
            return false
        }

        correctingMediaItemTransition = true
        player.seekTo(
            expectedIndex,
            0L
        )
        return true
    }

    private fun isPendingMedia3TransportTransition(reason: Int): Boolean {
        return pendingMedia3TransportCommand != NO_PLAYER_COMMAND &&
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
    }

    private fun resolvePendingMedia3TransportTargetIndex(): Int? {
        val startIndex = pendingMedia3TransportStartIndex
            .takeIf { index -> index in queue.indices }
            ?: currentIndex

        return when (pendingMedia3TransportCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                playbackModeResolver.resolveNextIndex(
                    queueSize = queue.size,
                    currentIndex = startIndex,
                    fromAutoTransition = false
                )
            }

            Player.COMMAND_SEEK_TO_PREVIOUS -> {
                playbackModeResolver.resolvePreviousIndex(
                    queueSize = queue.size,
                    currentIndex = startIndex,
                    shouldRestartCurrent = pendingMedia3TransportStartPositionMs >
                            PREVIOUS_RESTART_WINDOW_MS
                )
            }

            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                playbackModeResolver.resolvePreviousIndex(
                    queueSize = queue.size,
                    currentIndex = startIndex,
                    shouldRestartCurrent = false
                )
            }

            else -> null
        }
    }

    private fun clearPendingMedia3TransportCommand() {
        pendingMedia3TransportCommand = NO_PLAYER_COMMAND
        pendingMedia3TransportStartIndex = NO_INDEX
        pendingMedia3TransportStartPositionMs = 0L
    }

    @OptIn(UnstableApi::class)
    private fun configureControllers() {

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
                    this@MusicPlaybackService.togglePlayPause()
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
                    this@MusicPlaybackService.togglePlayPause()
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
                override fun seekTo(positionMs: Int) = this@MusicPlaybackService.seekTo(positionMs)
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
                if (::artworkController.isInitialized) {
                    artworkController.currentArtwork
                } else {
                    null
                }
            },
            artworkTrackIdProvider = {
                if (::artworkController.isInitialized) {
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
                    this@MusicPlaybackService.persistSessionFromCurrentStateAsync()
                }

                override fun persistCurrentTrackProgress(positionMs: Int) {
                    this@MusicPlaybackService.persistCurrentTrackProgressAsync(positionMs)
                }

                override fun resumePlaybackInternal() {
                    this@MusicPlaybackService.resumePlaybackInternal()
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
                    this@MusicPlaybackService.applyVolumeForPlaybackStart(playWhenReady)
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
                    return this@MusicPlaybackService.isEffectivelyPlaying()
                }

                override fun currentPlayerPositionMs(): Long {
                    return player.currentPosition.coerceAtLeast(0L)
                }

                override fun updateNotificationSessionQueue() {
                    notificationSessionBridge.updateQueue()
                }

                override fun refreshArtworkAndSession(force: Boolean) {
                    this@MusicPlaybackService.refreshArtworkAndSession(force)
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
            }
        )

        commandHandler = PlaybackServiceCommandHandler(
            service = this,
            payloadStore = commandPayloadStore,
            callbacks = object : PlaybackServiceCommandHandler.Callbacks {

                override fun refreshNotificationStyle() {
                    serviceScope.launch {
                        latestSettingPreferences =
                            settingPreferencesDataStore.observeSettingPreferences().first()
                        notificationController.refreshStyle { delayMs, block ->
                            progressTicker.postDelayed(
                                block = block,
                                delayMs = delayMs
                            )
                        }
                    }
                }

                override fun exitService() {
                    this@MusicPlaybackService.exitService()
                }

                override fun pauseAndPersistForNotificationClose() {
                    this@MusicPlaybackService.pauseAndPersistForNotificationClose()
                }

                override fun togglePlayPause() {
                    this@MusicPlaybackService.togglePlayPause()
                }

                override fun resumePlayback() {
                    this@MusicPlaybackService.resumePlayback()
                }

                override fun pausePlayback() {
                    this@MusicPlaybackService.pausePlayback()
                }

                override fun playNext() {
                    this@MusicPlaybackService.playNext()
                }

                override fun playPrevious() {
                    this@MusicPlaybackService.playPrevious()
                }

                override fun stopPlayback() {
                    this@MusicPlaybackService.stopPlayback()
                }

                override fun stopPlaybackWithoutClearingQueue() {
                    this@MusicPlaybackService.stopPlaybackWithoutClearingQueue()
                }

                override fun restartCurrentTrack() {
                    this@MusicPlaybackService.restartCurrentTrack()
                }

                override fun clearQueueKeepingNotification() {
                    this@MusicPlaybackService.clearQueueKeepingNotification()
                }

                override fun cyclePlaybackMode() {
                    playbackModeResolver.cyclePlaybackMode()
                }

                override fun setPlaybackMode(mode: Int) {
                    playbackModeResolver.setPlaybackMode(mode)
                }

                override fun toggleCurrentFavorite() {
                    this@MusicPlaybackService.toggleCurrentFavorite()
                }

                override fun setCurrentFavorite(isFavorite: Boolean) {
                    this@MusicPlaybackService.setCurrentFavorite(isFavorite)
                }

                override fun playIndex(index: Int) {
                    this@MusicPlaybackService.playIndexFromCommand(index)
                }

                override fun playFromQueue(
                    queue: List<Music>,
                    index: Int
                ) {
                    this@MusicPlaybackService.handlePlayFromQueue(
                        incomingQueue = queue,
                        incomingIndex = index
                    )
                }

                override fun enqueue(queue: List<Music>) {
                    this@MusicPlaybackService.handleEnqueue(queue)
                }

                override fun playNextQueue(queue: List<Music>) {
                    this@MusicPlaybackService.handlePlayNextQueue(queue)
                }

                override fun replaceQueue(
                    queue: List<Music>,
                    index: Int
                ) {
                    this@MusicPlaybackService.replaceQueue(
                        newQueue = queue,
                        requestedIndex = index
                    )
                }

                override fun removeQueueItem(index: Int) {
                    this@MusicPlaybackService.removeQueueItem(index)
                }

                  override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
                      this@MusicPlaybackService.moveQueueItem(
                          fromIndex = fromIndex,
                          toIndex = toIndex
                      )
                  }

                  override fun updateTrackMetadata(music: Music) {
                      this@MusicPlaybackService.updateEditedTrackMetadata(music)
                  }

                  override fun updateTracksMetadata(music: List<Music>) {
                      this@MusicPlaybackService.updateEditedTracksMetadata(music)
                  }

                  override fun seekTo(positionMs: Int) {
                      this@MusicPlaybackService.seekTo(positionMs)
                  }

                override fun setStopAfterCurrentTrack(enabled: Boolean) {
                    updateStopAfterCurrentTrackMode(enabled)
                }

                override fun applyAudioEffects() {
                    applyAudioEffectsFromPreferences()
                }

                override fun applyPlaybackTuning() {
                    playbackTuningController.applyPlaybackTuning()
                }

                override fun setDesktopLyricsLocked(locked: Boolean) {
                    serviceScope.launch {
                        desktopLyricPreferenceStore.setLocked(locked)
                    }
                }

                override fun currentMusic(): Music? {
                    return queue.getOrNull(currentIndex)
                }
            }
        )

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
                    return this@MusicPlaybackService.capturePlaybackSnapshot()
                }

                override fun persistPlaybackSnapshotBlocking(
                    snapshot: PlaybackSnapshot,
                    persistQueue: Boolean
                ) {
                    this@MusicPlaybackService.persistPlaybackSnapshotBlocking(
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
                    if (::volumeFader.isInitialized) {
                        volumeFader.cancel()
                    }
                }

                override fun stopAndClearPlayer() {
                    if (::player.isInitialized) {
                        player.pause()
                        player.stop()
                        player.clearMediaItems()
                    }
                }

                override fun abandonAudioFocus() {
                    if (::audioFocusController.isInitialized) {
                        audioFocusController.abandon()
                    }
                }

                override fun clearArtworkState() {
                    this@MusicPlaybackService.clearArtworkState()
                }

                override fun clearQueueState() {
                    this@MusicPlaybackService.clearQueueState()
                }

                override fun clearPersistedPlaybackState() {
                    this@MusicPlaybackService.clearPersistedPlaybackState()
                }

                override fun clearPersistedQueue() {
                    clearPersistedQueueBlocking()
                }

                override fun clearNotificationSession() {
                    if (::notificationSessionBridge.isInitialized) {
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
                    this@MusicPlaybackService.publishStateAfterShutdown(snapshot)
                }

                override fun removeNotification() {
                    if (::notificationController.isInitialized) {
                        notificationController.stopForegroundAndRemove()
                    }
                }

                override fun onServiceShouldStop() {
                    isRunning = false
                    stopSelf()
                }
            }
        )

        notificationCloseController = NotificationCloseController(
            callbacks = object : NotificationCloseCallbacks {

                override fun capturePlaybackSnapshot(): PlaybackSnapshot {
                    return this@MusicPlaybackService.capturePlaybackSnapshot()
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
                    this@MusicPlaybackService.persistPlaybackSnapshotBlocking(
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

    private fun handleProgressTick() {
        if (::timedTransitionController.isInitialized) {
            timedTransitionController.maybeHandleTimedTransition()
        }

        if (player.isPlaying) {
            playbackStatsTracker.onProgress(
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = resolveCurrentSnapshotDurationMs(
                    track = queue.getOrNull(currentIndex)
                )
            )
        }

        if (shouldPublishProgressState()) {
            publishPlaybackState(
                reason = PublishReason.ProgressTick,
                forceNotification = false,
                forceWidgetUpdate = false
            )

            maybePersistSessionFromProgressTick()
        }
    }

    private fun buildMedia3Session(): MediaSession {
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

    private val media3SessionCallback = @UnstableApi
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
            when (customCommand.customAction) {
                MEDIA3_COMMAND_TOGGLE_FAVORITE -> toggleCurrentFavorite()
                MEDIA3_COMMAND_CYCLE_PLAYBACK_MODE -> playbackModeResolver.cyclePlaybackMode()
                MEDIA3_COMMAND_CLOSE_NOTIFICATION -> pauseAndPersistForNotificationClose()
                MEDIA3_COMMAND_STOP_AFTER_CURRENT -> {
                    updateStopAfterCurrentTrackMode(!stopAfterCurrentTrack)
                    publishAllRuntimeState(forceNotification = true)
                }

                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }

            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        @Suppress("DEPRECATION")
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            if (playerCommand.isMedia3TransportNavigationCommand()) {
                pendingMedia3TransportCommand = playerCommand
                pendingMedia3TransportStartIndex = currentIndex
                pendingMedia3TransportStartPositionMs = player.currentPosition.coerceAtLeast(0L)
            } else if (playerCommand == Player.COMMAND_STOP) {
                pendingMedia3StopSnapshot = capturePlaybackSnapshot()
            }

            return SessionResult.RESULT_SUCCESS
        }

        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future =
                SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()

            serviceScope.launch {
                runCatching {
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
                        0,
                        mediaItems.lastIndex
                    )

                    MediaSession.MediaItemsWithStartPosition(
                        mediaItems,
                        startIndex,
                        resolveCurrentSnapshotPositionMs()
                    )
                }.onSuccess { result ->
                    future.set(result)
                }.onFailure { error ->
                    future.setException(error)
                }
            }

            return future
        }

        @OptIn(UnstableApi::class)
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            val event: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }

            if (event?.action != KeyEvent.ACTION_DOWN) return false

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

    private fun Int.isMedia3TransportNavigationCommand(): Boolean {
        return this == Player.COMMAND_SEEK_TO_NEXT ||
                this == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                this == Player.COMMAND_SEEK_TO_PREVIOUS ||
                this == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
    }

    private fun restoreLastSessionIntoRuntimeStateIfNeeded() {
        serviceScope.launch {
            ensurePlaybackRestored()
        }
    }

    private suspend fun ensurePlaybackRestored() {
        restoreManager.ensureRestored(
            onRestored = { restored ->
                val restoredTrackId = restored.queue.getOrNull(restored.index)?.id

                setQueueState(
                    newQueue = restored.queue,
                    requestedIndex = restored.index
                )

                val restoredIndex = currentIndex
                val restoredPositionMs = if (
                    queue.getOrNull(restoredIndex)?.id == restoredTrackId
                ) {
                    restored.positionMs
                } else {
                    0L
                }

                pendingRestoreTrackId = restoredTrackId
                prepareRestoredPlayerState(
                    index = restoredIndex,
                    positionMs = restoredPositionMs
                )

                syncControllersAfterQueueRestore(forceArtwork = true)

                stateOrchestrator.publishRestored(
                    index = restoredIndex,
                    positionMs = restoredPositionMs,
                    queue = queue
                )
            },
            onEmpty = {
                pendingRestoreTrackId = null
                clearQueueState()

                syncControllersAfterQueueRestore(forceArtwork = false)

                publishPlaybackState(
                    reason = PublishReason.Restore,
                    forceNotification = true
                )
            },
            onFailed = {
                pendingRestoreTrackId = null
                publishPlaybackState(
                    reason = PublishReason.Restore,
                    forceNotification = true
                )
            }
        )
    }


    private fun syncControllersAfterQueueRestore(forceArtwork: Boolean) {
        notificationSessionBridge.updateQueue()
        refreshArtworkAndSession(force = forceArtwork)
        notificationSessionBridge.updatePlaybackState()
        updateNotification(force = true)
    }

    private fun handleMedia3PlayerInteractionFinished() {
        pendingMedia3StopSnapshot?.let { snapshot ->
            handleMedia3StopFinished(snapshot)
            pendingMedia3StopSnapshot = null
            clearPendingMedia3TransportCommand()
            return
        }

        syncCurrentIndexWithPlayer()
        persistSessionFromCurrentStateAsync()
        updateMedia3CommandButtons()
        publishAllRuntimeState(forceNotification = true)
        clearPendingMedia3TransportCommand()
    }

    private fun handleMedia3StopFinished(snapshot: PlaybackSnapshot) {
        keepIdleNotification = false
        updateStopAfterCurrentTrackMode(false)
        notificationDismissedByUser = false

        playbackStatsTracker.reset()
        resetTimedTransitionState()

        if (::audioFocusController.isInitialized) {
            audioFocusController.abandon()
        }

        persistPlaybackSnapshotAsync(
            snapshot = snapshot,
            persistQueue = false
        )
        updateMedia3CommandButtons()
        publishStateAfterShutdown(snapshot)
        updateNotification(force = true)
    }

    private fun prepareRestoredPlayerState(
        index: Int,
        positionMs: Long
    ) {
        if (index !in queue.indices) return

        setPlayerQueue(
            queue = queue,
            startIndex = index,
            startPositionMs = positionMs,
            playWhenReady = false
        )

        if (::volumeFader.isInitialized) {
            volumeFader.applyResolvedVolume()
        } else {
            playbackTuningController.applyResolvedPlayerVolume()
        }
    }

    private fun handleTrackEnded() {
        if (
            ::timedTransitionController.isInitialized &&
            timedTransitionController.consumeTrackEndedDuringCrossfade()
        ) {
            return
        }

        resetTimedTransitionState()

        playbackStatsTracker.onTrackEnded(
            music = queue.getOrNull(currentIndex)
        )

        if (stopAfterCurrentTrack) {
            updateStopAfterCurrentTrackMode(false)
            val timerAction = SleepTimerManager.state.value.action
            if (timerAction == gd.app.musicplayer.playback.SleepTimerState.ACTION_STOP_PLAYBACK) {
                SleepTimerManager.finishPendingTrackEnd(executeAction = false)
                pauseAtTrackEndForSleepTimer()
            } else {
                SleepTimerManager.finishPendingTrackEnd()
            }
            return
        }

        playNextInternal(fromAutoTransition = true)
    }

    private fun pauseAtTrackEndForSleepTimer() {
        if (::volumeFader.isInitialized) {
            volumeFader.cancel()
            volumeFader.resetToFullVolume()
        }

        if (::audioFocusController.isInitialized) {
            audioFocusController.abandon()
        }

        if (::player.isInitialized) {
            player.playWhenReady = false
        }

        persistSessionFromCurrentStateAsync()
        updateMedia3CommandButtons()
        publishAllRuntimeState(forceNotification = true)
    }

    @OptIn(UnstableApi::class)
    private fun updateStopAfterCurrentTrackMode(enabled: Boolean) {
        stopAfterCurrentTrack = enabled
        if (::player.isInitialized) {
            player.pauseAtEndOfMediaItems = enabled
        }
    }

    private fun resumeWithDefaultQueue() {
        if (defaultQueueRestoreJob?.isActive == true) return

        defaultQueueRestoreJob = serviceScope.launch {
            val tracks = withContext(dispatchers.io) {
                runCatching {
                    observeTracksUseCase(MusicSet.Tracks).first()
                }.getOrDefault(emptyList())
            }

            if (tracks.isEmpty()) {
                pendingResumeAfterDefaultQueue = false
                playbackRuntimeStateStore.initializeIfNeeded()
                publishPlaybackState(
                    reason = PublishReason.Restore,
                    forceNotification = true
                )
                return@launch
            }

            val playableTracks = filterPlayableQueue(tracks)

            if (playableTracks.isEmpty()) {
                pendingResumeAfterDefaultQueue = false
                playbackRuntimeStateStore.initializeIfNeeded()
                publishPlaybackState(
                    reason = PublishReason.Restore,
                    forceNotification = true
                )
                return@launch
            }

            withContext(dispatchers.io) {
                playbackQueueRepo.replaceQueue(playableTracks)
                playbackStatePreferenceStore.setMusicProgress(
                    trackId = playableTracks.first().id,
                    progressMs = 0,
                    currentIndex = 0
                )
            }

            setQueueState(
                newQueue = playableTracks,
                requestedIndex = 0
            )

            restoreManager.markRestored()

            notificationSessionBridge.updateQueue()

            val shouldAutoResume = pendingResumeAfterDefaultQueue
            pendingResumeAfterDefaultQueue = false

            if (!audioFocusController.request()) {
                return@launch
            }

            setPlayerQueue(
                queue = queue,
                startIndex = 0,
                startPositionMs = 0L,
                playWhenReady = shouldAutoResume
            )

            applyVolumeForPlaybackStart(playWhenReady = shouldAutoResume)
            refreshArtworkAndSession(force = true)
            publishAllRuntimeState(forceNotification = true)
        }
    }

    private fun handlePlayFromQueue(
        incomingQueue: List<Music>,
        incomingIndex: Int
    ) {
        serviceScope.launch {
            ensurePlaybackRestored()

            if (!::queueActionController.isInitialized) return@launch

            queueActionController.playFromQueue(
                incomingQueue = incomingQueue,
                incomingIndex = incomingIndex
            )
        }
    }

    private fun handleEnqueue(incomingQueue: List<Music>) {
        serviceScope.launch {
            ensurePlaybackRestored()

            if (!::queueActionController.isInitialized) return@launch

            queueActionController.enqueue(incomingQueue)
        }
    }

    private fun handlePlayNextQueue(incomingQueue: List<Music>) {
        serviceScope.launch {
            ensurePlaybackRestored()

            if (!::queueActionController.isInitialized) return@launch

            queueActionController.playNextQueue(incomingQueue)
        }
    }

    private fun removeQueueItem(index: Int) {
        serviceScope.launch {
            ensurePlaybackRestored()

            if (!::queueActionController.isInitialized) return@launch

            queueActionController.removeQueueItem(index)
        }
    }

    private fun moveQueueItem(
        fromIndex: Int,
        toIndex: Int
    ) {
        serviceScope.launch {
            ensurePlaybackRestored()

            if (!::queueActionController.isInitialized) return@launch

            queueActionController.moveQueueItem(
                fromIndex = fromIndex,
                toIndex = toIndex
            )
        }
    }

    private fun replaceQueue(
        newQueue: List<Music>,
        requestedIndex: Int
    ) {
        serviceScope.launch {
            ensurePlaybackRestored()

            if (!::queueActionController.isInitialized) return@launch

            queueActionController.replaceQueue(
                newQueue = newQueue,
                requestedIndex = requestedIndex
            )
        }
    }

    private fun playIndexFromCommand(index: Int) {
        serviceScope.launch {
            ensurePlaybackRestored()

            playIndex(
                index = index,
                playWhenReady = true
            )
        }
    }

    private fun playIndex(
        index: Int,
        playWhenReady: Boolean
    ) {
        if (!::queueActionController.isInitialized) return

        queueActionController.playIndex(
            index = index,
            playWhenReady = playWhenReady
        )
    }

    private fun setPlayerQueue(
        queue: List<Music>,
        startIndex: Int,
        startPositionMs: Long = 0L,
        playWhenReady: Boolean
    ) {
        if (!::playerQueueController.isInitialized) return

        playerQueueController.setPlayerQueue(
            queue = queue,
            startIndex = startIndex,
            startPositionMs = startPositionMs,
            playWhenReady = playWhenReady
        )
    }

    private fun isPlayerPlaylistSynced(): Boolean {
        if (!::playerQueueController.isInitialized) return false

        return playerQueueController.isPlayerPlaylistSynced()
    }

    private fun applyVolumeForPlaybackStart(playWhenReady: Boolean) {
        if (
            playWhenReady &&
            playbackTuningController.isPlayPauseFadeEnabled()
        ) {
            volumeFader.fadeIn(
                durationMs = PLAY_PAUSE_FADE_DURATION_MS
            )
        } else {
            volumeFader.resetToFullVolume()
        }
    }

    private fun togglePlayPause() {
        serviceScope.launch {
            ensurePlaybackRestored()

            if (isEffectivelyPlaying()) {
                pausePlaybackInternal()
            } else {
                resumePlaybackInternal()
            }
        }
    }

    private fun resumePlayback() {
        if (resumeJob?.isActive == true) return

        resumeJob = serviceScope.launch {
            ensurePlaybackRestored()
            resumePlaybackInternal()
        }
    }

    private fun resumePlaybackInternal() {
        if (queue.isEmpty()) {
            pendingResumeAfterDefaultQueue = true
            resumeWithDefaultQueue()
            return
        }

        syncCurrentIndexWithPlayer()

        if (!audioFocusController.request()) return

        if (currentIndex !in queue.indices) {
            playIndex(
                index = 0,
                playWhenReady = true
            )
            return
        }

        if (!isPlayerPlaylistSynced()) {
            setPlayerQueue(
                queue = queue,
                startIndex = currentIndex,
                startPositionMs = player.currentPosition.coerceAtLeast(0L),
                playWhenReady = true
            )

            applyVolumeForPlaybackStart(playWhenReady = true)
            refreshArtworkAndSession(force = true)
            publishAllRuntimeState(forceNotification = true)
            return
        }

        if (player.playbackState == Player.STATE_IDLE) {
            playIndex(
                index = currentIndex,
                playWhenReady = true
            )
            return
        }

        if (!player.isPlaying) {
            playbackTuningController.applyPlaybackTuning()
            applyAudioEffectsFromPreferences()

            if (playbackTuningController.isPlayPauseFadeEnabled()) {
                volumeFader.muteImmediately()
                player.play()
                volumeFader.fadeIn(
                    durationMs = PLAY_PAUSE_FADE_DURATION_MS
                )
            } else {
                volumeFader.resetToFullVolume()
                player.play()
            }

            publishAllRuntimeState(forceNotification = true)
        }
    }

    private fun pausePlayback(
        withFade: Boolean = playbackTuningController.isPlayPauseFadeEnabled()
    ) {
        pausePlaybackInternal(withFade)
    }

    private fun pausePlaybackInternal(
        withFade: Boolean = playbackTuningController.isPlayPauseFadeEnabled()
    ) {
        if (!isEffectivelyPlaying()) return

        cancelTimedTransitionAndRestoreVolume()

        if (withFade && player.isPlaying) {
            volumeFader.fadeOut(
                durationMs = PLAY_PAUSE_FADE_DURATION_MS
            ) {
                player.pause()

                // Restore normal volume while paused so the next play starts from a clean state.
                volumeFader.resetToFullVolume()

                persistCurrentTrackProgressFromPlayerAsync()
                persistSessionFromCurrentStateAsync()
                updateMedia3CommandButtons()
                publishAllRuntimeState(forceNotification = true)
            }
            return
        }

        player.pause()
        persistCurrentTrackProgressFromPlayerAsync()
        persistSessionFromCurrentStateAsync()
        updateMedia3CommandButtons()
        publishAllRuntimeState(forceNotification = true)
    }

    @OptIn(UnstableApi::class)
    private fun updateMedia3CommandButtons() {
        val session = media3Session ?: return
        val buttons = buildMedia3CommandButtons()

        session.setCustomLayout(buttons)
        session.setMediaButtonPreferences(buttons)
    }

    private fun buildMedia3CommandButtons(): List<CommandButton> {
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

    private fun restartCurrentTrack() {
        serviceScope.launch {
            ensurePlaybackRestored()

            if (!::queueActionController.isInitialized) return@launch

            queueActionController.restartCurrentTrack()
        }
    }

    private fun playNext(fromAutoTransition: Boolean = false) {
        serviceScope.launch {
            ensurePlaybackRestored()
            playNextInternal(fromAutoTransition)
        }
    }

    private fun playNextInternal(fromAutoTransition: Boolean = false) {
        if (!::queueActionController.isInitialized) return

        queueActionController.playNext(
            fromAutoTransition = fromAutoTransition
        )
    }

    private fun commitCrossfadeTransition(
        nextIndex: Int,
        positionMs: Long
    ) {
        if (
            !::playerQueueController.isInitialized ||
            nextIndex !in queue.indices
        ) {
            return
        }

        if (!isPlayerPlaylistSynced()) {
            setPlayerQueue(
                queue = queue,
                startIndex = nextIndex,
                startPositionMs = positionMs,
                playWhenReady = true
            )
        } else {
            suppressNextCrossfadeCommitTransition = true
            playerQueueController.seekTo(
                index = nextIndex,
                positionMs = positionMs
            )
            playerQueueController.setPlayWhenReady(true)
            playerQueueController.play()
        }

        queueManager.updateCurrentIndex(nextIndex)
        persistCurrentTrackProgressAsync(
            positionMs = positionMs
                .coerceAtLeast(0L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        )
        playbackStatsTracker.reset()
        playbackTuningController.applyPlaybackTuning()

        refreshArtworkAndSession(force = true)
        persistSessionFromCurrentStateAsync()
        updateMedia3CommandButtons()
        publishAllRuntimeState(forceNotification = true)
    }

    private fun playPrevious() {
        serviceScope.launch {
            ensurePlaybackRestored()
            playPreviousInternal()
        }
    }

    private fun playPreviousInternal() {
        if (!::queueActionController.isInitialized) return

        queueActionController.playPrevious()
    }

    private fun seekTo(positionMs: Int) {
        serviceScope.launch {
            ensurePlaybackRestored()
            seekToInternal(positionMs)
        }
    }

    private fun seekToInternal(positionMs: Int) {
        if (!::queueActionController.isInitialized) return

        queueActionController.seekTo(positionMs)
    }

    private fun applyAudioEffectsFromPreferences() {
        serviceScope.launch {
            playbackTuningController.refreshSoundBalanceFromPreferences()
            audioEffectsManager.applyFromPreferences(player)
            if (::volumeFader.isInitialized) {
                volumeFader.applyResolvedVolume()
            } else {
                playbackTuningController.applyResolvedPlayerVolumeOnMain()
            }
        }
    }

    private fun stopPlayback() {
        keepIdleNotification = false
        updateStopAfterCurrentTrackMode(false)
        notificationDismissedByUser = false
        val snapshot = capturePlaybackSnapshot()

        playbackStatsTracker.reset()
        resetTimedTransitionState()

        if (::volumeFader.isInitialized) {
            volumeFader.cancel()
        }

        persistCurrentTrackProgressBlocking(
            track = snapshot.currentTrack,
            positionMs = snapshot.positionMs,
            currentIndex = snapshot.currentIndex
        )
        persistPlaybackSnapshotBlocking(
            snapshot = snapshot,
            persistQueue = false
        )

        if (::player.isInitialized) {
            player.pause()
            player.stop()
        }

        if (::audioFocusController.isInitialized) {
            audioFocusController.abandon()
        }

        publishStateAfterShutdown(snapshot)
        updateNotification(force = true)
    }

    private fun stopAndClearQueue() {
        notificationDismissedByUser = false
        shutdownPlayback(ShutdownOptions.StopAndClearQueue)
    }

    private fun stopPlaybackWithoutClearingQueue() {
        notificationDismissedByUser = false
        shutdownPlayback(ShutdownOptions.StopWithoutClearingQueue)
    }

    private fun stopAtQueueStart() {
        notificationDismissedByUser = false
        updateStopAfterCurrentTrackMode(false)

        if (queue.isEmpty()) {
            stopAndClearQueue()
            return
        }

        playbackStatsTracker.reset()
        resetTimedTransitionState()

        if (::volumeFader.isInitialized) {
            volumeFader.cancel()
        }

        queueManager.updateCurrentIndex(0)

        if (::playerQueueController.isInitialized) {
            playerQueueController.setPlayerQueue(
                queue = queue,
                startIndex = 0,
                startPositionMs = 0L,
                playWhenReady = false
            )
        } else if (::player.isInitialized) {
            player.pause()
            player.seekTo(0L)
        }

        if (::audioFocusController.isInitialized) {
            audioFocusController.abandon()
        }

        persistPlaybackSnapshotBlocking(
            snapshot = capturePlaybackSnapshot(),
            persistQueue = true
        )
        persistCurrentTrackProgressAsync(0)

        refreshArtworkAndSession(force = true)
        publishAllRuntimeState(forceNotification = true)
    }

    private fun pauseAndPersistForNotificationClose() {
        if (!::notificationCloseController.isInitialized) return

        notificationCloseController.pauseAndCloseNotification()
    }

    private fun exitService() {
        shutdownPlayback(ShutdownOptions.ExitService)
    }

    private fun clearQueueKeepingNotification() {
        keepIdleNotification = true
        notificationDismissedByUser = false
        shutdownPlayback(ShutdownOptions.ClearQueueKeepingNotification)
        notificationController.stopForegroundDetached()
        notificationController.update(force = true)
    }

    private fun shutdownPlayback(options: ShutdownOptions) {
        keepIdleNotification = false
        updateStopAfterCurrentTrackMode(false)
        notificationDismissedByUser = false

        if (::shutdownController.isInitialized) {
            shutdownController.shutdown(options)
        }
    }

    private fun publishStateAfterShutdown(snapshot: PlaybackSnapshot? = null) {
        if (::stateOrchestrator.isInitialized) {
            stateOrchestrator.publishStateAfterShutdown(
                snapshot = snapshot,
                notifyWidgets = false
            )
        }

        if (::widgetUpdateCoordinator.isInitialized) {
            val widgetSnapshot = snapshot?.toWidgetPlaybackSnapshot()
                ?: playbackRuntimeStateStore.state.value.toWidgetPlaybackSnapshot()

            widgetUpdateJob?.cancel()
            runBlocking {
                withContext(NonCancellable) {
                    widgetUpdateCoordinator.updateAll(widgetSnapshot)
                }
            }
        }
    }

    private fun handleNotificationFavoriteToggle() {
        toggleCurrentFavorite()
    }

    private fun toggleCurrentFavorite() {
        serviceScope.launch {
            ensurePlaybackRestored()

            if (::favoriteController.isInitialized) {
                favoriteController.toggleCurrent()
            }
        }
    }

    private fun setCurrentFavorite(isFavorite: Boolean) {
        serviceScope.launch {
            ensurePlaybackRestored()

            if (::favoriteController.isInitialized) {
                favoriteController.setCurrentFavorite(isFavorite)
            }
        }
    }

    private fun refreshArtworkAndSession(force: Boolean = false) {
        if (::artworkController.isInitialized) {
            artworkController.refresh(force = force)
            return
        }

        updateNotification(force = true)
    }

    private fun updateEditedTrackMetadata(
        music: Music
    ) {
        val changed = queueManager.updateTrackMetadata(music)
        if (!changed) return

        queueManager.save()
        notificationSessionBridge.updateQueue()
        refreshArtworkAndSession(force = true)
        persistSessionFromCurrentStateAsync()
        publishPlaybackState(
            reason = PublishReason.QueueChanged,
            forceNotification = true,
            forceWidgetUpdate = true
        )
    }

    private fun updateEditedTracksMetadata(
        music: List<Music>
    ) {
        val changed = queueManager.updateTracksMetadata(music)
        if (!changed) return

        queueManager.save()
        notificationSessionBridge.updateQueue()
        refreshArtworkAndSession(force = true)
        persistSessionFromCurrentStateAsync()
        publishPlaybackState(
            reason = PublishReason.QueueChanged,
            forceNotification = true,
            forceWidgetUpdate = true
        )
    }

    private fun publishAllRuntimeState(
        forceNotification: Boolean = false
    ) {
        if (::stateOrchestrator.isInitialized) {
            stateOrchestrator.publishAllRuntimeState(
                forceNotification = forceNotification
            )
        }

        updateWidgetsFromRuntimeState()
    }

    private fun publishPlaybackState(
        reason: PublishReason,
        forceNotification: Boolean = false,
        forceWidgetUpdate: Boolean = forceNotification
    ) {
        if (::stateOrchestrator.isInitialized) {
            stateOrchestrator.publishPlaybackState(
                reason = reason,
                forceNotification = forceNotification,
                forceWidgetUpdate = forceWidgetUpdate
            )
        }

        if (reason != PublishReason.ProgressTick) {
            updateWidgetsFromRuntimeState()
        }
    }

    private fun updateWidgetsFromRuntimeState() {
        if (!::widgetUpdateCoordinator.isInitialized || !::serviceScope.isInitialized) return

        val state = playbackRuntimeStateStore.state.value
        val snapshot = state.toWidgetPlaybackSnapshot()

        widgetUpdateJob?.cancel()
        widgetUpdateJob = serviceScope.launch {
            withContext(NonCancellable) {
                widgetUpdateCoordinator.updateAll(snapshot)
            }
        }
    }

    private fun gd.app.musicplayer.playback.queue.MusicPlaybackState.toWidgetPlaybackSnapshot(): WidgetPlaybackSnapshot {
        return WidgetPlaybackSnapshot(
            queue = queue,
            currentTrack = currentTrack,
            currentIndex = currentIndex,
            positionMs = positionMs,
            isPlaying = isPlaying,
            playMode = latestSettingPreferences.playMode
        )
    }

    private fun PlaybackSnapshot.toWidgetPlaybackSnapshot(): WidgetPlaybackSnapshot {
        return WidgetPlaybackSnapshot(
            queue = queue,
            currentTrack = currentTrack,
            currentIndex = currentIndex,
            positionMs = positionMs,
            isPlaying = false,
            playMode = latestSettingPreferences.playMode
        )
    }

    private fun updateNotification(force: Boolean = false) {
        if (::stateOrchestrator.isInitialized) {
            stateOrchestrator.updateNotification(force = force)
        }
    }

    private fun setQueueState(
        newQueue: List<Music>,
        requestedIndex: Int
    ) {
        val playableQueue = filterPlayableQueue(newQueue)

        if (playableQueue.isEmpty()) {
            queueManager.clear()
            resetTimedTransitionState()
            return
        }

        queueManager.setQueue(
            newQueue = playableQueue,
            requestedIndex = remapRequestedIndex(
                originalQueue = newQueue,
                playableQueue = playableQueue,
                requestedIndex = requestedIndex
            )
        )

        resetTimedTransitionState()
    }

    private fun clearQueueState() {
        queueManager.clear()

        resetTimedTransitionState()
    }

    private fun filterPlayableQueue(queue: List<Music>): List<Music> {
        return if (::playerQueueController.isInitialized) {
            playerQueueController.filterPlayable(queue)
        } else {
            queue
        }
    }

    private fun remapRequestedIndex(
        originalQueue: List<Music>,
        playableQueue: List<Music>,
        requestedIndex: Int
    ): Int {
        val requestedTrackId = originalQueue.getOrNull(requestedIndex)?.id

        val requestedPlayableIndex = requestedTrackId
            ?.let { trackId ->
                playableQueue.indexOfFirst { music -> music.id == trackId }
            }
            ?.takeIf { index -> index >= 0 }

        if (requestedPlayableIndex != null) return requestedPlayableIndex

        return requestedIndex.coerceIn(
            0,
            playableQueue.lastIndex
        )
    }

    private fun clearArtworkState() {
        if (::artworkController.isInitialized) {
            artworkController.clear()
        } else if (::artworkLoader.isInitialized) {
            artworkLoader.clear()
        }
    }

    private fun resetTimedTransitionState() {
        if (::timedTransitionController.isInitialized) {
            timedTransitionController.reset()
        }
    }

    private fun cancelTimedTransitionAndRestoreVolume() {
        if (::timedTransitionController.isInitialized) {
            timedTransitionController.cancelAndRestoreVolume()
        }
    }

    private fun isEffectivelyPlaying(): Boolean {
        return player.isPlaying ||
                (
                        player.playWhenReady &&
                                currentIndex in queue.indices &&
                                player.playbackState != Player.STATE_IDLE
                        )
    }

    private fun registerScreenOffReceiver() {
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

    private fun unregisterScreenOffReceiver() {
        if (!screenReceiverRegistered) return

        runCatching {
            unregisterReceiver(screenOffLockReceiver)
        }

        screenReceiverRegistered = false
    }

    private fun isNightMode(configuration: Configuration): Boolean {
        return (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun shouldPublishProgressState(): Boolean {
        return when (restoreManager.status) {
            RestoreStatus.NotStarted -> {
                queue.isNotEmpty() || playbackRuntimeStateStore.isInitialized()
            }

            RestoreStatus.Restoring -> false

            RestoreStatus.Restored,
            RestoreStatus.Empty,
            RestoreStatus.Failed -> true
        }
    }

    private fun maybePersistSessionFromProgressTick() {
        if (!::player.isInitialized || !player.isPlaying) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastSessionAutoSaveElapsedMs < SESSION_AUTO_SAVE_INTERVAL_MS) return

        lastSessionAutoSaveElapsedMs = now
        persistPlaybackSnapshotAsync(
            snapshot = capturePlaybackSnapshot(),
            persistQueue = false
        )
    }

    private fun capturePlaybackSnapshot(): PlaybackSnapshot {
        return snapshotManager.capture(
            player = if (::player.isInitialized) player else null,
            queueState = queueManager.state
        )
    }


    private fun syncCurrentIndexWithPlayer() {
        val restoreTrackId = pendingRestoreTrackId
        if (restoreTrackId != null) {
            val currentPlayerTrackId = currentPlayerMediaId()
            if (currentPlayerTrackId != restoreTrackId) {
                return
            }
            pendingRestoreTrackId = null
        }

        val resolvedIndex = snapshotManager.resolvePlayerIndex(
            player = if (::player.isInitialized) player else null,
            queue = queue
        ) ?: return

        if (resolvedIndex != currentIndex) {
            queueManager.updateCurrentIndex(resolvedIndex)
        }
    }

    private fun persistPlaybackSnapshotBlocking(
        snapshot: PlaybackSnapshot,
        persistQueue: Boolean
    ) {
        runBlocking {
            snapshotManager.persist(
                snapshot = snapshot,
                persistQueue = persistQueue
            )
        }
    }

    private fun persistPlaybackSnapshotAsync(
        snapshot: PlaybackSnapshot,
        persistQueue: Boolean
    ) {
        serviceScope.launch {
            withContext(NonCancellable) {
                snapshotManager.persist(
                    snapshot = snapshot,
                    persistQueue = persistQueue
                )
            }
        }
    }

    private fun persistSessionFromCurrentStateAsync() {
        persistPlaybackSnapshotAsync(
            snapshot = capturePlaybackSnapshot(),
            persistQueue = false
        )
    }

    private fun persistCurrentTrackProgressAsync(positionMs: Int) {
        val track = queueManager.currentTrack ?: return

        serviceScope.launch {
            snapshotManager.persistProgress(
                track = track,
                positionMs = positionMs.toLong().coerceAtLeast(0L),
                currentIndex = queueManager.currentIndex
            )
        }
    }

    private fun persistCurrentTrackProgressFromPlayerAsync() {
        val track = queueManager.currentTrack ?: return
        val positionMs = resolveCurrentSnapshotPositionMs()
        val currentIndex = queueManager.currentIndex

        serviceScope.launch {
            snapshotManager.persistProgress(
                track = track,
                positionMs = positionMs,
                currentIndex = currentIndex
            )
        }
    }

    private fun persistCurrentTrackProgressBlocking(
        track: Music?,
        positionMs: Long,
        currentIndex: Int
    ) {
        if (track == null) return

        runBlocking {
            snapshotManager.persistProgress(
                track = track,
                positionMs = positionMs,
                currentIndex = currentIndex
            )
        }
    }

    private fun persistSessionFromCurrentState() {
        persistPlaybackSnapshotBlocking(
            snapshot = capturePlaybackSnapshot(),
            persistQueue = false
        )
    }

    private fun currentPlayerMediaId(): Long? {
        if (!::player.isInitialized) return null

        return runCatching {
            player.currentMediaItem?.mediaId?.toLongOrNull()
        }.getOrNull()
    }

    private fun clearPersistedPlaybackState() {
        runBlocking {
            snapshotManager.clearProgress()
        }
    }

    private fun clearPersistedQueueBlocking() {
        runBlocking {
            withContext(dispatchers.io) {
                playbackQueueRepo.clearQueue()
            }
        }
    }

    private fun resolveCurrentSnapshotPositionMs(): Long {
        return snapshotManager.resolveCurrentPositionMs(
            player = if (::player.isInitialized) player else null,
            queueState = queueManager.state
        )
    }

    private fun resolveCurrentSnapshotDurationMs(track: Music?): Long {
        return snapshotManager.resolveCurrentDurationMs(
            player = if (::player.isInitialized) player else null,
            track = track
        )
    }

    private fun String.requiresImmediateForegroundPromotion(): Boolean {
        return this == ACTION_PLAY_FROM_QUEUE ||
                this == ACTION_PLAY ||
                this == ACTION_PLAY_NEXT ||
                this == ACTION_REPLACE_QUEUE ||
                this == ACTION_TOGGLE_PLAY_PAUSE ||
                this == ACTION_NEXT ||
                this == ACTION_PREVIOUS
    }

    companion object {
        const val ACTION_EXIT = "opraton_action_exit"
        const val ACTION_CHANGE_MODE = "opraton_action_change_mode"
        const val ACTION_MODE_RANDOM = "ACTION_MODE_RANDOM"
        const val ACTION_CHANGE_MUSIC_BY_INDEX = "music_action_change_music2"
        const val ACTION_DESK_LRC_LOCK = "ACTION_DESK_LRC_LOCK"

        const val ACTION_PLAY_FROM_QUEUE = "gd.app.musicplayer.action.PLAY_FROM_QUEUE"
        const val ACTION_ENQUEUE = "gd.app.musicplayer.action.ENQUEUE"
        const val ACTION_PLAY_NEXT = "gd.app.musicplayer.action.PLAY_NEXT"
        const val ACTION_REPLACE_QUEUE = "gd.app.musicplayer.action.REPLACE_QUEUE"
        const val ACTION_TOGGLE_PLAY_PAUSE = "gd.app.musicplayer.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_PLAY = "gd.app.musicplayer.action.PLAY"
        const val ACTION_PAUSE = "gd.app.musicplayer.action.PAUSE"
        const val ACTION_NEXT = "music_action_next"
        const val ACTION_PREVIOUS = "music_action_previous"
        const val ACTION_SEEK_TO = "gd.app.musicplayer.action.SEEK_TO"
        const val ACTION_CLEAR_QUEUE = "gd.app.musicplayer.action.CLEAR_QUEUE"
        const val ACTION_REMOVE_QUEUE_ITEM = "gd.app.musicplayer.action.REMOVE_QUEUE_ITEM"
        const val ACTION_MOVE_QUEUE_ITEM = "gd.app.musicplayer.action.MOVE_QUEUE_ITEM"
        const val ACTION_UPDATE_TRACK_METADATA = "gd.app.musicplayer.action.UPDATE_TRACK_METADATA"
        const val ACTION_UPDATE_TRACKS_METADATA = "gd.app.musicplayer.action.UPDATE_TRACKS_METADATA"
        const val ACTION_STOP = "music_action_stop"
        const val ACTION_QUIT = "gd.app.musicplayer.action.QUIT"
        const val ACTION_TOGGLE_FAVORITE = "gd.app.musicplayer.action.TOGGLE_FAVORITE"
        const val ACTION_SET_STOP_AFTER_CURRENT_TRACK =
            "gd.app.musicplayer.action.SET_STOP_AFTER_CURRENT_TRACK"
        const val ACTION_APPLY_AUDIO_EFFECTS = "gd.app.musicplayer.action.APPLY_AUDIO_EFFECTS"
        const val ACTION_APPLY_PLAYBACK_TUNING = "gd.app.musicplayer.action.APPLY_PLAYBACK_TUNING"
        const val ACTION_REFRESH_NOTIFICATION_STYLE =
            "gd.app.musicplayer.action.REFRESH_NOTIFICATION_STYLE"
        const val ACTION_RESTART_CURRENT = "gd.app.musicplayer.action.RESTART_CURRENT"
        const val ACTION_CUSTOM_FAVORITE = "gd.app.musicplayer.action.FAVORITE"
        const val ACTION_CUSTOM_UNFAVORITE = "gd.app.musicplayer.action.UNFAVORITE"
        const val ACTION_CUSTOM_STOP = "gd.app.musicplayer.action.STOP_CUSTOM"

        const val EXTRA_QUEUE_ITEMS = "queue_items"
        const val EXTRA_QUEUE_TOKEN = "queue_token"
        const val EXTRA_INDEX = "index"
        const val EXTRA_FROM_INDEX = "from_index"
        const val EXTRA_TO_INDEX = "to_index"
        const val EXTRA_TRACK = "track"
        const val EXTRA_ACTION_DATA = "music_action_data"
        const val EXTRA_SEEK_POSITION_MS = "seek_position_ms"
        const val EXTRA_STOP_AFTER_CURRENT_TRACK = "stop_after_current_track"

        @Volatile
        var isRunning: Boolean = false
            private set

        private const val NO_INDEX = -1
        private const val NO_TRACK_ID = Long.MIN_VALUE
        private const val NO_PLAYER_COMMAND = -1

        private const val PROGRESS_TICK_MS = 100L
        private const val SESSION_AUTO_SAVE_INTERVAL_MS = 5_000L
        private const val MEDIA3_SESSION_ACTIVITY_REQUEST_CODE = 2
        private const val MEDIA3_COMMAND_TOGGLE_FAVORITE =
            "gd.app.musicplayer.media3.TOGGLE_FAVORITE"
        private const val MEDIA3_COMMAND_CYCLE_PLAYBACK_MODE =
            "gd.app.musicplayer.media3.CYCLE_PLAYBACK_MODE"
        private const val MEDIA3_COMMAND_STOP_AFTER_CURRENT =
            "gd.app.musicplayer.media3.STOP_AFTER_CURRENT"
        private const val MEDIA3_COMMAND_CLOSE_NOTIFICATION =
            "gd.app.musicplayer.media3.CLOSE_NOTIFICATION"
        private const val PREVIOUS_RESTART_WINDOW_MS = 5_000L
        private const val PLAY_PAUSE_FADE_DURATION_MS = 1_000L

        fun startAction(
            context: Context,
            action: String
        ): Boolean {
            return startAction(
                context = context,
                action = action,
                actionData = null
            )
        }

        fun startAction(
            context: Context,
            action: String,
            actionData: Parcelable?
        ): Boolean {
            return runCatching {
                val intent = Intent(
                    context.applicationContext,
                    MusicPlaybackService::class.java
                ).apply {
                    this.action = action

                    actionData?.let { data ->
                        putExtra(
                            EXTRA_ACTION_DATA,
                            data
                        )
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.applicationContext.startForegroundService(intent)
                } else {
                    context.applicationContext.startService(intent)
                }

                true
            }.getOrDefault(false)
        }

        fun startActionWithIndex(
            context: Context,
            action: String,
            index: Int
        ): Boolean {
            return runCatching {
                val intent = Intent(
                    context.applicationContext,
                    MusicPlaybackService::class.java
                ).apply {
                    this.action = action
                    putExtra(EXTRA_ACTION_DATA, IndexActionData(index))
                    putExtra(EXTRA_INDEX, index)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.applicationContext.startForegroundService(intent)
                } else {
                    context.applicationContext.startService(intent)
                }

                true
            }.getOrDefault(false)
        }
    }
}
