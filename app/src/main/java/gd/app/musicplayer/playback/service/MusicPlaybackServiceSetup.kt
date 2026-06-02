package gd.app.musicplayer.playback.service

import android.content.Intent
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.AppForegroundTracker
import gd.app.musicplayer.playback.desktop.DesktopLyricsOverlayController
import gd.app.musicplayer.playback.notification.NotificationCloseCallbacks
import gd.app.musicplayer.playback.notification.NotificationCloseController
import gd.app.musicplayer.playback.shutdown.ShutdownCallbacks
import gd.app.musicplayer.playback.shutdown.ShutdownController
import gd.app.musicplayer.playback.shutdown.ShutdownOptions
import gd.app.musicplayer.playback.state.PlaybackSnapshot
import gd.app.musicplayer.playback.state.PublishReason
import gd.app.musicplayer.playback.statusbar.StatusBarLyricsOverlayController
import kotlinx.coroutines.launch


internal fun MusicPlaybackService.configureControllers() {
    measureSetupPhase("core") {
        playbackSessionOrNull()?.setupCoreControllers()
    }
    measureSetupPhase("session_state_favorites") {
        playbackSessionOrNull()?.setupSessionStateAndFavorites()
    }
    measureSetupPhase("queue_runtime") {
        playbackSessionOrNull()?.setupQueueAndRuntimeControllers()
    }
    measureSetupPhase("shutdown") { setupShutdownControllers() }
    measureSetupPhase("deferred_overlays") { initializeNonCriticalOverlayControllersDeferred() }
}

private inline fun MusicPlaybackService.measureSetupPhase(
    phase: String,
    block: () -> Unit
) {
    val startedAt = SystemClock.elapsedRealtime()
    block()
    val elapsedMs = SystemClock.elapsedRealtime() - startedAt
    Log.d(CONTROLLER_SETUP_TIMING_TAG, "phase=$phase elapsedMs=$elapsedMs")
}

private fun MusicPlaybackService.setupShutdownControllers() {
    shutdownController = ShutdownController(
        callbacks = object : ShutdownCallbacks {

            override fun capturePlaybackSnapshot(): PlaybackSnapshot {
                return this@setupShutdownControllers.capturePlaybackSnapshot()
            }

            override fun persistForSnapshotPolicy(
                snapshot: PlaybackSnapshot,
                persistQueue: Boolean
            ) {
                this@setupShutdownControllers.persistFor(
                    PersistenceEvent.PlaybackSnapshot(
                        snapshot = snapshot,
                        persistQueue = persistQueue
                    )
                )
            }

            override fun resetPlaybackStatistics() {
                playbackStatsTracker.reset()
            }

            override fun resetTimedTransition() {
                resetTimedTransitionState()
            }

            override fun cancelVolumeFade() {
                volumeFaderOrNull()?.cancel()
            }

            override fun stopAndClearPlayer() {
                playerOrNull()?.run {
                    pause()
                    stop()
                    clearMediaItems()
                }
            }

            override fun abandonAudioFocus() {
                audioFocusControllerOrNull()?.abandon()
            }

            override fun clearArtworkState() {
                this@setupShutdownControllers.clearArtworkState()
            }

            override fun clearQueueState() {
                this@setupShutdownControllers.clearQueueState()
            }

            override fun clearPersistedPlaybackState() {
                this@setupShutdownControllers.clearPersistedPlaybackState()
            }

            override fun clearPersistedQueue() {
                clearPersistedQueueAsync()
            }

            override fun clearNotificationSession() {
                notificationSessionBridgeOrNull()?.run {
                    clearMetadata()
                    clearQueue()
                }
            }

            override fun markRestoreEmpty() {
                restoreManager.markEmpty()
            }

            override fun resetRuntimeState() {
                stateOrchestrator.resetRuntimeState()
            }

            override fun publishStateAfterShutdown(snapshot: PlaybackSnapshot) {
                this@setupShutdownControllers.publishStateAfterShutdown(snapshot)
            }

            override fun removeNotification() {
                notificationControllerOrNull()?.stopForegroundAndRemove()
            }

            override fun onServiceShouldStop() {
                MusicPlaybackService.isRunning = false
                stopSelf()
            }
        }
    )

    shutdownCoordinator = playbackSessionOrNull()?.createShutdownCoordinator()
        ?: return

    notificationCloseController = NotificationCloseController(
        callbacks = object : NotificationCloseCallbacks {

            override fun capturePlaybackSnapshot(): PlaybackSnapshot {
                return this@setupShutdownControllers.capturePlaybackSnapshot()
            }

            override fun setNotificationDismissedByUser(dismissed: Boolean) {
                sessionFlags.notificationDismissedByUser = dismissed
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

            override fun persistForSnapshotPolicy(
                snapshot: PlaybackSnapshot,
                persistQueue: Boolean
            ) {
                this@setupShutdownControllers.persistFor(
                    PersistenceEvent.PlaybackSnapshot(
                        snapshot = snapshot,
                        persistQueue = persistQueue
                    )
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

private fun MusicPlaybackService.initializeNonCriticalOverlayControllersDeferred() {
    serviceScope.launch {
        desktopLyricsController = createDesktopLyricsOverlayController()
        statusBarLyricsController = createStatusBarLyricsOverlayController()

        desktopLyricsController.renderPreference(runtimeCacheState.latestDesktopLyricPreference)
        desktopLyricsController.renderAppForeground(AppForegroundTracker.isForeground.value)
        desktopLyricsController.renderPlaybackState(playbackRuntimeStateStore.state.value)
        statusBarLyricsController.renderPlaybackState(playbackRuntimeStateStore.state.value)
    }
}

private fun MusicPlaybackService.createDesktopLyricsOverlayController(): DesktopLyricsOverlayController {
    return DesktopLyricsOverlayController(
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
                this@createDesktopLyricsOverlayController.togglePlayPause()
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
}

private fun MusicPlaybackService.createStatusBarLyricsOverlayController(): StatusBarLyricsOverlayController {
    return StatusBarLyricsOverlayController(
        context = applicationContext,
        scope = serviceScope,
        trackLyricPreferenceStore = trackLyricPreferenceStore,
        callbacks = object : StatusBarLyricsOverlayController.Callbacks {
            override fun togglePlayPause() {
                this@createStatusBarLyricsOverlayController.togglePlayPause()
            }
        }
    )
}

private const val CONTROLLER_SETUP_TIMING_TAG = "PlaybackSetupTiming"
