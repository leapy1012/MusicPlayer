package gd.app.musicplayer.playback

import android.app.NotificationManager
import android.app.Service
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
import android.os.IBinder
import android.os.Looper
import android.os.Parcelable
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.model.PlaybackSession
import gd.app.musicplayer.data.repo.PlaybackQueueRepo
import gd.app.musicplayer.util.PreferenceUtil
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MusicPlaybackService : Service() {

    @Inject lateinit var playbackRuntimeStateStore: PlaybackRuntimeStateStore
    @Inject lateinit var playbackSessionStore: PlaybackSessionStore
    @Inject lateinit var playbackQueueRepo: PlaybackQueueRepo

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
    private lateinit var statisticsRecorder: PlaybackStatisticsRecorder
    private lateinit var volumeFader: VolumeFader

    private val mediaSession: MediaSession
        get() = mediaSessionController.session

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
            SupervisorJob() + applicationContext.appDependencies.dispatchers.io
        )

        defaultArtwork = BitmapFactory.decodeResource(
            resources,
            R.drawable.notify_default_album
        )

        configurePlayer()
        configureControllers()

        notificationController.createNotificationChannel()

        /**
         * Important rule:
         *
         * Runtime state is the source of truth while this process already has an initialized
         * PlaybackRuntimeStateStore. Do not overwrite it from PlaybackSessionStore.
         */
        restoreLastSessionIntoRuntimeStateIfNeeded()

        registerScreenOffReceiver()
        progressHandler.post(progressTicker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == null && !player.isPlaying && currentIndex !in queue.indices) {
            isRunning = false
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (action?.requiresImmediateForegroundPromotion() == true) {
            notificationController.ensureForegroundStarted()
        }

        val shouldContinue = commandHandler.handle(intent, action)
        if (!shouldContinue) return START_NOT_STICKY

        stopForegroundIfIdle()
        statePublisher.publish()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val newNightMode = isNightMode(newConfig)
        if (newNightMode != isNightMode) {
            isNightMode = newNightMode
            notificationController.update(force = true)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.isPlaying) {
            notificationController.stopForegroundAndRemove()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        progressHandler.removeCallbacksAndMessages(null)

        if (::volumeFader.isInitialized) volumeFader.cancel()
        if (::artworkLoader.isInitialized) artworkLoader.clear()
        if (::mediaSessionController.isInitialized) mediaSessionController.release()

        AudioEffectsManager.release()

        if (::player.isInitialized) player.release()
        if (::audioFocusController.isInitialized) audioFocusController.abandon()

        unregisterScreenOffReceiver()

        serviceScope.cancel()
        isRunning = false

        super.onDestroy()
    }

    private fun configurePlayer() {
        player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
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
                            AudioEffectsManager.attachAndApply(
                                this@MusicPlaybackService,
                                player
                            )
                            playbackTuningController.applyResolvedPlayerVolume()
                        }

                        Player.STATE_ENDED -> {
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
                            return
                        }

                        Player.STATE_BUFFERING,
                        Player.STATE_IDLE -> Unit
                    }

                    publishAllRuntimeState()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        AudioEffectsManager.applyFromPreferences(
                            this@MusicPlaybackService,
                            player
                        )
                        playbackTuningController.applyResolvedPlayerVolume()
                        statisticsRecorder.recordStartIfNeeded(
                            queue.getOrNull(currentIndex)
                        )
                    }

                    publishAllRuntimeState()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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
        playbackModeResolver = PlaybackModeResolver(this)

        playbackTuningController = PlaybackTuningController(
            context = this,
            player = player,
            currentMusicProvider = { queue.getOrNull(currentIndex) }
        )

        volumeFader = VolumeFader(
            player = player,
            handler = progressHandler
        )

        queuePersistence = PlaybackQueuePersistence(
            playbackQueueRepo,
            scope = serviceScope
        )

        statisticsRecorder = PlaybackStatisticsRecorder(
            context = applicationContext,
            scope = serviceScope
        )

        artworkLoader = ArtworkLoader(
            context = this,
            defaultArtwork = defaultArtwork
        )

        screenOffLockReceiver = ScreenOffLockReceiver {
            currentIndex in queue.indices
        }

        audioFocusController = AudioFocusController(
            context = this,
            isPlaying = { player.isPlaying },
            pausePlayback = { pausePlayback(withFade = false) },
            resumePlayback = { resumePlayback() }
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

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        notificationController = PlaybackNotificationController(
            service = this,
            notificationManager = notificationManager,
            mediaSessionTokenProvider = { mediaSession.sessionToken },
            currentMusicProvider = { queue.getOrNull(currentIndex) },
            currentArtworkProvider = { currentArtwork },
            artworkTrackIdProvider = { currentArtworkTrackId },
            isEffectivelyPlaying = { isEffectivelyPlaying() },
            isFavoriteProvider = {
                queue.getOrNull(currentIndex)?.playlistId == MusicSet.FAVORITES
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

                override fun exitService() = this@MusicPlaybackService.exitService()

                override fun pauseAndPersistForNotificationClose() =
                    this@MusicPlaybackService.pauseAndPersistForNotificationClose()

                override fun togglePlayPause() = this@MusicPlaybackService.togglePlayPause()
                override fun resumePlayback() = this@MusicPlaybackService.resumePlayback()
                override fun pausePlayback() = this@MusicPlaybackService.pausePlayback()
                override fun playNext() = this@MusicPlaybackService.playNext()
                override fun playPrevious() = this@MusicPlaybackService.playPrevious()

                override fun stopPlaybackWithoutClearingQueue() =
                    this@MusicPlaybackService.stopPlaybackWithoutClearingQueue()

                override fun restartCurrentTrack() = this@MusicPlaybackService.restartCurrentTrack()

                override fun clearQueueKeepingNotification() =
                    this@MusicPlaybackService.clearQueueKeepingNotification()

                override fun cyclePlaybackMode() = playbackModeResolver.cyclePlaybackMode()
                override fun setPlaybackMode(mode: Int) = playbackModeResolver.setPlaybackMode(mode)

                override fun toggleFavorite(music: Music) =
                    this@MusicPlaybackService.toggleFavorite(music)

                override fun playIndex(index: Int) =
                    this@MusicPlaybackService.playIndex(index, playWhenReady = true)

                override fun playFromQueue(queue: List<Music>, index: Int) =
                    this@MusicPlaybackService.handlePlayFromQueue(queue, index)

                override fun enqueue(queue: List<Music>) =
                    this@MusicPlaybackService.handleEnqueue(queue)

                override fun playNextQueue(queue: List<Music>) =
                    this@MusicPlaybackService.handlePlayNextQueue(queue)

                override fun replaceQueue(queue: List<Music>, index: Int) =
                    this@MusicPlaybackService.replaceQueue(queue, index)

                override fun seekTo(positionMs: Int) = this@MusicPlaybackService.seekTo(positionMs)

                override fun setStopAfterCurrentTrack(enabled: Boolean) {
                    stopAfterCurrentTrack = enabled
                }

                override fun applyAudioEffects() {
                    AudioEffectsManager.applyFromPreferences(
                        this@MusicPlaybackService,
                        player
                    )
                    playbackTuningController.applyResolvedPlayerVolume()
                }

                override fun applyPlaybackTuning() =
                    playbackTuningController.applyPlaybackTuning()

                override fun currentMusic(): Music? = queue.getOrNull(currentIndex)
            }
        )

        playbackTuningController.applyPlaybackTuning()
    }

    /**
     * Restores service queue/session state during service creation.
     *
     * Rules:
     * - If PlaybackRuntimeStateStore is already initialized, it wins.
     *   We rebuild the service queue/currentIndex from runtime state and never read session state.
     * - If runtime state is not initialized, PlaybackSessionStore is used as the cold-start fallback.
     * - ExoPlayer, MediaSession, notification, and statePublisher calls must run on main.
     */
    private fun restoreLastSessionIntoRuntimeStateIfNeeded() {
        val runtimeAlreadyInitialized = playbackRuntimeStateStore.isInitialized()

        serviceScope.launch {
            val restoredQueue = playbackQueueRepo.getQueue()
            if (restoredQueue.isEmpty()) return@launch

            if (runtimeAlreadyInitialized) {
                val runtimeIndex = playbackRuntimeStateStore.state.value.currentIndex

                progressHandler.post {
                    setQueueState(restoredQueue, runtimeIndex)
                    syncControllersAfterQueueRestore(forceArtwork = true)
                }
                return@launch
            }

            val restoredSession = playbackSessionStore.getLastSession() ?: return@launch
            val safeIndex = resolveStartIndex(restoredQueue, restoredSession)
            val safePosition = restoredSession.positionMs.coerceAtLeast(0L)

            progressHandler.post {
                if (playbackRuntimeStateStore.isInitialized()) {
                    val runtimeIndex = playbackRuntimeStateStore.state.value.currentIndex
                    setQueueState(restoredQueue, runtimeIndex)
                    syncControllersAfterQueueRestore(forceArtwork = true)
                    return@post
                }

                playbackRuntimeStateStore.initializeIfNeeded()
                setQueueState(restoredQueue, safeIndex)
                syncControllersAfterQueueRestore(forceArtwork = true)
                statePublisher.publishRestored(safeIndex, safePosition, restoredQueue)
            }
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
            val persistedQueue = runCatching {
                playbackQueueRepo.queue.first()
            }.getOrDefault(emptyList())

            val persistedSession = playbackSessionStore.getLastSession()

            if (persistedQueue.isNotEmpty()) {
                val startIndex = persistedSession
                    ?.let { resolveStartIndex(persistedQueue, it) }
                    ?: 0

                val startPositionMs = persistedSession?.positionMs?.coerceAtLeast(0L) ?: 0L

                progressHandler.post {
                    setQueueState(persistedQueue, startIndex)
                    mediaSessionController.updateQueue()
                    playIndex(startIndex, playWhenReady = true)
                    if (startPositionMs > 0L) {
                        player.seekTo(startPositionMs)
                        statePublisher.publish()
                    }
                }
                return@launch
            }

            val tracks = runCatching {
                applicationContext.appDependencies.mainRepo.observeTracks(
                    musicSet = MusicSet.Tracks,
                    sortStyle = PreferenceUtil.getInstance(applicationContext)
                        .getSortStyle(MusicSet.Tracks),
                    sortDescending = PreferenceUtil.getInstance(applicationContext)
                        .isSortReversed(MusicSet.Tracks, false)
                ).first()
            }.getOrDefault(emptyList())

            if (tracks.isEmpty()) return@launch

            playbackQueueRepo.replaceQueue(tracks)
            playbackSessionStore.saveSession(
                musicId = tracks.first().id,
                positionMs = 0L,
                currentIndex = 0
            )

            progressHandler.post {
                setQueueState(tracks, 0)
                mediaSessionController.updateQueue()
                playIndex(0, playWhenReady = true)
            }
        }
    }

    private fun resolveStartIndex(
        queue: List<Music>,
        session: PlaybackSession
    ): Int {
        if (queue.isEmpty()) return 0

        val indexByMusicId = queue.indexOfFirst { it.id == session.musicId }

        return if (indexByMusicId >= 0) {
            indexByMusicId
        } else {
            session.currentIndex.coerceIn(0, queue.lastIndex)
        }
    }

    private fun togglePlayPause() {
        if (player.isPlaying) pausePlayback() else resumePlayback()
    }

    private fun exitService() {
        notificationController.stopForegroundAndRemove()
        isRunning = false
        stopSelf()
    }

    private fun stopForegroundIfIdle() {
        notificationController.stopForegroundIfIdle(
            hasQueueItem = currentIndex in queue.indices,
            keepIdleNotification = keepIdleNotification
        )
    }

    private fun handlePlayFromQueue(incomingQueue: List<Music>, incomingIndex: Int) {
        if (incomingQueue.isEmpty()) return

        setQueueState(incomingQueue, incomingIndex)
        queuePersistence.save(queue)
        mediaSessionController.updateQueue()
        playIndex(currentIndex, playWhenReady = true)
    }

    private fun handleEnqueue(incomingQueue: List<Music>) {
        if (incomingQueue.isEmpty()) return

        val wasEmpty = queue.isEmpty()
        val nextQueue = queue + incomingQueue
        val nextIndex = if (wasEmpty) 0 else currentIndex

        setQueueState(nextQueue, nextIndex)
        queuePersistence.save(queue)
        mediaSessionController.updateQueue()
        publishAllRuntimeState()
    }

    private fun handlePlayNextQueue(incomingQueue: List<Music>) {
        if (incomingQueue.isEmpty()) return

        if (queue.isEmpty()) {
            setQueueState(incomingQueue, 0)
            queuePersistence.save(queue)
            mediaSessionController.updateQueue()
            playIndex(0, playWhenReady = true)
            return
        }

        val insertIndex = if (currentIndex == queue.lastIndex) queue.size else currentIndex + 1

        val nextQueue = queue.toMutableList()
            .apply { addAll(insertIndex, incomingQueue) }
            .toList()

        setQueueState(nextQueue, currentIndex)
        queuePersistence.save(queue)
        mediaSessionController.updateQueue()
        publishAllRuntimeState()
    }

    private fun replaceQueue(newQueue: List<Music>, requestedIndex: Int) {
        if (newQueue.isEmpty()) {
            stopPlayback()
            return
        }

        val previousTrackId = queue.getOrNull(currentIndex)?.id
        val wasPlaying = player.isPlaying

        val preservedIndex = previousTrackId?.let { trackId ->
            newQueue.indexOfFirst { it.id == trackId }.takeIf { it >= 0 }
        }

        val targetIndex = (preservedIndex ?: requestedIndex).coerceIn(0, newQueue.lastIndex)

        setQueueState(newQueue, targetIndex)
        queuePersistence.save(queue)
        mediaSessionController.updateQueue()

        val selectedTrack = queue.getOrNull(currentIndex)
        val canKeepCurrentTrack = previousTrackId != null &&
                selectedTrack?.id == previousTrackId &&
                player.currentMediaItem != null &&
                player.playbackState != Player.STATE_IDLE

        if (canKeepCurrentTrack) {
            refreshArtworkAndSession(force = false)
            publishAllRuntimeState()
            return
        }

        playIndex(currentIndex, playWhenReady = wasPlaying)
    }

    private fun playIndex(index: Int, playWhenReady: Boolean) {
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

        if (playWhenReady && !audioFocusController.request()) return

        player.stop()
        player.clearMediaItems()
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

    private fun resumePlayback() {
        if (queue.isEmpty()) {
            resumeWithPersistedOrDefaultQueue()
            return
        }

        if (!audioFocusController.request()) return

        if (currentIndex !in queue.indices) {
            playIndex(0, playWhenReady = true)
            return
        }

        if (player.playbackState == Player.STATE_IDLE) {
            playIndex(currentIndex, playWhenReady = true)
            return
        }

        if (!player.isPlaying) {
            player.play()
            playbackTuningController.applyPlaybackTuning()
            AudioEffectsManager.applyFromPreferences(this, player)

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
        if (!player.isPlaying) return

        if (withFade) {
            volumeFader.fade(
                from = player.volume,
                to = 0f,
                durationMs = PLAY_PAUSE_FADE_DURATION_MS
            ) {
                player.pause()
                playbackTuningController.applyResolvedPlayerVolume()
                publishAllRuntimeState()
            }
        } else {
            player.pause()
            publishAllRuntimeState()
        }
    }

    private fun restartCurrentTrack() {
        if (currentIndex !in queue.indices) return

        resetTimedTransitionState()
        player.seekTo(0)

        if (!player.isPlaying) {
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
            if (fromAutoTransition) stopPlayback()
            return
        }

        playIndex(nextIndex, playWhenReady = true)
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

        playIndex(previousIndex, playWhenReady = true)
    }

    private fun seekTo(positionMs: Int) {
        if (currentIndex !in queue.indices) return

        val fallbackDurationMs = queue.getOrNull(currentIndex)?.duration ?: 0
        val durationMs = player.duration
            .takeIf { it != C.TIME_UNSET && it > 0L }
            ?.toInt()
            ?: fallbackDurationMs

        val target = positionMs.coerceIn(0, durationMs)

        player.seekTo(target.toLong())
        publishAllRuntimeState()
    }

    private fun stopPlayback() {
        persistSessionFromRuntimeStateStore()
        keepIdleNotification = false
        clearArtworkState()
        statisticsRecorder.reset()
        resetTimedTransitionState()
        volumeFader.cancel()

        player.stop()
        player.clearMediaItems()

        clearQueueState()
        queuePersistence.save(queue)

        statePublisher.reset()
        mediaSessionController.clearMetadata()
        mediaSessionController.clearQueue()
        audioFocusController.abandon()
        notificationController.stopForegroundAndRemove()

        isRunning = false
        stopSelf()
    }

    private fun stopPlaybackWithoutClearingQueue() {
        keepIdleNotification = false
        resetTimedTransitionState()
        volumeFader.cancel()
        persistSessionFromRuntimeStateStore()

        player.stop()
        player.clearMediaItems()
        audioFocusController.abandon()

        publishAllRuntimeState()
        notificationController.stopForegroundAndRemove()

        isRunning = false
        stopSelf()
    }

    private fun clearQueueKeepingNotification() {
        keepIdleNotification = true
        clearArtworkState()
        statisticsRecorder.reset()
        resetTimedTransitionState()
        volumeFader.cancel()

        player.stop()
        player.clearMediaItems()

        clearQueueState()
        queuePersistence.save(queue)

        statePublisher.reset()
        mediaSessionController.clearMetadata()
        mediaSessionController.clearQueue()
        audioFocusController.abandon()
        notificationController.stopForegroundDetached()
        notificationController.update(force = true)
    }

    private fun pauseAndPersistForNotificationClose() {
        persistSessionFromRuntimeStateStore()
        pausePlayback(withFade = false)
        notificationController.stopForegroundAndRemove()

        isRunning = false
        stopSelf()
    }

    private fun handleNotificationFavoriteToggle() {
        queue.getOrNull(currentIndex)?.let(::toggleFavorite)
    }

    private fun toggleFavorite(music: Music) {
        serviceScope.launch {
            val favorited = applicationContext.appDependencies.toggleFavoriteTrackUseCase(music.id)

            progressHandler.post {
                val updatedTrack = music.copy(
                    playlistId = if (favorited) MusicSet.FAVORITES else 0L
                )

                queue = queue.map { queued ->
                    if (queued.id == updatedTrack.id) updatedTrack else queued
                }

                queuePersistence.save(queue)
                publishAllRuntimeState()
                notificationController.update(force = true)
            }
        }
    }

    private fun maybeHandleTimedTransition() {
        if (!player.isPlaying || currentIndex !in queue.indices) return

        val durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: return
        val remainingMs = durationMs - player.currentPosition
        val currentTrackId = queue[currentIndex].id

        if (timedTransitionTrackId == currentTrackId) return

        val preferences = PreferenceUtil.getInstance(this)

        if (preferences.getBooleanPreference(KEY_CROSS_FADE, false)) {
            val fadeDurationMs = preferences
                .getIntPreference(KEY_FADE_DURATION_MS, DEFAULT_FADE_DURATION_MS)
                .coerceIn(MIN_FADE_DURATION_MS, MAX_FADE_DURATION_MS)

            val hasNext = playbackModeResolver.resolveNextIndex(
                queueSize = queue.size,
                currentIndex = currentIndex
            ) != null

            if (remainingMs in 1..fadeDurationMs.toLong() && hasNext) {
                timedTransitionTrackId = currentTrackId
                timedTransitionStartedAtMs = android.os.SystemClock.elapsedRealtime()

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
            return
        }

        val hasGaplessNext = playbackModeResolver.resolveNextIndex(
            queueSize = queue.size,
            currentIndex = currentIndex
        ) != null

        if (
            preferences.getBooleanPreference(KEY_GAPLESS_PLAYBACK, false) &&
            remainingMs in 1..GAPLESS_ADVANCE_WINDOW_MS &&
            hasGaplessNext
        ) {
            timedTransitionTrackId = currentTrackId
            timedTransitionStartedAtMs = android.os.SystemClock.elapsedRealtime()
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
                currentArtwork = bitmap
                currentArtworkTrackId = music.id
                mediaSessionController.updateMetadata(music, bitmap)
                notificationController.update()
            },
            onFailed = { bitmap ->
                currentArtwork = bitmap
                currentArtworkTrackId = music.id
                mediaSessionController.updateMetadata(music, bitmap)
                notificationController.update()
            },
            onCleared = {
                if (currentArtworkTrackId == music.id) {
                    currentArtwork = defaultArtwork
                }
            }
        )
    }

    private fun publishAllRuntimeState() {
        notificationController.update()
        mediaSessionController.updatePlaybackState()
        statePublisher.publish()
    }

    private fun setQueueState(newQueue: List<Music>, requestedIndex: Int) {
        queue = newQueue
        currentIndex = if (newQueue.isEmpty()) {
            NO_INDEX
        } else {
            requestedIndex.coerceIn(0, newQueue.lastIndex)
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
            registerReceiver(screenOffLockReceiver, filter)
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
        private const val PREVIOUS_RESTART_WINDOW_MS = 5_000L
        private const val PLAY_PAUSE_FADE_DURATION_MS = 1_000L
        private const val GAPLESS_ADVANCE_WINDOW_MS = 150L
        private const val DEFAULT_FADE_DURATION_MS = 6_000
        private const val MIN_FADE_DURATION_MS = 1_000
        private const val MAX_FADE_DURATION_MS = 12_000
        private const val URI_SCHEME_SEPARATOR = "://"

        private const val KEY_CROSS_FADE = "fade_enable"
        private const val KEY_GAPLESS_PLAYBACK = "gapless_play"
        private const val KEY_FADE_DURATION_MS = "fade_duration"

        fun startAction(context: Context, action: String): Boolean {
            return startAction(context, action, null)
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
                    actionData?.let { putExtra(EXTRA_ACTION_DATA, it) }
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
