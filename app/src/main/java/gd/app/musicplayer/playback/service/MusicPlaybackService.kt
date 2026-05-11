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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
import gd.app.musicplayer.playback.VolumeFader
import gd.app.musicplayer.ui.shell.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

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
    private lateinit var volumeFader: VolumeFader

    private val compatMediaSession: MediaSession
        get() = mediaSessionController.session

    private var media3Session: androidx.media3.session.MediaSession? = null

    private val progressHandler = Handler(Looper.getMainLooper())

    private var screenReceiverRegistered = false

    private var queue: List<Music> = emptyList()
    private var currentIndex = NO_INDEX

    private var currentArtwork: Bitmap? = null
    private var currentArtworkTrackId = NO_TRACK_ID

    private var timedTransitionTrackId = NO_TRACK_ID
    private var timedTransitionStartedAtMs = 0L

    private var keepIdleNotification = false
    private var stopAfterCurrentTrack = false
    private var isNightMode = false

    @Volatile
    private var latestSettingPreferences = SettingPreferences()

    @Volatile
    private var latestDesktopLyricPreference = DesktopLyricPreference()

    private val progressTicker = object : Runnable {
        override fun run() {
            maybeHandleTimedTransition()
            statePublisher.publish()
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

        notificationController.createNotificationChannel()

        restoreLastSessionIntoRuntimeStateIfNeeded()
        registerScreenOffReceiver()
        android.util.Log.e("Leapy", "queue" + queue.toString())
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
        super.onStartCommand(intent, flags, startId)

        val action = intent?.action

        if (action == ACTION_APP_TASK_REMOVED) {
            stopServiceIfAppClosedAndNotPlaying()
            return START_NOT_STICKY
        }

        if (action == null && !isEffectivelyPlaying() && currentIndex !in queue.indices) {
            isRunning = false
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (action?.requiresImmediateForegroundPromotion() == true) {
            notificationController.ensureForegroundStarted()
        }

        val shouldContinue = commandHandler.handle(
            intent = intent,
            action = action
        )

        if (!shouldContinue) {
            return START_NOT_STICKY
        }

        stopForegroundIfIdle()
        statePublisher.publish()

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopServiceIfAppClosedAndNotPlaying()
//        super.onTaskRemoved(rootIntent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val newNightMode = isNightMode(newConfig)

        if (newNightMode != isNightMode) {
            isNightMode = newNightMode
            notificationController.update(force = true)
        }
    }

    override fun onDestroy() {
        progressHandler.removeCallbacksAndMessages(null)

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

    private fun observePreferences() {
        observeSettingPreferences()
        observeDesktopLyricPreference()
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

    private fun configurePlayer() {
        player = ExoPlayer.Builder(this)
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

                    publishAllRuntimeState()
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int
                ) {
                    resetTimedTransitionState()
                    playbackTuningController.applyResolvedPlayerVolume()
                    refreshArtworkAndSession(force = true)

                    if (player.isPlaying) {
                        statisticsRecorder.recordStartIfNeeded(
                            music = queue.getOrNull(currentIndex),
                            force = true
                        )
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    playNext()
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
            currentMusicProvider = { queue.getOrNull(currentIndex) },
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
            isPlaying = { player.isPlaying },
            pausePlayback = { pausePlayback(withFade = false) },
            resumePlayback = { resumePlayback() },
            isSimultaneousPlayEnabled = {
                latestSettingPreferences.audio.simultaneousPlayEnabled
            }
        )

        statePublisher = PlaybackStatePublisher(
            context = applicationContext,
            player = player,
            runtimeStateStore = playbackRuntimeStateStore,
            queueProvider = { queue },
            currentIndexProvider = { currentIndex }
        )

        mediaSessionController = PlaybackMediaSessionController(
            context = this,
            player = player,
            queueProvider = { queue },
            currentIndexProvider = { currentIndex },
            isEffectivelyPlaying = { isEffectivelyPlaying() },
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
            mediaSessionTokenProvider = { compatMediaSession.sessionToken },
            currentMusicProvider = { queue.getOrNull(currentIndex) },
            currentArtworkProvider = { currentArtwork },
            artworkTrackIdProvider = { currentArtworkTrackId },
            isEffectivelyPlaying = { isEffectivelyPlaying() },
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

                override fun updateNotificationDelayed() {
                    progressHandler.postDelayed(
                        { notificationController.update() },
                        NOTIFICATION_UPDATE_DELAY_MS
                    )
                }

                override fun refreshNotificationStyle() {
                    notificationController.refreshStyle { delayMs, block ->
                        progressHandler.postDelayed(block, delayMs)
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

                override fun playIndex(index: Int) {
                    this@MusicPlaybackService.playIndex(
                        index = index,
                        playWhenReady = true
                    )
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

                override fun toggleDesktopLyricsLock() {
                    serviceScope.launch {
                        desktopLyricPreferenceStore.setLocked(
                            !latestDesktopLyricPreference.locked
                        )
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
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        return androidx.media3.session.MediaSession.Builder(
            this,
            player
        )
            .setSessionActivity(sessionActivity)
            .build()
    }

    private fun handleTrackEnded() {
        resetTimedTransitionState()

        statisticsRecorder.recordCompletion(
            queue.getOrNull(currentIndex)
        )

        if (stopAfterCurrentTrack) {
            stopAfterCurrentTrack = false
            stopPlayback()
            return
        }

        playNext(fromAutoTransition = true)
    }

    private fun restoreLastSessionIntoRuntimeStateIfNeeded() {
        val runtimeAlreadyInitialized = playbackRuntimeStateStore.isInitialized()

        serviceScope.launch {
            val restoredQueue = withContext(dispatchers.io) {
                playbackQueueRepo.getQueue()
            }

            android.util.Log.e("Leapy", "restoredQueue = " + restoredQueue)

            if (restoredQueue.isEmpty()) return@launch

            if (runtimeAlreadyInitialized) {
                val runtimeIndex = playbackRuntimeStateStore.state.value.currentIndex

                setQueueState(
                    newQueue = restoredQueue,
                    requestedIndex = runtimeIndex
                )

                syncControllersAfterQueueRestore(forceArtwork = true)
                return@launch
            }

            val restoredSession = withContext(dispatchers.io) {
                playbackSessionStore.getLastSession()
            } ?: return@launch

            val safeIndex = resolveStartIndex(
                queue = restoredQueue,
                session = restoredSession
            )

            val safePosition = restoredSession.positionMs.coerceAtLeast(0L)

            if (playbackRuntimeStateStore.isInitialized()) {
                val runtimeIndex = playbackRuntimeStateStore.state.value.currentIndex

                setQueueState(
                    newQueue = restoredQueue,
                    requestedIndex = runtimeIndex
                )

                syncControllersAfterQueueRestore(forceArtwork = true)
                return@launch
            }

            playbackRuntimeStateStore.initializeIfNeeded()

            setQueueState(
                newQueue = restoredQueue,
                requestedIndex = safeIndex
            )

            syncControllersAfterQueueRestore(forceArtwork = true)

            statePublisher.publishRestored(safeIndex, safePosition, restoredQueue)
        }
    }

    private fun syncControllersAfterQueueRestore(forceArtwork: Boolean) {
        mediaSessionController.updateQueue()
        refreshArtworkAndSession(force = forceArtwork)
        mediaSessionController.updatePlaybackState()
        notificationController.update(force = true)
    }

    private fun resumeWithPersistedOrDefaultQueue() {
        serviceScope.launch {
            val persistedQueue = withContext(dispatchers.io) {
                runCatching {
                    playbackQueueRepo.queue.first()
                }.getOrDefault(emptyList())
            }

            val persistedSession = withContext(dispatchers.io) {
                playbackSessionStore.getLastSession()
            }

            if (persistedQueue.isNotEmpty()) {
                val startIndex = persistedSession
                    ?.let { session ->
                        resolveStartIndex(
                            queue = persistedQueue,
                            session = session
                        )
                    }
                    ?: 0

                val startPositionMs = persistedSession
                    ?.positionMs
                    ?.coerceAtLeast(0L)
                    ?: 0L

                setQueueState(
                    newQueue = persistedQueue,
                    requestedIndex = startIndex
                )

                mediaSessionController.updateQueue()

                playIndex(
                    index = currentIndex,
                    playWhenReady = true
                )

                if (startPositionMs > 0L) {
                    player.seekTo(startPositionMs)
                    statePublisher.publish()
                }

                return@launch
            }

            val tracks = withContext(dispatchers.io) {
                runCatching {
                    observeTracksUseCase(MusicSet.Tracks).first()
                }.getOrDefault(emptyList())
            }

            if (tracks.isEmpty()) return@launch

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

            mediaSessionController.updateQueue()

            playIndex(
                index = 0,
                playWhenReady = true
            )
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

        setQueueState(
            newQueue = incomingQueue,
            requestedIndex = incomingIndex
        )

        queuePersistence.save(queue)
        mediaSessionController.updateQueue()

        playIndex(
            index = currentIndex,
            playWhenReady = true
        )
    }

    private fun handleEnqueue(incomingQueue: List<Music>) {
        if (incomingQueue.isEmpty()) return

        val wasEmpty = queue.isEmpty()
        val nextQueue = queue + incomingQueue
        val nextIndex = if (wasEmpty) 0 else currentIndex

        setQueueState(
            newQueue = nextQueue,
            requestedIndex = nextIndex
        )

        queuePersistence.save(queue)
        mediaSessionController.updateQueue()
        publishAllRuntimeState()
    }

    private fun handlePlayNextQueue(incomingQueue: List<Music>) {
        if (incomingQueue.isEmpty()) return

        if (queue.isEmpty()) {
            setQueueState(
                newQueue = incomingQueue,
                requestedIndex = 0
            )

            queuePersistence.save(queue)
            mediaSessionController.updateQueue()

            playIndex(
                index = 0,
                playWhenReady = true
            )

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

        val previousTrackId = queue.getOrNull(currentIndex)?.id
        val wasPlaying = isEffectivelyPlaying()

        val preservedIndex = previousTrackId?.let { trackId ->
            newQueue
                .indexOfFirst { music -> music.id == trackId }
                .takeIf { index -> index >= 0 }
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

        val selectedTrack = queue.getOrNull(currentIndex)

        val canKeepCurrentTrack =
            previousTrackId != null &&
                    selectedTrack?.id == previousTrackId &&
                    player.currentMediaItem != null &&
                    player.playbackState != Player.STATE_IDLE

        if (canKeepCurrentTrack) {
            refreshArtworkAndSession(force = false)
            publishAllRuntimeState()
            return
        }

        playIndex(
            index = currentIndex,
            playWhenReady = wasPlaying
        )
    }

    private fun playIndex(
        index: Int,
        playWhenReady: Boolean
    ) {
        if (queue.isEmpty()) return
        if (index !in queue.indices) return

        currentIndex = index

        statisticsRecorder.reset()
        resetTimedTransitionState()

        val music = queue[index]
        val mediaUri = music.resolveMediaUri() ?: run {
            playNext()
            return
        }

        if (playWhenReady && !audioFocusController.request()) {
            return
        }

        player.setMediaItem(MediaItem.fromUri(mediaUri))
        player.prepare()
        player.playWhenReady = playWhenReady

        if (playWhenReady && playbackTuningController.isPlayPauseFadeEnabled()) {
            volumeFader.fade(
                from = 0f,
                to = playbackTuningController.resolveTargetPlaybackVolume(),
                durationMs = PLAY_PAUSE_FADE_DURATION_MS
            )
        } else {
            playbackTuningController.applyResolvedPlayerVolume()
        }

        refreshArtworkAndSession(force = true)
        publishAllRuntimeState()
    }

    private fun togglePlayPause() {
        if (isEffectivelyPlaying()) {
            pausePlayback()
        } else {
            resumePlayback()
        }
    }

    private fun resumePlayback() {
        android.util.Log.e("Leapy", "resume queue" + queue.toString())
        if (queue.isEmpty()) {
            resumeWithPersistedOrDefaultQueue()
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
                volumeFader.fade(
                    from = 0f,
                    to = playbackTuningController.resolveTargetPlaybackVolume(),
                    durationMs = PLAY_PAUSE_FADE_DURATION_MS
                )
            }

            publishAllRuntimeState()
        }
    }

    private fun pausePlayback(
        withFade: Boolean = playbackTuningController.isPlayPauseFadeEnabled()
    ) {
        if (!isEffectivelyPlaying()) return

        if (withFade && player.isPlaying) {
            volumeFader.fade(
                from = player.volume,
                to = 0f,
                durationMs = PLAY_PAUSE_FADE_DURATION_MS
            ) {
                player.pause()
                playbackTuningController.applyResolvedPlayerVolume()
                publishAllRuntimeState()
            }
            return
        }

        player.pause()
        publishAllRuntimeState()
    }

    private fun restartCurrentTrack() {
        if (currentIndex !in queue.indices) return

        resetTimedTransitionState()
        player.seekTo(0)

        if (!isEffectivelyPlaying()) {
            resumePlayback()
        } else {
            publishAllRuntimeState()
        }
    }

    private fun playNext(fromAutoTransition: Boolean = false) {
        if (queue.isEmpty()) return

        resetTimedTransitionState()

        val nextIndex = playbackModeResolver.resolveNextIndex(
            queueSize = queue.size,
            currentIndex = currentIndex
        ) ?: run {
            if (fromAutoTransition) {
                stopPlayback()
            }
            return
        }

        playIndex(
            index = nextIndex,
            playWhenReady = true
        )
    }

    private fun playPrevious() {
        if (queue.isEmpty()) return

        if (player.currentPosition > PREVIOUS_RESTART_WINDOW_MS) {
            seekTo(0)
            return
        }

        val previousIndex = playbackModeResolver.resolvePreviousIndex(
            queueSize = queue.size,
            currentIndex = currentIndex,
            shouldRestartCurrent = false
        ) ?: return

        playIndex(
            index = previousIndex,
            playWhenReady = true
        )
    }

    private fun seekTo(positionMs: Int) {
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
        shutdownPlayback(
            clearQueue = true,
            clearRuntimeState = true,
            removeNotification = true,
            stopService = true
        )
    }

    private fun stopPlaybackWithoutClearingQueue() {
        shutdownPlayback(
            clearQueue = false,
            clearRuntimeState = false,
            removeNotification = true,
            stopService = true
        )
    }

    private fun pauseAndPersistForNotificationClose() {
        if (isEffectivelyPlaying()) {
            player.pause()
        }

        publishAllRuntimeState()
        persistSessionFromRuntimeStateStore()
        notificationController.stopForegroundAndRemove()
    }

    private fun exitService() {
        shutdownPlayback(
            clearQueue = false,
            clearRuntimeState = false,
            removeNotification = true,
            stopService = true
        )
    }

    private fun stopServiceIfAppClosedAndNotPlaying() {
        if (isEffectivelyPlaying()) {
            notificationController.update(force = true)
            return
        }

        shutdownPlayback(
            clearQueue = false,
            clearRuntimeState = false,
            removeNotification = true,
            stopService = true
        )
    }

    private fun clearQueueKeepingNotification() {
        keepIdleNotification = true

        shutdownPlayback(
            clearQueue = true,
            clearRuntimeState = true,
            removeNotification = false,
            stopService = false
        )

        notificationController.stopForegroundDetached()
        notificationController.update(force = true)
    }

    private fun shutdownPlayback(
        clearQueue: Boolean,
        clearRuntimeState: Boolean,
        removeNotification: Boolean,
        stopService: Boolean
    ) {
        persistSessionFromRuntimeStateStore()

        keepIdleNotification = false
        stopAfterCurrentTrack = false

        statisticsRecorder.reset()
        resetTimedTransitionState()
        volumeFader.cancel()

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
            queuePersistence.save(queue)
            mediaSessionController.clearMetadata()
            mediaSessionController.clearQueue()
        }

        if (clearRuntimeState) {
            statePublisher.reset()
        } else {
            publishStateAfterShutdown()
        }

        if (removeNotification) {
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

    private fun stopForegroundIfIdle() {
        notificationController.stopForegroundIfIdle(
            hasQueueItem = currentIndex in queue.indices,
            keepIdleNotification = keepIdleNotification
        )
    }

    private fun handleNotificationFavoriteToggle() {
        queue.getOrNull(currentIndex)?.let(::toggleFavorite)
    }

    private fun toggleFavorite(music: Music) {
        serviceScope.launch {
            val favorited = withContext(dispatchers.io) {
                toggleFavoriteTrackUseCase(music.id)
            }

            val updatedTrack = music.copy(
                playlistId = if (favorited) {
                    MusicSet.Companion.FAVORITES
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
            publishAllRuntimeState()
            notificationController.update(force = true)
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
                playNext(fromAutoTransition = true)
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
            playNext(fromAutoTransition = true)
        }
    }

    private fun refreshArtworkAndSession(force: Boolean = false) {
        val music = queue.getOrNull(currentIndex)

        if (music == null) {
            currentArtwork = null
            currentArtworkTrackId = NO_TRACK_ID
            mediaSessionController.clearMetadata()
            notificationController.update()
            return
        }

        if (!force && currentArtworkTrackId == music.id) {
            mediaSessionController.updateMetadata(
                music = music,
                artwork = currentArtwork ?: defaultArtwork
            )

            notificationController.update()
            return
        }

        artworkLoader.load(
            music = music,
            onLoaded = { bitmap ->
                handleArtworkLoaded(
                    music = music,
                    bitmap = bitmap
                )
            },
            onFailed = { bitmap ->
                handleArtworkLoaded(
                    music = music,
                    bitmap = bitmap
                )
            },
            onCleared = {
                if (currentArtworkTrackId == music.id) {
                    currentArtwork = defaultArtwork
                }
            }
        )
    }

    private fun handleArtworkLoaded(
        music: Music,
        bitmap: Bitmap
    ) {
        currentArtwork = bitmap
        currentArtworkTrackId = music.id

        mediaSessionController.updateMetadata(
            music = music,
            artwork = bitmap
        )

        notificationController.update()
    }

    private fun publishAllRuntimeState() {
        notificationController.update()
        mediaSessionController.updatePlaybackState()
        statePublisher.publish()
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

    private fun persistSessionFromRuntimeStateStore() {
        val runtimeState = playbackRuntimeStateStore.state.value

        serviceScope.launch {
            withContext(dispatchers.io) {
                val track = runtimeState.currentTrack

                if (track == null || runtimeState.currentIndex < 0) {
                    playbackSessionStore.clearSession()
                } else {
                    playbackSessionStore.saveSession(
                        musicId = track.id,
                        positionMs = runtimeState.positionMs.coerceAtLeast(0L),
                        currentIndex = runtimeState.currentIndex
                    )
                }
            }
        }
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
        const val ACTION_UPDATE_NOTIFICATION = "ACTION_UPDATE_NOTIFICATION"
        const val ACTION_FOREGROUND = "ACTION_FOREGROUND"
        const val ACTION_NOTIFICATION_STYLE = "ACTION_NOTIFICATION_STYLE"
        const val ACTION_EXIT = "opraton_action_exit"
        const val ACTION_PLAY_PAUSE = "music_action_play_pause"
        const val ACTION_CHANGE_MODE = "opraton_action_change_mode"
        const val ACTION_MODE_RANDOM = "ACTION_MODE_RANDOM"
        const val ACTION_MODE_LOOP = "ACTION_MODE_LOOP"
        const val ACTION_CHANGE_FAVORITE = "opraton_action_change_favourite"
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

        const val ACTION_APP_TASK_REMOVED =
            "gd.app.musicplayer.action.APP_TASK_REMOVED"

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
        private const val NOTIFICATION_UPDATE_DELAY_MS = 50L
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
