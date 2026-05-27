package gd.app.musicplayer.playback.service

import android.content.res.Configuration
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.state.PublishReason
import gd.app.musicplayer.playback.shutdown.ShutdownOptions
import gd.app.musicplayer.playback.timer.SleepTimerManager
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal fun MusicPlaybackService.handleEmptyStartCommand(
    startId: Int
): Int {
    if (isLifecycleControllerInitialized()) {
        return lifecycleController.handleEmptyStartCommand(startId)
    }

    if (!isEffectivelyPlaying() && !hasCurrentQueueItem()) {
        setServiceRunning(false)
        stopSelfForStartId(startId)
        return START_NOT_STICKY
    }

    publishPlaybackState(
        reason = PublishReason.UserAction,
        forceNotification = false
    )

    return START_STICKY
}

internal fun MusicPlaybackService.handleAppTaskRemoved() {
    if (isEffectivelyPlaying()) {
        handleTaskRemovedWhilePlaying()
        return
    }

    setServiceRunning(false)
    shutdownPlayback(ShutdownOptions.TaskRemovedWhenPaused)
}

internal fun MusicPlaybackService.promoteToForegroundForPlaybackCommand() {
    notificationDismissedByUser = false

    if (isNotificationControllerInitialized()) {
        notificationController.ensureForegroundStarted()
    }
}

internal suspend fun MusicPlaybackService.refreshNotificationStyleFromCommand() {
    if (!isNotificationControllerInitialized()) return
    if (!isProgressTickerInitialized()) return

    latestSettingPreferences =
        settingPreferencesDataStore.observeSettingPreferences().first()

    notificationController.refreshStyle { delayMs, block ->
        progressTicker.postDelayed(
            block = block,
            delayMs = delayMs
        )
    }
}

internal fun MusicPlaybackService.cyclePlaybackModeFromCommand() {
    if (isPlaybackModeResolverInitialized()) {
        playbackModeResolver.cyclePlaybackMode()
    }
}

internal fun MusicPlaybackService.setPlaybackModeFromCommand(
    mode: Int
) {
    if (isPlaybackModeResolverInitialized()) {
        playbackModeResolver.setPlaybackMode(mode)
    }
}

internal fun MusicPlaybackService.applyPlaybackTuningFromCommand() {
    if (isPlaybackTuningControllerInitialized()) {
        playbackTuningController.applyPlaybackTuning()
    }
}

internal fun MusicPlaybackService.setDesktopLyricsLockedFromCommand(
    locked: Boolean
) {
    if (!isServiceScopeInitialized()) return

    serviceScope.launch {
        desktopLyricPreferenceStore.setLocked(locked)
    }
}

internal fun MusicPlaybackService.currentMusicForCommand(): Music? {
    return queue.getOrNull(currentIndex)
}

internal fun MusicPlaybackService.hasCurrentQueueItem(): Boolean {
    return currentIndex in queue.indices
}

internal fun MusicPlaybackService.setServiceRunning(
    running: Boolean
) {
    MusicPlaybackService.isRunning = running
}

internal fun MusicPlaybackService.stopSelfForStartId(
    startId: Int
) {
    stopSelf(startId)
}

internal fun MusicPlaybackService.createNotificationChannelFromLifecycle() {
    if (isNotificationControllerInitialized()) {
        notificationController.createNotificationChannel()
    }
}

internal fun MusicPlaybackService.startProgressTickerFromLifecycle() {
    if (isProgressTickerInitialized()) {
        progressTicker.start()
    }
}

internal fun MusicPlaybackService.publishPlaybackStateFromLifecycle(
    reason: PublishReason,
    forceNotification: Boolean
) {
    publishPlaybackState(
        reason = reason,
        forceNotification = forceNotification
    )
}

internal fun MusicPlaybackService.updateNightModeFromConfiguration(
    configuration: Configuration
) {
    val newNightMode = isNightMode(configuration)

    if (newNightMode == isNightMode) return

    isNightMode = newNightMode
    updateNotification(force = true)
}

internal fun MusicPlaybackService.updateNotificationFromLifecycle(
    force: Boolean
) {
    updateNotification(force = force)
}

internal fun MusicPlaybackService.notifyOverlayConfigurationChanged() {
    if (isDesktopLyricsControllerInitialized()) {
        desktopLyricsController.onConfigurationChanged()
    }

    if (isStatusBarLyricsControllerInitialized()) {
        statusBarLyricsController.onConfigurationChanged()
    }
}

internal fun MusicPlaybackService.handleTaskRemovedWhilePlaying() {
    publishPlaybackState(
        reason = PublishReason.UserAction,
        forceNotification = true
    )
}

internal fun MusicPlaybackService.releasePlaybackResources() {
    releaseTickersAndJobs()
    releaseObservers()
    releaseFaders()
    releaseArtwork()
    releaseMediaSessionResources()
    releaseLyricsOverlays()
    releaseAudioResources()
    releasePlayers()
    releaseServiceScope()

    SleepTimerManager.setPlaybackActiveProvider(null)
    setServiceRunning(false)
}

private fun MusicPlaybackService.releaseTickersAndJobs() {
    if (isProgressTickerInitialized()) {
        progressTicker.shutdown()
    }

    resumeJob?.cancel()
    resumeJob = null

    defaultQueueRestoreJob?.cancel()
    defaultQueueRestoreJob = null
    defaultTracksObserverJob?.cancel()
    defaultTracksObserverJob = null

    pendingResumeAfterDefaultQueue = false
    cachedDefaultTracks = emptyList()
    cachedPlayableDefaultTracks = emptyList()
    deferForcedStartupUiUpdates = false
    pendingQueueSessionSyncAfterStartupPlay = false
}

private fun MusicPlaybackService.releaseObservers() {
    if (isArtworkControllerInitialized()) {
        artworkController.stopObserving()
    }

    if (isFavoriteControllerInitialized()) {
        favoriteController.stopObserving()
    }
}

private fun MusicPlaybackService.releaseFaders() {
    if (isVolumeFaderInitialized()) {
        volumeFader.cancel()
    }

    if (isCrossfadeVolumeFaderInitialized()) {
        crossfadeVolumeFader.cancel()
    }
}

private fun MusicPlaybackService.releaseArtwork() {
    when {
        isArtworkControllerInitialized() -> {
            artworkController.clear()
        }

        isArtworkLoaderInitialized() -> {
            artworkLoader.clear()
        }
    }
}

private fun MusicPlaybackService.releaseMediaSessionResources() {
    media3Session?.release()
    media3Session = null

    if (isNotificationSessionBridgeInitialized()) {
        notificationSessionBridge.release()
    }
}

private fun MusicPlaybackService.releaseLyricsOverlays() {
    if (isDesktopLyricsControllerInitialized()) {
        desktopLyricsController.destroy()
    }

    if (isStatusBarLyricsControllerInitialized()) {
        statusBarLyricsController.destroy()
    }
}

private fun MusicPlaybackService.releaseAudioResources() {
    if (isAudioEffectsManagerInitialized()) {
        audioEffectsManager.release()
    }

    if (isTimedTransitionControllerInitialized()) {
        timedTransitionController.release()
    }

    if (isAudioFocusControllerInitialized()) {
        audioFocusController.abandon()
    }

    unregisterScreenOffReceiver()
}

private fun MusicPlaybackService.releasePlayers() {
    if (isPlaybackEngineInitialized()) {
        playbackEngine.release(
            player = if (isPlayerInitialized()) player else null,
            crossfadePlayer = if (isCrossfadePlayerInitialized()) crossfadePlayer else null,
            playerEventHandler = if (isPlayerEventHandlerInitialized()) {
                playerEventHandler
            } else {
                null
            }
        )
        return
    }

    releasePlayersDirectly()
}

private fun MusicPlaybackService.releasePlayersDirectly() {
    if (isPlayerInitialized()) {
        if (isPlayerEventHandlerInitialized()) {
            player.removeListener(playerEventHandler)
        }

        player.release()
    }

    if (isCrossfadePlayerInitialized()) {
        crossfadePlayer.release()
    }
}

private fun MusicPlaybackService.releaseServiceScope() {
    if (isServiceScopeInitialized()) {
        serviceScope.cancel()
    }
}
