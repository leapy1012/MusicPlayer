package gd.app.musicplayer.playback.service

import gd.app.musicplayer.core.common.extension.toMediaItemOrNull
import gd.app.musicplayer.domain.model.Music
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import gd.app.musicplayer.playback.shutdown.ShutdownOptions
import gd.app.musicplayer.playback.state.PublishReason


internal fun MusicPlaybackService.updateStopAfterCurrentTrackMode(enabled: Boolean) {
    stopAfterCurrentTrack = enabled
    if (isPlayerInitialized()) {
        player.pauseAtEndOfMediaItems = enabled
    }
}

internal fun MusicPlaybackService.resumeWithDefaultQueue() {
    if (defaultQueueRestoreJob?.isActive == true) return

    defaultQueueRestoreJob = serviceScope.launch {
        val playableTracks = cachedPlayableDefaultTracks.takeIf { it.isNotEmpty() }
            ?: cachedDefaultTracks
                .takeIf { it.isNotEmpty() }
                ?.filter { music -> music.toMediaItemOrNull() != null }
            ?: withContext(dispatchers.io) {
                runCatching {
                    musicDao.getVisibleTracksSnapshotForPlayback()
                }.getOrDefault(emptyList())
            }.filter { music ->
                music.toMediaItemOrNull() != null
            }

        if (playableTracks.isEmpty()) {
            pendingResumeAfterDefaultQueue = false
            playbackRuntimeStateStore.initializeIfNeeded()
            publishPlaybackState(
                reason = PublishReason.Restore,
                forceNotification = true
            )
            return@launch
        }

        setQueueState(
            newQueue = playableTracks,
            requestedIndex = 0
        )

        restoreManager.markRestored()
        pendingQueueSessionSyncAfterStartupPlay = true

        val shouldAutoResume = pendingResumeAfterDefaultQueue
        pendingResumeAfterDefaultQueue = false

        if (!audioFocusController.request()) {
            return@launch
        }

        deferForcedStartupUiUpdates = true

        // Obfuscated parity: start playback path immediately from in-memory queue,
        // do not block play on upfront queue/progress persistence writes.
        setPlayerQueue(
            queue = queue,
            startIndex = 0,
            startPositionMs = 0L,
            playWhenReady = shouldAutoResume
        )

        applyVolumeForPlaybackStart(playWhenReady = shouldAutoResume)
        // Keep empty-queue play startup thin (obfuscated parity intent):
        // player callbacks publish/update state right after actual transition.
        publishPlaybackState(
            reason = PublishReason.Restore,
            forceNotification = false
        )
    }
}

internal fun MusicPlaybackService.handlePlayFromQueue(
    incomingQueue: List<Music>,
    incomingIndex: Int
) {
    serviceScope.launch {
        ensurePlaybackRestored()

        if (!isQueueActionControllerInitialized()) return@launch

        queueActionController.playFromQueue(
            incomingQueue = incomingQueue,
            incomingIndex = incomingIndex
        )
    }
}

internal fun MusicPlaybackService.handleEnqueue(incomingQueue: List<Music>) {
    serviceScope.launch {
        ensurePlaybackRestored()

        if (!isQueueActionControllerInitialized()) return@launch

        queueActionController.enqueue(incomingQueue)
    }
}

internal fun MusicPlaybackService.handlePlayNextQueue(incomingQueue: List<Music>) {
    serviceScope.launch {
        ensurePlaybackRestored()

        if (!isQueueActionControllerInitialized()) return@launch

        queueActionController.playNextQueue(incomingQueue)
    }
}

internal fun MusicPlaybackService.removeQueueItem(index: Int) {
    serviceScope.launch {
        ensurePlaybackRestored()

        if (!isQueueActionControllerInitialized()) return@launch

        queueActionController.removeQueueItem(index)
    }
}

internal fun MusicPlaybackService.moveQueueItem(
    fromIndex: Int,
    toIndex: Int
) {
    serviceScope.launch {
        ensurePlaybackRestored()

        if (!isQueueActionControllerInitialized()) return@launch

        queueActionController.moveQueueItem(
            fromIndex = fromIndex,
            toIndex = toIndex
        )
    }
}

internal fun MusicPlaybackService.replaceQueue(
    newQueue: List<Music>,
    requestedIndex: Int
) {
    serviceScope.launch {
        ensurePlaybackRestored()

        if (!isQueueActionControllerInitialized()) return@launch

        queueActionController.replaceQueue(
            newQueue = newQueue,
            requestedIndex = requestedIndex
        )
    }
}

internal fun MusicPlaybackService.playIndexFromCommand(index: Int) {
    serviceScope.launch {
        ensurePlaybackRestored()

        playIndex(
            index = index,
            playWhenReady = true
        )
    }
}

internal fun MusicPlaybackService.togglePlayPause() {
    serviceScope.launch {
        ensurePlaybackRestored()

        if (isEffectivelyPlaying()) {
            pausePlaybackInternal()
        } else {
            resumePlaybackInternal()
        }
    }
}

internal fun MusicPlaybackService.resumePlayback() {
    if (resumeJob?.isActive == true) return

    resumeJob = serviceScope.launch {
        ensurePlaybackRestored()
        resumePlaybackInternal()
    }
}

internal fun MusicPlaybackService.pausePlayback(
    withFade: Boolean = playbackTuningController.isPlayPauseFadeEnabled()
) {
    pausePlaybackInternal(withFade)
}

internal fun MusicPlaybackService.pauseAndPersistForNotificationClose() {
    if (!isNotificationCloseControllerInitialized()) return

    notificationCloseController.pauseAndCloseNotification()
}

internal fun MusicPlaybackService.exitService() {
    shutdownPlayback(ShutdownOptions.ExitService)
}

internal fun MusicPlaybackService.clearQueueKeepingNotification() {
    keepIdleNotification = true
    notificationDismissedByUser = false
    shutdownPlayback(ShutdownOptions.ClearQueueKeepingNotification)
    notificationController.stopForegroundDetached()
    notificationController.update(force = true)
}

internal fun MusicPlaybackService.shutdownPlayback(options: ShutdownOptions) {
    if (isShutdownCoordinatorInitialized()) {
        shutdownCoordinator.shutdown(options)
        return
    }

    keepIdleNotification = false
    updateStopAfterCurrentTrackMode(false)
    notificationDismissedByUser = false

    if (isShutdownControllerInitialized()) {
        shutdownController.shutdown(options)
    }
}

internal fun MusicPlaybackService.handleNotificationFavoriteToggle() {
    toggleCurrentFavorite()
}

internal fun MusicPlaybackService.toggleCurrentFavorite() {
    serviceScope.launch {
        ensurePlaybackRestored()

        if (isFavoriteControllerInitialized()) {
            favoriteController.toggleCurrent()
        }
    }
}

internal fun MusicPlaybackService.setCurrentFavorite(isFavorite: Boolean) {
    serviceScope.launch {
        ensurePlaybackRestored()

        if (isFavoriteControllerInitialized()) {
            favoriteController.setCurrentFavorite(isFavorite)
        }
    }
}

internal fun MusicPlaybackService.refreshArtworkAndSession(force: Boolean = false) {
    if (isArtworkControllerInitialized()) {
        artworkController.refresh(force = force)
        return
    }

    updateNotification(force = true)
}

internal fun MusicPlaybackService.updateEditedTrackMetadata(
    music: Music
) {
    val changed = queueManager.updateTrackMetadata(music)
    if (!changed) return

    notificationSessionBridge.updateQueue()
    refreshArtworkAndSession(force = true)
    persistSessionFromCurrentStateAsync()
    publishPlaybackState(
        reason = PublishReason.QueueChanged,
        forceNotification = true,
        forceWidgetUpdate = true
    )
}

internal fun MusicPlaybackService.updateEditedTracksMetadata(
    music: List<Music>
) {
    val changed = queueManager.updateTracksMetadata(music)
    if (!changed) return

    notificationSessionBridge.updateQueue()
    refreshArtworkAndSession(force = true)
    persistSessionFromCurrentStateAsync()
    publishPlaybackState(
        reason = PublishReason.QueueChanged,
        forceNotification = true,
        forceWidgetUpdate = true
    )
}

internal fun MusicPlaybackService.filterPlayableQueue(queue: List<Music>): List<Music> {
    return if (isPlayerQueueControllerInitialized()) {
        playerQueueController.filterPlayable(queue)
    } else {
        queue
    }
}

internal fun MusicPlaybackService.clearArtworkState() {
    if (isArtworkControllerInitialized()) {
        artworkController.clear()
    } else if (isArtworkLoaderInitialized()) {
        artworkLoader.clear()
    }
}


