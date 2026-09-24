package gd.app.musicplayer.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.core.common.dispatcher.AppDispatchers
import gd.app.musicplayer.core.database.dao.MusicDao
import gd.app.musicplayer.core.datastore.PlaybackStatePreferenceStore
import gd.app.musicplayer.core.common.extension.toQueueMediaId
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import gd.app.musicplayer.playback.command.PlaybackServiceActions
import gd.app.musicplayer.playback.command.PlaybackServiceStarter
import gd.app.musicplayer.playback.effects.PlaybackFadeController
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import gd.app.musicplayer.playback.queue.PlaybackQueueManager
import gd.app.musicplayer.playback.queue.PlayerQueueController
import gd.app.musicplayer.playback.state.PlaybackRuntimeStateStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-scoped audio controller matching original [y6.y] (AudioController):
 * - [ensureInitialized] = [y6.y.Y] + [n0] warm of playlist -9
 * - [playOrPause] = [A0], [play] = [y0], [pause] = pause path via BassPlayer [w6.b] fade
 * - Audio plays in-process; [MusicPlaybackService] is started for notification after play-state
 *   becomes true ([y6.y.c] → [u0] → MusicPlayService ACTION_UPDATE_NOTIFICATION)
 */
@Singleton
class PlaybackAudioController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
    private val queueManager: PlaybackQueueManager,
    private val playbackQueueRepo: PlaybackQueueRepo,
    private val playbackStatePreferenceStore: PlaybackStatePreferenceStore,
    private val runtimeStateStore: PlaybackRuntimeStateStore,
    private val musicDao: MusicDao,
    private val processPlayerHolder: ProcessPlayerHolder,
    private val playbackModeResolver: PlaybackModeResolver,
    private val playbackFadeController: PlaybackFadeController
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val started = AtomicBoolean(false)

    /** Original [y6.y.f16758j] — queue warm finished. */
    @Volatile
    private var warmReady = false

    /** Original [y6.y.f16759k] — play requested before warm finished. */
    @Volatile
    private var pendingPlay = false

    @Volatile
    private var playerQueueController: PlayerQueueController? = null

    /**
     * Original [y6.y.Y] side-effect: first access constructs controller and starts [n0].
     * Called from Main / BMusicActivity equivalent.
     */
    fun ensureInitialized() {
        if (!started.compareAndSet(false, true)) return
        scope.launch(dispatchers.io) {
            warmQueueFromPlayingPlaylist()
        }
    }

    /** Original [A0] playOrPause. */
    fun playOrPause() {
        val playing = runtimeStateStore.state.value.isPlaying ||
            (processPlayerHolder.playerOrNull()?.isPlaying == true)
        if (playing) {
            pause()
        } else {
            play()
        }
    }

    /**
     * Original [y0] play.
     * If warm not ready → set pending flag and return (no service start).
     */
    fun play() {
        if (!warmReady) {
            pendingPlay = true
            ensureInitialized()
            return
        }
        scope.launch {
            playInternal()
        }
    }

    /** Pause current in-process player when available; else no-op until service path. */
    fun pause() {
        pendingPlay = false
        val player = processPlayerHolder.playerOrNull()
        if (player != null) {
            playbackFadeController.bind(player)
            playbackFadeController.fadeOutAndPause(player) {
                publishRuntimeIsPlaying(false)
            }
            return
        }
        // Service may own transport after attach — fall through via Intent only if needed.
        PlaybackServiceStarter.startAction(context, PlaybackServiceActions.ACTION_PAUSE)
    }

    fun playNext() {
        ensureInitialized()
        if (!warmReady || !processPlayerHolder.isCreated) {
            PlaybackServiceStarter.startAction(context, PlaybackServiceActions.ACTION_NEXT)
            return
        }
        scope.launch { playNextInternal() }
    }

    fun playPrevious() {
        ensureInitialized()
        if (!warmReady || !processPlayerHolder.isCreated) {
            PlaybackServiceStarter.startAction(context, PlaybackServiceActions.ACTION_PREVIOUS)
            return
        }
        scope.launch { playPreviousInternal() }
    }

    val isWarmReady: Boolean
        get() = warmReady

    /**
     * Original [n0] + warm completion Runnable:
     * load MusicSet(-9) / PLAYING_QUEUE, restore cursor from prefs, set warmReady,
     * optionally prep without play, then honor pendingPlay via [y0].
     */
    private suspend fun warmQueueFromPlayingPlaylist() {
        // Original n0: load MusicSet(-9) + last progress on BG; no ShakeDetector / UI initializer.
        val queue = withContext(dispatchers.io) {
            playbackQueueRepo.getQueue()
        }
        val progress = withContext(dispatchers.io) {
            playbackStatePreferenceStore.getMusicProgress()
        }

        withContext(dispatchers.main) {
            if (queue.isNotEmpty()) {
                val index = queue.indexOfFirst { it.id == progress.trackId }
                    .takeIf { it >= 0 }
                    ?: 0
                queueManager.setQueue(queue, index)
                val track = queueManager.currentTrack
                runtimeStateStore.setState(
                    MusicPlaybackState(
                        initialized = true,
                        queue = queueManager.queue,
                        currentIndex = queueManager.currentIndex,
                        currentTrack = track,
                        isPlaying = false,
                        positionMs = progress.progressMs.toLong().coerceAtLeast(0L),
                        durationMs = track?.duration?.toLong() ?: 0L,
                        audioSessionId = -1
                    )
                )
                // Original warm F(b0(), 24) uses prepareDelay — MediaPlayer stays null until play.
                // Do not create ExoPlayer here.
            } else {
                runtimeStateStore.initializeIfNeeded()
            }

            warmReady = true
            if (pendingPlay) {
                pendingPlay = false
                scope.launch { playInternal() }
            }
        }
    }

    /**
     * Original [y0] body after warm:
     * [Q] cancel delayed play, [S] fill empty with all tracks, then BassPlayer.F / resume.
     */
    private suspend fun playInternal() {
        pendingPlay = false

        // S(): if queue empty → E0(null) all tracks → U0(list, 0)
        if (queueManager.queue.isEmpty()) {
            val allTracks = withContext(dispatchers.io) {
                runCatching { musicDao.getVisibleTracksSnapshotForPlayback() }
                    .getOrDefault(emptyList())
            }
            if (allTracks.isEmpty()) {
                return
            }
            withContext(dispatchers.main) {
                queueManager.setQueue(allTracks, 0)
                publishQueueState(isPlaying = false)
            }
        }

        val track = queueManager.currentTrack ?: return
        if (TextUtils.isEmpty(track.data)) {
            return
        }

        withContext(dispatchers.main) {
            val components = processPlayerHolder.getOrCreate()
            val player = components.player
            playbackFadeController.bind(player)

            val controller = playerQueueController?.also { it.replacePlayer(player) }
                ?: PlayerQueueController(
                    player = player,
                    queueProvider = { queueManager.queue }
                ).also { playerQueueController = it }

            val trackMediaId = track.toQueueMediaId()
            val samePrepared = player.mediaItemCount == 1 &&
                player.currentMediaItem?.mediaId == trackMediaId &&
                player.playbackState != androidx.media3.common.Player.STATE_IDLE

            if (samePrepared) {
                // Original: BassPlayer.t() && same music → x() resume with w6.b.l fade-in
                playbackFadeController.fadeInAndPlay(player)
            } else {
                // Original: F(music, 1) load + play — fade in after prepare
                val positionMs = runtimeStateStore.state.value.positionMs.coerceAtLeast(0L)
                controller.setPlayerQueue(
                    queue = queueManager.queue,
                    startIndex = queueManager.currentIndex,
                    startPositionMs = positionMs,
                    playWhenReady = false
                )
                playbackFadeController.fadeInAndPlay(player)
            }

            publishQueueState(isPlaying = true)
            // Original [c(true)] → [u0]: start notification service AFTER play-state true
            startNotificationService()
        }
    }

    /**
     * Same index policy as [QueueActionController.playNext] / [PlaybackModeResolver]:
     * Shuffle-all → random other index (original shuffle next), not queue+1.
     */
    private suspend fun playNextInternal() {
        val queue = queueManager.queue
        if (queue.isEmpty()) return

        val nextIndex = playbackModeResolver.resolveNextIndex(
            queueSize = queue.size,
            currentIndex = queueManager.currentIndex,
            fromAutoTransition = false
        ) ?: return

        playResolvedIndex(nextIndex)
    }

    private suspend fun playPreviousInternal() {
        val queue = queueManager.queue
        if (queue.isEmpty()) return

        val player = processPlayerHolder.playerOrNull()
        // Same restart window as service QueueMutationCallbacks (5s).
        if (player != null && player.currentPosition > PREVIOUS_RESTART_WINDOW_MS) {
            player.seekTo(0L)
            return
        }

        val previousIndex = playbackModeResolver.resolvePreviousIndex(
            queueSize = queue.size,
            currentIndex = queueManager.currentIndex,
            shouldRestartCurrent = false
        ) ?: return

        playResolvedIndex(previousIndex)
    }

    private suspend fun playResolvedIndex(index: Int) {
        if (index !in queueManager.queue.indices) return

        queueManager.updateCurrentIndex(index)
        val components = processPlayerHolder.getOrCreate()
        val player = components.player
        val controller = playerQueueController?.also { it.replacePlayer(player) }
            ?: PlayerQueueController(
                player = player,
                queueProvider = { queueManager.queue }
            ).also { playerQueueController = it }

        controller.setPlayerQueue(
            queue = queueManager.queue,
            startIndex = index,
            startPositionMs = 0L,
            playWhenReady = true
        )
        publishQueueState(isPlaying = true)
        startNotificationService()
    }

    private fun publishQueueState(isPlaying: Boolean) {
        val track = queueManager.currentTrack
        val player = processPlayerHolder.playerOrNull()
        runtimeStateStore.setState(
            MusicPlaybackState(
                initialized = true,
                queue = queueManager.queue,
                currentIndex = queueManager.currentIndex,
                currentTrack = track,
                isPlaying = isPlaying,
                positionMs = player?.currentPosition?.coerceAtLeast(0L)
                    ?: runtimeStateStore.state.value.positionMs,
                durationMs = player?.duration?.takeIf { it > 0 }?.coerceAtLeast(0L)
                    ?: track?.duration?.toLong()
                    ?: 0L,
                audioSessionId = player?.audioSessionId ?: -1
            )
        )
    }

    private fun publishRuntimeIsPlaying(isPlaying: Boolean) {
        val current = runtimeStateStore.state.value
        runtimeStateStore.setState(current.copy(isPlaying = isPlaying))
    }

    /**
     * Original [u0]: MusicPlayService.c(context, "ACTION_UPDATE_NOTIFICATION").
     * Dream: start FG service for notification/MediaSession only — players already exist.
     */
    private fun startNotificationService() {
        mainHandler.post {
            PlaybackServiceStarter.startAction(
                context,
                PlaybackServiceActions.ACTION_REFRESH_NOTIFICATION_STYLE
            )
        }
    }

    private companion object {
        /** Same as service [PREVIOUS_RESTART_WINDOW_MS]. */
        private const val PREVIOUS_RESTART_WINDOW_MS = 5_000L
    }
}
