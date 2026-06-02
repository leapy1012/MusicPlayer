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
    lifecycleControllerOrNull()?.let { controller ->
        return controller.handleEmptyStartCommand(startId)
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
    sessionFlags.notificationDismissedByUser = false
    withNotificationController {
        notificationController.ensureForegroundStarted()
    }
}

internal suspend fun MusicPlaybackService.refreshNotificationStyleFromCommand() {
    val ticker = progressTickerOrNull() ?: return

    runtimeCacheState.latestSettingPreferences =
        settingPreferencesDataStore.observeSettingPreferences().first()

    withNotificationController {
        notificationController.refreshStyle { delayMs, block ->
            ticker.postDelayed(
                block = block,
                delayMs = delayMs
            )
        }
    }
}

internal fun MusicPlaybackService.cyclePlaybackModeFromCommand() {
    playbackModeResolverOrNull()?.cyclePlaybackMode()
}

internal fun MusicPlaybackService.setPlaybackModeFromCommand(
    mode: Int
) {
    playbackModeResolverOrNull()?.setPlaybackMode(mode)
}

internal fun MusicPlaybackService.applyPlaybackTuningFromCommand() {
    playbackTuningControllerOrNull()?.applyPlaybackTuning()
}

internal fun MusicPlaybackService.setDesktopLyricsLockedFromCommand(
    locked: Boolean
) {
    serviceScopeOrNull()?.launch {
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
    withNotificationController {
        notificationController.createNotificationChannel()
    }
}

internal fun MusicPlaybackService.startProgressTickerFromLifecycle() {
    withProgressTicker {
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
    val newNightMode = playbackSessionOrNull()?.isNightMode(configuration)
        ?: return

    if (newNightMode == runtimeCacheState.isNightMode) return

    runtimeCacheState.isNightMode = newNightMode
    updateNotification(force = true)
}

internal fun MusicPlaybackService.updateNotificationFromLifecycle(
    force: Boolean
) {
    updateNotification(force = force)
}

internal fun MusicPlaybackService.notifyOverlayConfigurationChanged() {
    withDesktopLyricsController {
        desktopLyricsController.onConfigurationChanged()
    }

    withStatusBarLyricsController {
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
    withProgressTicker {
        progressTicker.shutdown()
    }
    jobState.cancelAndClearAll()

    startupState.reset()
    media3TransportState.reset()
    sessionFlags.reset()
    runtimeCacheState.reset()
}

private fun MusicPlaybackService.releaseObservers() {
    withArtworkController {
        artworkController.stopObserving()
    }

    withFavoriteController {
        favoriteController.stopObserving()
    }
}

private fun MusicPlaybackService.releaseFaders() {
    volumeFaderOrNull()?.cancel()
    crossfadeVolumeFaderOrNull()?.cancel()
}

private fun MusicPlaybackService.releaseArtwork() {
    artworkControllerOrNull()?.clear() ?: artworkLoaderOrNull()?.clear()
}

private fun MusicPlaybackService.releaseMediaSessionResources() {
    media3Session?.release()
    media3Session = null

    withNotificationSessionBridge {
        notificationSessionBridge.release()
    }
}

private fun MusicPlaybackService.releaseLyricsOverlays() {
    withDesktopLyricsController {
        desktopLyricsController.destroy()
    }

    withStatusBarLyricsController {
        statusBarLyricsController.destroy()
    }
}

private fun MusicPlaybackService.releaseAudioResources() {
    audioEffectsManagerOrNull()?.release()
    timedTransitionControllerOrNull()?.release()
    audioFocusControllerOrNull()?.abandon()

    playbackSessionOrNull()?.unregisterScreenOffReceiver()
}

private fun MusicPlaybackService.releasePlayers() {
    val engine = playbackEngineOrNull()
    if (engine != null) {
        engine.release(
            player = playerOrNull(),
            crossfadePlayer = crossfadePlayerOrNull(),
            playerEventHandler = playerEventHandlerOrNull()
        )
        return
    }

    releasePlayersDirectly()
}

private fun MusicPlaybackService.releasePlayersDirectly() {
    playerOrNull()?.let { basePlayer ->
        playerEventHandlerOrNull()?.let(basePlayer::removeListener)
        basePlayer.release()
    }
    crossfadePlayerOrNull()?.release()
}

private fun MusicPlaybackService.releaseServiceScope() {
    serviceScopeOrNull()?.cancel()
}
