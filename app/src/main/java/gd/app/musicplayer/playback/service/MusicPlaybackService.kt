package gd.app.musicplayer.playback.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.session.MediaSession
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.dispatcher.AppDispatchers
import gd.app.musicplayer.data.local.db.dao.MusicDao
import gd.app.musicplayer.data.local.preference.DesktopLyricPreference
import gd.app.musicplayer.data.local.preference.DesktopLyricPreferenceStore
import gd.app.musicplayer.data.local.preference.PlaybackStatePreferenceStore
import gd.app.musicplayer.data.local.preference.SettingPreferences
import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import gd.app.musicplayer.data.local.preference.SoundEffectPreferences
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.model.PlaybackSession
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import gd.app.musicplayer.domain.usecase.library.ObserveAlbumPictureUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import gd.app.musicplayer.domain.usecase.playlist.ToggleFavoriteTrackUseCase
import gd.app.musicplayer.playback.ArtworkLoader
import gd.app.musicplayer.playback.AudioEffectsManager
import gd.app.musicplayer.playback.AudioFocusController
import gd.app.musicplayer.playback.HeadsetMediaButtonHandler
import gd.app.musicplayer.playback.IndexActionData
import gd.app.musicplayer.playback.PlaybackMediaSessionController
import gd.app.musicplayer.playback.PlaybackModeResolver
import gd.app.musicplayer.playback.PlaybackNotificationController
import gd.app.musicplayer.playback.PlaybackQueuePersistence
import gd.app.musicplayer.playback.PlaybackRuntimeStateStore
import gd.app.musicplayer.playback.PlaybackServiceCommandHandler
import gd.app.musicplayer.playback.PlaybackSessionStore
import gd.app.musicplayer.playback.PlaybackStatePublisher
import gd.app.musicplayer.playback.PlaybackStatisticsRecorder
import gd.app.musicplayer.playback.PlaybackTuningController
import gd.app.musicplayer.playback.ScreenOffLockReceiver
import gd.app.musicplayer.playback.StereoBalanceAudioProcessor
import gd.app.musicplayer.playback.SleepTimerManager
import gd.app.musicplayer.playback.VolumeFader
import gd.app.musicplayer.ui.shell.MainActivity
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MusicPlaybackService : MediaSessionService() {

    @Inject
    lateinit var playbackRuntimeStateStore: PlaybackRuntimeStateStore

    @Inject
    lateinit var playbackSessionStore: PlaybackSessionStore

    @Inject
    lateinit var playbackQueueRepo: PlaybackQueueRepo

    @Inject
    lateinit var desktopLyricPreferenceStore: DesktopLyricPreferenceStore

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
    lateinit var statisticsRecorder: PlaybackStatisticsRecorder

    private lateinit var player: ExoPlayer
    private lateinit var serviceScope: CoroutineScope
    private lateinit var defaultArtwork: Bitmap

    private lateinit var audioFocusController: AudioFocusController
    private lateinit var artworkLoader: ArtworkLoader
    private lateinit var commandHandler: PlaybackServiceCommandHandler
    private lateinit var mediaSessionController: PlaybackMediaSessionController
    private lateinit var notificationController: PlaybackNotificationController
    private lateinit var playbackModeResolver: PlaybackModeResolver
    private lateinit var playbackTuningController: PlaybackTuningController
    private lateinit var queuePersistence: PlaybackQueuePersistence
    private lateinit var screenOffLockReceiver: ScreenOffLockReceiver
    private lateinit var statePublisher: PlaybackStatePublisher
    private lateinit var stereoBalanceAudioProcessor: StereoBalanceAudioProcessor
    private lateinit var volumeFader: VolumeFader

    private val compatMediaSession: MediaSession
        get() = mediaSessionController.session

    private var media3Session: androidx.media3.session.MediaSession? = null

    private val progressHandler = Handler(Looper.getMainLooper())
    private val restoreMutex = Mutex()

    @Volatile
    private var restoreStatus: RestoreStatus = RestoreStatus.NotStarted

    private var resumeJob: Job? = null
    private var currentTrackArtworkObserverJob: Job? = null
    private var screenReceiverRegistered = false

    private var queue: List<Music> = emptyList()
    private var currentIndex = NO_INDEX

    private var currentArtwork: Bitmap? = null
    private var currentArtworkTrackId = NO_TRACK_ID

    private var timedTransitionTrackId = NO_TRACK_ID
    private var timedTransitionStartedAtMs = 0L

    private var keepIdleNotification = false
    private var stopAfterCurrentTrack = false
    private var notificationDismissedByUser = false
    private var isNightMode = false

    @Volatile
    private var latestSettingPreferences = SettingPreferences()

    @Volatile
    private var latestDesktopLyricPreference = DesktopLyricPreference()

    private enum class RestoreStatus {
        NotStarted,
        Restoring,
        Restored,
        Empty,
        Failed
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            maybeHandleTimedTransition()
            if (shouldPublishProgressState()) {
                statePublisher.publish()
            }
            progressHandler.postDelayed(this, PROGRESS_TICK_MS)
        }
    }

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

        notificationController.createNotificationChannel()

        restoreLastSessionIntoRuntimeStateIfNeeded()
        registerScreenOffReceiver()

        progressHandler.post(progressTicker)
    }

    override fun onGetSession(
        controllerInfo: androidx.media3.session.MediaSession.ControllerInfo
    ): androidx.media3.session.MediaSession? {
        return media3Session
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
    }

    override fun onDestroy() {
        progressHandler.removeCallbacksAndMessages(null)

        resumeJob?.cancel()
        resumeJob = null
        currentTrackArtworkObserverJob?.cancel()
        currentTrackArtworkObserverJob = null

        if (::volumeFader.isInitialized) {
            volumeFader.cancel()
        }

        if (::artworkLoader.isInitialized) {
            artworkLoader.clear()
        }

        media3Session?.release()
        media3Session = null

        if (::mediaSessionController.isInitialized) {
            mediaSessionController.release()
        }

        if (::audioEffectsManager.isInitialized) {
            audioEffectsManager.release()
        }

        if (::player.isInitialized) {
            player.release()
        }

        if (::audioFocusController.isInitialized) {
            audioFocusController.abandon()
        }

        unregisterScreenOffReceiver()

        if (::serviceScope.isInitialized) {
            serviceScope.cancel()
        }

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

        updateNotification()
        statePublisher.publish()

        return START_STICKY
    }

    private fun handleAppTaskRemoved() {
        if (isEffectivelyPlaying()) {
            updateNotification(force = true)
            statePublisher.publish()
            return
        }

        shutdownPlayback(
            clearQueue = false,
            clearPersistedQueue = false,
            clearRuntimeState = false,
            removeNotification = true,
            stopService = true
        )
    }

    private fun observePreferences() {
        observeSettingPreferences()
        observeDesktopLyricPreference()
        observeAudioEffectPreferences()
    }

    private fun observeCurrentTrackArtwork() {
        currentTrackArtworkObserverJob?.cancel()
        currentTrackArtworkObserverJob = serviceScope.launch {
            playbackRuntimeStateStore.state
                .map { state -> state.currentTrack?.id }
                .distinctUntilChanged()
                .flatMapLatest { musicId: Long? ->
                    if (musicId == null) {
                        emptyFlow<Pair<Long, String?>>()
                    } else {
                        observeAlbumPictureUseCase(musicId)
                            .distinctUntilChanged()
                            .map { artworkPath: String? -> musicId to artworkPath }
                    }
                }
                .collect { (musicId, artworkPath) ->
                    syncCurrentTrackArtwork(
                        trackId = musicId,
                        artworkPath = artworkPath
                    )
                }
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

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun configurePlayer() {
        stereoBalanceAudioProcessor = StereoBalanceAudioProcessor()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(
                        arrayOf<AudioProcessor>(stereoBalanceAudioProcessor)
                    )
                    .build()
            }
        }

        player = ExoPlayer.Builder(this, renderersFactory)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false
            )
            .build()

        player.addListener(
            object : Player.Listener {

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            applyAudioEffectsFromPreferences()
                        }

                        Player.STATE_ENDED -> {
                            handleTrackEnded()
                            return
                        }

                        Player.STATE_BUFFERING,
                        Player.STATE_IDLE -> Unit
                    }

                    publishAllRuntimeState()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        applyAudioEffectsFromPreferences()

                        statisticsRecorder.recordStartIfNeeded(
                            queue.getOrNull(currentIndex)
                        )
                    }

                    publishAllRuntimeState(forceNotification = true)
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int
                ) {
                    val playerIndex = player.currentMediaItemIndex

                    if (playerIndex in queue.indices) {
                        currentIndex = playerIndex
                    }

                    resetTimedTransitionState()
                    playbackTuningController.applyResolvedPlayerVolume()
                    refreshArtworkAndSession(force = true)

                    if (player.isPlaying) {
                        statisticsRecorder.recordStartIfNeeded(
                            music = queue.getOrNull(currentIndex),
                            force = true
                        )
                    }

                    publishAllRuntimeState(forceNotification = true)
                }

                override fun onPlayerError(error: PlaybackException) {
                    playNextInternal()
                }
            }
        )
    }

    private fun configureControllers() {
        playbackModeResolver = PlaybackModeResolver(
            settingsPreferenceOps = settingPreferencesDataStore,
            applicationScope = serviceScope
        )

        playbackTuningController = PlaybackTuningController(
            player = player,
            playbackStatePreferenceStore = playbackStatePreferenceStore,
            settingPreferencesDataStore = settingPreferencesDataStore,
            soundEffectPreferences = soundEffectPreferences,
            stereoBalanceAudioProcessor = stereoBalanceAudioProcessor,
            currentMusicProvider = {
                queue.getOrNull(currentIndex)
            },
            applicationScope = serviceScope
        )

        volumeFader = VolumeFader(
            player = player,
            handler = progressHandler
        )

        queuePersistence = PlaybackQueuePersistence(
            playbackQueueRepo,
            scope = serviceScope
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

        mediaSessionController = PlaybackMediaSessionController(
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
            callbacks = object : PlaybackMediaSessionController.Callbacks {
                override fun play() = resumePlayback()
                override fun pause() = pausePlayback()
                override fun next() = playNext()
                override fun previous() = playPrevious()
                override fun seekTo(positionMs: Int) = this@MusicPlaybackService.seekTo(positionMs)
                override fun toggleFavorite() = handleNotificationFavoriteToggle()
                override fun quit() = pauseAndPersistForNotificationClose()
                override fun stop() = stopPlaybackWithoutClearingQueue()
            }
        )

        mediaSessionController.updateQueue()
        mediaSessionController.updatePlaybackState()

        media3Session = buildMedia3Session()

        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        notificationController = PlaybackNotificationController(
            service = this,
            notificationManager = notificationManager,
            mediaSessionTokenProvider = {
                compatMediaSession.sessionToken
            },
            currentMusicProvider = {
                queue.getOrNull(currentIndex)
            },
            currentArtworkProvider = {
                currentArtwork
            },
            artworkTrackIdProvider = {
                currentArtworkTrackId
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

        commandHandler = PlaybackServiceCommandHandler(
            service = this,
            callbacks = object : PlaybackServiceCommandHandler.Callbacks {

                override fun refreshNotificationStyle() {
                    notificationController.refreshStyle { delayMs, block ->
                        progressHandler.postDelayed(
                            block,
                            delayMs
                        )
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

                override fun toggleFavorite(music: Music) {
                    this@MusicPlaybackService.toggleFavorite(music)
                }

                override fun setFavorite(
                    music: Music,
                    favorited: Boolean
                ) {
                    this@MusicPlaybackService.setFavorite(
                        music = music,
                        favorited = favorited
                    )
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

                override fun seekTo(positionMs: Int) {
                    this@MusicPlaybackService.seekTo(positionMs)
                }

                override fun setStopAfterCurrentTrack(enabled: Boolean) {
                    stopAfterCurrentTrack = enabled
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
    }

    private fun buildMedia3Session(): androidx.media3.session.MediaSession {
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

        return androidx.media3.session.MediaSession.Builder(
            this,
            player
        )
            .setSessionActivity(sessionActivity)
            .build()
    }

    private fun restoreLastSessionIntoRuntimeStateIfNeeded() {
        serviceScope.launch {
            ensurePlaybackRestored()
        }
    }

    private suspend fun ensurePlaybackRestored() {
        if (isRestoreFinished()) return

        restoreMutex.withLock {
            if (isRestoreFinished()) return

            restoreStatus = RestoreStatus.Restoring

            runCatching {
                restorePlaybackStateInternal()
            }.onSuccess { restored ->
                restoreStatus = if (restored) {
                    RestoreStatus.Restored
                } else {
                    RestoreStatus.Empty
                }
            }.onFailure {
                restoreStatus = RestoreStatus.Failed
                playbackRuntimeStateStore.initializeIfNeeded()
                statePublisher.publish()
            }
        }
    }

    private fun isRestoreFinished(): Boolean {
        return restoreStatus == RestoreStatus.Restored ||
                restoreStatus == RestoreStatus.Empty ||
                restoreStatus == RestoreStatus.Failed
    }

    private suspend fun restorePlaybackStateInternal(): Boolean {
        val restoredQueue = withContext(dispatchers.io) {
            playbackQueueRepo.getQueue()
        }

        if (restoreStatus != RestoreStatus.Restoring) {
            return queue.isNotEmpty()
        }

        playbackRuntimeStateStore.initializeIfNeeded()

        if (restoredQueue.isEmpty()) {
            clearQueueState()
            syncControllersAfterQueueRestore(forceArtwork = false)
            statePublisher.publish()
            return false
        }

        val restoredSession = withContext(dispatchers.io) {
            playbackSessionStore.getLastSession()
        }

        val safeIndex = restoredSession?.let { session ->
            resolveStartIndex(
                queue = restoredQueue,
                session = session
            )
        } ?: 0

        val safePosition = restoredSession
            ?.positionMs
            ?.coerceAtLeast(0L)
            ?: 0L

        if (restoreStatus != RestoreStatus.Restoring) {
            return queue.isNotEmpty()
        }

        setQueueState(
            newQueue = restoredQueue,
            requestedIndex = safeIndex
        )

        prepareRestoredPlayerState(
            index = safeIndex,
            positionMs = safePosition
        )

        syncControllersAfterQueueRestore(forceArtwork = true)

        statePublisher.publishRestored(
            safeIndex,
            safePosition,
            restoredQueue
        )

        return true
    }

    private fun syncControllersAfterQueueRestore(forceArtwork: Boolean) {
        mediaSessionController.updateQueue()
        refreshArtworkAndSession(force = forceArtwork)
        mediaSessionController.updatePlaybackState()
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

        playbackTuningController.applyResolvedPlayerVolume()
    }

    private fun handleTrackEnded() {
        resetTimedTransitionState()

        statisticsRecorder.recordCompletion(
            queue.getOrNull(currentIndex)
        )

        if (stopAfterCurrentTrack) {
            stopAfterCurrentTrack = false
            SleepTimerManager.finishPendingTrackEnd()
            stopAndClearQueue()
            return
        }

        playNextInternal(fromAutoTransition = true)
    }

    private fun resumeWithDefaultQueue() {
        serviceScope.launch {
            val tracks = withContext(dispatchers.io) {
                runCatching {
                    observeTracksUseCase(MusicSet.Tracks).first()
                }.getOrDefault(emptyList())
            }

            if (tracks.isEmpty()) {
                playbackRuntimeStateStore.initializeIfNeeded()
                statePublisher.publish()
                return@launch
            }

            withContext(dispatchers.io) {
                playbackQueueRepo.replaceQueue(tracks)
                playbackSessionStore.saveSession(
                    musicId = tracks.first().id,
                    positionMs = 0L,
                    currentIndex = 0
                )
            }

            setQueueState(
                newQueue = tracks,
                requestedIndex = 0
            )

            restoreStatus = RestoreStatus.Restored

            mediaSessionController.updateQueue()

            if (!audioFocusController.request()) {
                return@launch
            }

            setPlayerQueue(
                queue = queue,
                startIndex = 0,
                startPositionMs = 0L,
                playWhenReady = true
            )

            refreshArtworkAndSession(force = true)
            publishAllRuntimeState(forceNotification = true)
        }
    }

    private fun resolveStartIndex(
        queue: List<Music>,
        session: PlaybackSession
    ): Int {
        if (queue.isEmpty()) return 0

        val indexByMusicId = queue.indexOfFirst { music ->
            music.id == session.musicId
        }

        return if (indexByMusicId >= 0) {
            indexByMusicId
        } else {
            session.currentIndex.coerceIn(
                0,
                queue.lastIndex
            )
        }
    }

    private fun handlePlayFromQueue(
        incomingQueue: List<Music>,
        incomingIndex: Int
    ) {
        if (incomingQueue.isEmpty()) return

        restoreStatus = RestoreStatus.Restored

        setQueueState(
            newQueue = incomingQueue,
            requestedIndex = incomingIndex
        )

        queuePersistence.save(queue)
        mediaSessionController.updateQueue()

        if (!audioFocusController.request()) {
            return
        }

        setPlayerQueue(
            queue = queue,
            startIndex = currentIndex,
            startPositionMs = 0L,
            playWhenReady = true
        )

        refreshArtworkAndSession(force = true)
        publishAllRuntimeState(forceNotification = true)
    }

    private fun handleEnqueue(incomingQueue: List<Music>) {
        if (incomingQueue.isEmpty()) return

        val wasEmpty = queue.isEmpty()
        val nextQueue = queue + incomingQueue
        val nextIndex = if (wasEmpty) 0 else currentIndex

        restoreStatus = RestoreStatus.Restored

        setQueueState(
            newQueue = nextQueue,
            requestedIndex = nextIndex
        )

        queuePersistence.save(queue)
        mediaSessionController.updateQueue()

        if (wasEmpty || !isPlayerPlaylistSynced()) {
            setPlayerQueue(
                queue = queue,
                startIndex = currentIndex.coerceAtLeast(0),
                startPositionMs = player.currentPosition.coerceAtLeast(0L),
                playWhenReady = isEffectivelyPlaying()
            )
        } else {
            player.addMediaItems(
                incomingQueue.mapNotNull { music ->
                    music.toMediaItemOrNull()
                }
            )
        }

        publishAllRuntimeState()
    }

    private fun handlePlayNextQueue(incomingQueue: List<Music>) {
        if (incomingQueue.isEmpty()) return

        restoreStatus = RestoreStatus.Restored

        if (queue.isEmpty()) {
            setQueueState(
                newQueue = incomingQueue,
                requestedIndex = 0
            )

            queuePersistence.save(queue)
            mediaSessionController.updateQueue()

            if (!audioFocusController.request()) {
                return
            }

            setPlayerQueue(
                queue = queue,
                startIndex = 0,
                startPositionMs = 0L,
                playWhenReady = true
            )

            refreshArtworkAndSession(force = true)
            publishAllRuntimeState(forceNotification = true)

            return
        }

        val insertIndex = if (currentIndex == queue.lastIndex) {
            queue.size
        } else {
            currentIndex + 1
        }

        val nextQueue = queue
            .toMutableList()
            .apply {
                addAll(
                    index = insertIndex,
                    elements = incomingQueue
                )
            }
            .toList()

        setQueueState(
            newQueue = nextQueue,
            requestedIndex = currentIndex
        )

        queuePersistence.save(queue)
        mediaSessionController.updateQueue()

        if (isPlayerPlaylistSynced()) {
            player.addMediaItems(
                insertIndex,
                incomingQueue.mapNotNull { music ->
                    music.toMediaItemOrNull()
                }
            )
        } else {
            setPlayerQueue(
                queue = queue,
                startIndex = currentIndex,
                startPositionMs = player.currentPosition.coerceAtLeast(0L),
                playWhenReady = isEffectivelyPlaying()
            )
        }

        publishAllRuntimeState()
    }

    private fun replaceQueue(
        newQueue: List<Music>,
        requestedIndex: Int
    ) {
        if (newQueue.isEmpty()) {
            stopPlayback()
            return
        }

        restoreStatus = RestoreStatus.Restored

        val previousQueue = queue
        val previousTrackId = queue.getOrNull(currentIndex)?.id
        val previousPositionMs = player.currentPosition.coerceAtLeast(0L)
        val wasPlaying = isEffectivelyPlaying()
        val wasPlayerQueueSynced = isPlayerPlaylistSynced()

        val preservedIndex = previousTrackId?.let { trackId ->
            newQueue
                .indexOfFirst { music ->
                    music.id == trackId
                }
                .takeIf { index ->
                    index >= 0
                }
        }

        val targetIndex = (preservedIndex ?: requestedIndex).coerceIn(
            0,
            newQueue.lastIndex
        )

        setQueueState(
            newQueue = newQueue,
            requestedIndex = targetIndex
        )

        queuePersistence.save(queue)
        mediaSessionController.updateQueue()

        val reorderedInPlace = if (
            wasPlayerQueueSynced &&
            player.playbackState != Player.STATE_IDLE &&
            haveSameQueueContents(previousQueue, newQueue)
        ) {
            applyInPlaceQueueReorder(previousQueue, newQueue)
        } else {
            false
        }

        if (!reorderedInPlace) {
            val shouldPreservePosition =
                previousTrackId != null &&
                        queue.getOrNull(currentIndex)?.id == previousTrackId &&
                        player.playbackState != Player.STATE_IDLE

            setPlayerQueue(
                queue = queue,
                startIndex = currentIndex,
                startPositionMs = if (shouldPreservePosition) previousPositionMs else 0L,
                playWhenReady = wasPlaying
            )
        }

        refreshArtworkAndSession(force = true)
        publishAllRuntimeState(forceNotification = true)
    }

    private fun haveSameQueueContents(
        previousQueue: List<Music>,
        newQueue: List<Music>
    ): Boolean {
        if (previousQueue.size != newQueue.size) return false

        val counts = HashMap<Long, Int>(previousQueue.size)
        previousQueue.forEach { music ->
            counts[music.id] = (counts[music.id] ?: 0) + 1
        }
        newQueue.forEach { music ->
            val count = counts[music.id] ?: return false
            if (count == 1) {
                counts.remove(music.id)
            } else {
                counts[music.id] = count - 1
            }
        }
        return counts.isEmpty()
    }

    private fun applyInPlaceQueueReorder(
        previousQueue: List<Music>,
        newQueue: List<Music>
    ): Boolean {
        val currentIds = previousQueue.map { it.id }.toMutableList()
        val targetIds = newQueue.map { it.id }

        for (targetIndex in targetIds.indices) {
            val targetId = targetIds[targetIndex]
            if (currentIds[targetIndex] == targetId) continue

            val fromIndex = ((targetIndex + 1) until currentIds.size)
                .firstOrNull { index -> currentIds[index] == targetId }
                ?: return false

            player.moveMediaItem(fromIndex, targetIndex)
            val movedId = currentIds.removeAt(fromIndex)
            currentIds.add(targetIndex, movedId)
        }

        return true
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
        if (index !in queue.indices) return

        if (playWhenReady && !audioFocusController.request()) {
            return
        }

        if (!isPlayerPlaylistSynced()) {
            setPlayerQueue(
                queue = queue,
                startIndex = index,
                startPositionMs = 0L,
                playWhenReady = playWhenReady
            )
        } else {
            player.seekTo(
                index,
                0L
            )
            player.playWhenReady = playWhenReady

            if (playWhenReady) {
                player.play()
            }
        }

        currentIndex = index

        statisticsRecorder.reset()
        resetTimedTransitionState()
        applyVolumeForPlaybackStart(playWhenReady)

        refreshArtworkAndSession(force = true)
        publishAllRuntimeState(forceNotification = true)
    }

    private fun setPlayerQueue(
        queue: List<Music>,
        startIndex: Int,
        startPositionMs: Long = 0L,
        playWhenReady: Boolean
    ) {
        if (queue.isEmpty()) return

        val mediaItems = queue.mapNotNull { music ->
            music.toMediaItemOrNull()
        }

        if (mediaItems.isEmpty()) return

        val safeIndex = startIndex.coerceIn(
            0,
            mediaItems.lastIndex
        )

        player.setMediaItems(
            mediaItems,
            safeIndex,
            startPositionMs.coerceAtLeast(0L)
        )

        player.prepare()
        player.playWhenReady = playWhenReady
    }

    private fun isPlayerPlaylistSynced(): Boolean {
        if (!::player.isInitialized) return false
        if (player.mediaItemCount != queue.size) return false

        for (index in queue.indices) {
            val playerMediaId = player.getMediaItemAt(index).mediaId
            val queueMediaId = queue[index].id.toString()

            if (playerMediaId != queueMediaId) {
                return false
            }
        }

        return true
    }

    private fun applyVolumeForPlaybackStart(playWhenReady: Boolean) {
        if (
            playWhenReady &&
            playbackTuningController.isPlayPauseFadeEnabled()
        ) {
            volumeFader.fadePercent(
                volumeProvider = playbackTuningController::resolveTargetPlaybackVolume,
                fromPercent = 0f,
                toPercent = 1f,
                durationMs = PLAY_PAUSE_FADE_DURATION_MS
            )
        } else {
            playbackTuningController.applyResolvedPlayerVolume()
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
            resumeWithDefaultQueue()
            return
        }

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
            player.play()
            playbackTuningController.applyPlaybackTuning()
            applyAudioEffectsFromPreferences()

            if (playbackTuningController.isPlayPauseFadeEnabled()) {
                volumeFader.fadePercent(
                    volumeProvider = playbackTuningController::resolveTargetPlaybackVolume,
                    fromPercent = 0f,
                    toPercent = 1f,
                    durationMs = PLAY_PAUSE_FADE_DURATION_MS
                )
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

        if (withFade && player.isPlaying) {
            volumeFader.fadePercent(
                volumeProvider = playbackTuningController::resolveTargetPlaybackVolume,
                fromPercent = resolvePauseFadeStartPercent(),
                toPercent = 0f,
                durationMs = PLAY_PAUSE_FADE_DURATION_MS
            ) {
                player.pause()
                playbackTuningController.applyResolvedPlayerVolume()
                publishAllRuntimeState(forceNotification = true)
            }
            return
        }

        player.pause()
        publishAllRuntimeState(forceNotification = true)
    }

    private fun restartCurrentTrack() {
        serviceScope.launch {
            ensurePlaybackRestored()

            if (currentIndex !in queue.indices) return@launch

            resetTimedTransitionState()
            player.seekTo(0L)

            if (!isEffectivelyPlaying()) {
                resumePlaybackInternal()
            } else {
                publishAllRuntimeState()
            }
        }
    }

    private fun playNext(fromAutoTransition: Boolean = false) {
        serviceScope.launch {
            ensurePlaybackRestored()
            playNextInternal(fromAutoTransition)
        }
    }

    private fun playNextInternal(fromAutoTransition: Boolean = false) {
        if (queue.isEmpty()) return

        resetTimedTransitionState()

        val nextIndex = playbackModeResolver.resolveNextIndex(
            queueSize = queue.size,
            currentIndex = currentIndex
        ) ?: run {
            if (fromAutoTransition) {
                stopAndClearQueue()
            }
            return
        }

        if (!audioFocusController.request()) return

        currentIndex = nextIndex

        if (!isPlayerPlaylistSynced()) {
            setPlayerQueue(
                queue = queue,
                startIndex = nextIndex,
                startPositionMs = 0L,
                playWhenReady = true
            )
        } else {
            player.seekTo(
                nextIndex,
                0L
            )
            player.playWhenReady = true
            player.play()
        }

        statisticsRecorder.reset()
        applyVolumeForPlaybackStart(playWhenReady = true)
        refreshArtworkAndSession(force = true)
        publishAllRuntimeState(forceNotification = true)
    }

    private fun playPrevious() {
        serviceScope.launch {
            ensurePlaybackRestored()
            playPreviousInternal()
        }
    }

    private fun playPreviousInternal() {
        if (queue.isEmpty()) return

        if (player.currentPosition > PREVIOUS_RESTART_WINDOW_MS) {
            seekToInternal(0)
            return
        }

        val previousIndex = playbackModeResolver.resolvePreviousIndex(
            queueSize = queue.size,
            currentIndex = currentIndex,
            shouldRestartCurrent = false
        ) ?: return

        if (!audioFocusController.request()) return

        currentIndex = previousIndex

        if (!isPlayerPlaylistSynced()) {
            setPlayerQueue(
                queue = queue,
                startIndex = previousIndex,
                startPositionMs = 0L,
                playWhenReady = true
            )
        } else {
            player.seekTo(
                previousIndex,
                0L
            )
            player.playWhenReady = true
            player.play()
        }

        statisticsRecorder.reset()
        applyVolumeForPlaybackStart(playWhenReady = true)
        refreshArtworkAndSession(force = true)
        publishAllRuntimeState(forceNotification = true)
    }

    private fun seekTo(positionMs: Int) {
        serviceScope.launch {
            ensurePlaybackRestored()
            seekToInternal(positionMs)
        }
    }

    private fun seekToInternal(positionMs: Int) {
        if (currentIndex !in queue.indices) return

        val fallbackDurationMs = queue.getOrNull(currentIndex)?.duration ?: 0

        val durationMs = player.duration
            .takeIf { duration ->
                duration != C.TIME_UNSET && duration > 0L
            }
            ?.toInt()
            ?: fallbackDurationMs

        val target = positionMs.coerceIn(
            0,
            durationMs
        )

        player.seekTo(target.toLong())
        publishAllRuntimeState()
    }

    private fun applyAudioEffectsFromPreferences() {
        serviceScope.launch {
            audioEffectsManager.applyFromPreferences(player)
            playbackTuningController.applyResolvedPlayerVolumeOnMain()
        }
    }

    private fun stopPlayback() {
        keepIdleNotification = false
        stopAfterCurrentTrack = false
        notificationDismissedByUser = false

        statisticsRecorder.reset()
        resetTimedTransitionState()

        if (::volumeFader.isInitialized) {
            volumeFader.cancel()
        }

        persistSessionFromCurrentState()

        if (::player.isInitialized) {
            player.pause()
            player.stop()
        }

        if (::audioFocusController.isInitialized) {
            audioFocusController.abandon()
        }

        publishStateAfterShutdown()
        updateNotification(force = true)
    }

    private fun stopAndClearQueue() {
        notificationDismissedByUser = false
        shutdownPlayback(
            clearQueue = true,
            clearPersistedQueue = false,
            clearRuntimeState = true,
            removeNotification = true,
            stopService = true
        )
    }

    private fun stopPlaybackWithoutClearingQueue() {
        notificationDismissedByUser = false
        shutdownPlayback(
            clearQueue = false,
            clearPersistedQueue = false,
            clearRuntimeState = false,
            removeNotification = true,
            stopService = true
        )
    }

    private fun pauseAndPersistForNotificationClose() {
        val snapshotTrack = queue.getOrNull(currentIndex)
        val snapshotPositionMs = resolveCurrentSnapshotPositionMs()
        val snapshotDurationMs = resolveCurrentSnapshotDurationMs(snapshotTrack)
        val snapshotAudioSessionId = runCatching {
            player.audioSessionId
        }.getOrDefault(playbackRuntimeStateStore.state.value.audioSessionId)
        notificationDismissedByUser = true

        if (player.isPlaying || player.playWhenReady) {
            player.pause()
        }

        runBlocking {
            withContext(dispatchers.io) {
                persistSessionSnapshot(
                    track = snapshotTrack,
                    index = currentIndex,
                    positionMs = snapshotPositionMs
                )
            }
        }
        mediaSessionController.updatePlaybackState()
        statePublisher.publishSnapshot(
            currentIndex = currentIndex,
            currentTrack = snapshotTrack,
            isPlaying = false,
            positionMs = snapshotPositionMs,
            durationMs = snapshotDurationMs,
            audioSessionId = snapshotAudioSessionId
        )

        notificationController.stopForegroundAndRemove()
    }

    private fun exitService() {
        shutdownPlayback(
            clearQueue = false,
            clearPersistedQueue = false,
            clearRuntimeState = false,
            removeNotification = true,
            stopService = true
        )
    }

    private fun clearQueueKeepingNotification() {
        keepIdleNotification = true
        notificationDismissedByUser = false

        shutdownPlayback(
            clearQueue = true,
            clearPersistedQueue = true,
            clearRuntimeState = true,
            removeNotification = false,
            stopService = false
        )

        notificationController.stopForegroundDetached()
        notificationController.update(force = true)
    }

    private fun shutdownPlayback(
        clearQueue: Boolean,
        clearPersistedQueue: Boolean,
        clearRuntimeState: Boolean,
        removeNotification: Boolean,
        stopService: Boolean
    ) {
        persistSessionFromCurrentState()

        keepIdleNotification = false
        stopAfterCurrentTrack = false
        notificationDismissedByUser = false

        statisticsRecorder.reset()
        resetTimedTransitionState()

        if (::volumeFader.isInitialized) {
            volumeFader.cancel()
        }

        if (::player.isInitialized) {
            player.pause()
            player.stop()
            player.clearMediaItems()
        }

        if (::audioFocusController.isInitialized) {
            audioFocusController.abandon()
        }

        if (clearQueue) {
            clearArtworkState()
            clearQueueState()
            if (clearPersistedQueue) {
                queuePersistence.clear()
            }

            if (::mediaSessionController.isInitialized) {
                mediaSessionController.clearMetadata()
                mediaSessionController.clearQueue()
            }

            restoreStatus = RestoreStatus.Empty
        }

        if (clearRuntimeState) {
            statePublisher.reset()
        } else {
            publishStateAfterShutdown()
        }

        if (removeNotification && ::notificationController.isInitialized) {
            notificationController.stopForegroundAndRemove()
        }

        if (stopService) {
            isRunning = false
            stopSelf()
        }
    }

    private fun publishStateAfterShutdown() {
        mediaSessionController.updatePlaybackState()
        statePublisher.publish()
    }

    private fun handleNotificationFavoriteToggle() {
        queue.getOrNull(currentIndex)?.let(::toggleFavorite)
    }

    private fun setFavorite(
        music: Music,
        favorited: Boolean
    ) {
        val isCurrentlyFavorited = music.playlistId == MusicSet.FAVORITES
        if (isCurrentlyFavorited == favorited) return

        toggleFavorite(music)
    }

    private fun toggleFavorite(music: Music) {
        serviceScope.launch {
            val favorited = withContext(dispatchers.io) {
                toggleFavoriteTrackUseCase(music.id)
            }

            val updatedTrack = music.copy(
                playlistId = if (favorited) {
                    MusicSet.FAVORITES
                } else {
                    0L
                }
            )

            queue = queue.map { queued ->
                if (queued.id == updatedTrack.id) {
                    updatedTrack
                } else {
                    queued
                }
            }

            queuePersistence.save(queue)
            publishAllRuntimeState(forceNotification = true)
            updateNotification(force = true)
        }
    }

    private fun maybeHandleTimedTransition() {
        if (!player.isPlaying || currentIndex !in queue.indices) return

        val durationMs = player.duration
            .takeIf { duration ->
                duration != C.TIME_UNSET
            }
            ?: return

        val remainingMs = durationMs - player.currentPosition
        val currentTrackId = queue[currentIndex].id

        if (timedTransitionTrackId == currentTrackId) return

        val preferences = latestSettingPreferences

        if (preferences.audio.crossFadeEnabled) {
            maybeStartCrossfadeTransition(
                remainingMs = remainingMs,
                currentTrackId = currentTrackId,
                preferences = preferences
            )
            return
        }

        maybeStartGaplessTransition(
            remainingMs = remainingMs,
            currentTrackId = currentTrackId,
            preferences = preferences
        )
    }

    private fun maybeStartCrossfadeTransition(
        remainingMs: Long,
        currentTrackId: Long,
        preferences: SettingPreferences
    ) {
        val fadeDurationMs = (preferences.audio.fadeDurationSeconds * 1000)
            .coerceIn(
                MIN_FADE_DURATION_MS,
                MAX_FADE_DURATION_MS
            )

        val hasNext = playbackModeResolver.resolveNextIndex(
            queueSize = queue.size,
            currentIndex = currentIndex
        ) != null

        if (remainingMs !in 1..fadeDurationMs.toLong() || !hasNext) {
            return
        }

        timedTransitionTrackId = currentTrackId
        timedTransitionStartedAtMs = SystemClock.elapsedRealtime()

        volumeFader.fade(
            from = player.volume,
            to = 0f,
            durationMs = fadeDurationMs.toLong()
        ) {
            if (queue.getOrNull(currentIndex)?.id == currentTrackId) {
                playNextInternal(fromAutoTransition = true)
            }
        }
    }

    private fun maybeStartGaplessTransition(
        remainingMs: Long,
        currentTrackId: Long,
        preferences: SettingPreferences
    ) {
        val hasGaplessNext = playbackModeResolver.resolveNextIndex(
            queueSize = queue.size,
            currentIndex = currentIndex
        ) != null

        if (
            preferences.audio.gaplessPlaybackEnabled &&
            remainingMs in 1..GAPLESS_ADVANCE_WINDOW_MS &&
            hasGaplessNext
        ) {
            timedTransitionTrackId = currentTrackId
            timedTransitionStartedAtMs = SystemClock.elapsedRealtime()
            playNextInternal(fromAutoTransition = true)
        }
    }

    private fun resolvePauseFadeStartPercent(): Float {
        val targetVolume = playbackTuningController.resolveTargetPlaybackVolume()

        if (targetVolume <= 0f) {
            return 0f
        }

        return (player.volume / targetVolume).coerceAtLeast(0f)
    }

    private fun refreshArtworkAndSession(force: Boolean = false) {
        val music = queue.getOrNull(currentIndex)

        if (music == null) {
            currentArtwork = null
            currentArtworkTrackId = NO_TRACK_ID
            mediaSessionController.clearMetadata()
            updateNotification(force = true)
            return
        }

        if (!force && currentArtworkTrackId == music.id) {
            mediaSessionController.updateMetadata(
                music = music,
                artwork = currentArtwork ?: defaultArtwork
            )

            updateNotification()
            return
        }

        val requestedTrackId = music.id

        artworkLoader.load(
            music = music,
            onLoaded = { bitmap ->
                if (queue.getOrNull(currentIndex)?.id != requestedTrackId) {
                    return@load
                }

                handleArtworkLoaded(
                    music = music,
                    bitmap = bitmap
                )
            },
            onFailed = { bitmap ->
                if (queue.getOrNull(currentIndex)?.id != requestedTrackId) {
                    return@load
                }

                handleArtworkLoaded(
                    music = music,
                    bitmap = bitmap
                )
            },
            onCleared = {
                if (currentArtworkTrackId == requestedTrackId) {
                    currentArtwork = defaultArtwork
                }
            }
        )
    }

    private fun handleArtworkLoaded(
        music: Music,
        bitmap: Bitmap
    ) {
        if (queue.getOrNull(currentIndex)?.id != music.id) return

        currentArtwork = bitmap
        currentArtworkTrackId = music.id

        mediaSessionController.updateMetadata(
            music = music,
            artwork = bitmap
        )

        updateNotification(force = true)
    }

    private fun publishAllRuntimeState(
        forceNotification: Boolean = false
    ) {
        updateNotification(force = forceNotification)
        mediaSessionController.updatePlaybackState()
        statePublisher.publish()
    }

    private fun updateNotification(force: Boolean = false) {
        if (isEffectivelyPlaying()) {
            notificationDismissedByUser = false
        }

        notificationController.update(
            force = force,
            keepWhenPaused = !notificationDismissedByUser
        )
    }

    private fun syncCurrentTrackArtwork(
        trackId: Long,
        artworkPath: String?
    ) {
        if (currentIndex !in queue.indices) return

        val currentTrack = queue[currentIndex]
        if (currentTrack.id != trackId || currentTrack.albumPicture == artworkPath) return

        queue = queue.map { music ->
            if (music.id == trackId) {
                music.copy(albumPicture = artworkPath)
            } else {
                music
            }
        }

        queuePersistence.save(queue)
        statePublisher.publish()
        refreshArtworkAndSession(force = true)
    }

    private fun setQueueState(
        newQueue: List<Music>,
        requestedIndex: Int
    ) {
        queue = newQueue

        currentIndex = if (newQueue.isEmpty()) {
            NO_INDEX
        } else {
            requestedIndex.coerceIn(
                0,
                newQueue.lastIndex
            )
        }

        resetTimedTransitionState()
    }

    private fun clearQueueState() {
        queue = emptyList()
        currentIndex = NO_INDEX
        resetTimedTransitionState()
    }

    private fun clearArtworkState() {
        artworkLoader.clear()
        currentArtwork = null
        currentArtworkTrackId = NO_TRACK_ID
    }

    private fun resetTimedTransitionState() {
        timedTransitionTrackId = NO_TRACK_ID
        timedTransitionStartedAtMs = 0L
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
        return when (restoreStatus) {
            RestoreStatus.NotStarted -> queue.isNotEmpty() || playbackRuntimeStateStore.isInitialized()
            RestoreStatus.Restoring -> false
            RestoreStatus.Restored,
            RestoreStatus.Empty,
            RestoreStatus.Failed -> true
        }
    }

    private fun persistSessionFromCurrentState() {
        val track = queue.getOrNull(currentIndex)
        val positionMs = if (
            currentIndex in queue.indices &&
            ::player.isInitialized
        ) {
            player.currentPosition.coerceAtLeast(0L)
        } else {
            playbackRuntimeStateStore.state.value.positionMs.coerceAtLeast(0L)
        }

        runBlocking {
            withContext(dispatchers.io) {
                persistSessionSnapshot(
                    track = track,
                    index = currentIndex,
                    positionMs = positionMs
                )
            }
        }
    }

    private suspend fun persistSessionSnapshot(
        track: Music?,
        index: Int,
        positionMs: Long
    ) {
        if (track == null || index < 0) {
            playbackSessionStore.clearSession()
            return
        }

        playbackSessionStore.saveSession(
            musicId = track.id,
            positionMs = positionMs.coerceAtLeast(0L),
            currentIndex = index
        )
    }

    private fun resolveCurrentSnapshotPositionMs(): Long {
        return if (
            currentIndex in queue.indices &&
            ::player.isInitialized
        ) {
            runCatching {
                player.currentPosition.coerceAtLeast(0L)
            }.getOrDefault(
                playbackRuntimeStateStore.state.value.positionMs.coerceAtLeast(0L)
            )
        } else {
            playbackRuntimeStateStore.state.value.positionMs.coerceAtLeast(0L)
        }
    }

    private fun resolveCurrentSnapshotDurationMs(track: Music?): Long {
        val fallbackDurationMs = track?.duration?.toLong()?.coerceAtLeast(0L)
            ?: playbackRuntimeStateStore.state.value.durationMs.coerceAtLeast(0L)

        if (!::player.isInitialized) {
            return fallbackDurationMs
        }

        return runCatching {
            player.duration
                .takeIf { duration -> duration != C.TIME_UNSET }
                ?.coerceAtLeast(0L)
        }.getOrNull() ?: fallbackDurationMs
    }

    private fun Music.resolveMediaUri(): Uri? {
        val source = data.orEmpty()

        if (source.isBlank()) return null

        return if (source.contains(URI_SCHEME_SEPARATOR)) {
            source.toUri()
        } else {
            Uri.fromFile(File(source))
        }
    }

    private fun Music.toMediaItemOrNull(): MediaItem? {
        val mediaUri = resolveMediaUri() ?: return null

        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(mediaUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .build()
            )
            .build()
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
        const val EXTRA_INDEX = "index"
        const val EXTRA_ACTION_DATA = "music_action_data"
        const val EXTRA_SEEK_POSITION_MS = "seek_position_ms"
        const val EXTRA_STOP_AFTER_CURRENT_TRACK = "stop_after_current_track"

        @Volatile
        var isRunning: Boolean = false
            private set

        private const val NO_INDEX = -1
        private const val NO_TRACK_ID = Long.MIN_VALUE

        private const val PROGRESS_TICK_MS = 500L
        private const val MEDIA3_SESSION_ACTIVITY_REQUEST_CODE = 2
        private const val PREVIOUS_RESTART_WINDOW_MS = 5_000L
        private const val PLAY_PAUSE_FADE_DURATION_MS = 1_000L
        private const val GAPLESS_ADVANCE_WINDOW_MS = 150L
        private const val MIN_FADE_DURATION_MS = 1_000
        private const val MAX_FADE_DURATION_MS = 12_000
        private const val URI_SCHEME_SEPARATOR = "://"

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
