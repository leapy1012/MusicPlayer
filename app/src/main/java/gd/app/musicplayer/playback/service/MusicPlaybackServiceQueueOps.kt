package gd.app.musicplayer.playback.service

import gd.app.musicplayer.domain.model.Music
import kotlinx.coroutines.launch
import gd.app.musicplayer.playback.shutdown.ShutdownOptions
import gd.app.musicplayer.playback.state.PublishReason

internal fun MusicPlaybackService.updateStopAfterCurrentTrackMode(enabled: Boolean) {
    stopAfterCurrentTrack = enabled
    if (isPlayerInitialized()) {
        player.pauseAtEndOfMediaItems = enabled
    }
}

internal fun MusicPlaybackService.handlePlayFromQueue(
    incomingQueue: List<Music>,
    incomingIndex: Int
) {
    launchPlaybackRequest {

        if (!isQueueActionControllerInitialized()) return@launchPlaybackRequest

        queueActionController.playFromQueue(
            incomingQueue = incomingQueue,
            incomingIndex = incomingIndex
        )
    }
}

internal fun MusicPlaybackService.handleEnqueue(incomingQueue: List<Music>) {
    val request = playbackRequests.token()
    serviceScope.launch {
        ensurePlaybackRestored()
        if (!playbackRequests.isUnchanged(request)) return@launch

        if (!isQueueActionControllerInitialized()) return@launch

        queueActionController.enqueue(incomingQueue)
    }
}

internal fun MusicPlaybackService.handlePlayNextQueue(incomingQueue: List<Music>) {
    val request = playbackRequests.token()
    serviceScope.launch {
        ensurePlaybackRestored()
        if (!playbackRequests.isUnchanged(request)) return@launch

        if (!isQueueActionControllerInitialized()) return@launch

        queueActionController.playNextQueue(incomingQueue)
    }
}

internal fun MusicPlaybackService.removeQueueItem(index: Int) {
    val request = playbackRequests.token()
    serviceScope.launch {
        ensurePlaybackRestored()
        if (!playbackRequests.isUnchanged(request)) return@launch

        if (!isQueueActionControllerInitialized()) return@launch

        queueActionController.removeQueueItem(index)
    }
}

internal fun MusicPlaybackService.moveQueueItem(
    fromIndex: Int,
    toIndex: Int
) {
    val request = playbackRequests.token()
    serviceScope.launch {
        ensurePlaybackRestored()
        if (!playbackRequests.isUnchanged(request)) return@launch

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
    val request = playbackRequests.token()
    serviceScope.launch {
        ensurePlaybackRestored()
        if (!playbackRequests.isUnchanged(request)) return@launch

        if (!isQueueActionControllerInitialized()) return@launch

        queueActionController.replaceQueue(
            newQueue = newQueue,
            requestedIndex = requestedIndex
        )
    }
}

internal fun MusicPlaybackService.playIndexFromCommand(index: Int) {
    launchPlaybackRequest { playIndex(index, playWhenReady = true) }
}

internal fun MusicPlaybackService.togglePlayPause() {
    if (resumeJob?.isActive == true || isEffectivelyPlaying()) pausePlayback() else resumePlayback()
}

internal fun MusicPlaybackService.resumePlayback() {
    launchPlaybackRequest {
        if (ensurePlaybackReady() && playbackRequests.wantsPlayback) resumePlaybackInternal()
    }
}

internal fun MusicPlaybackService.pausePlayback(
    withFade: Boolean = playbackFadeController.isPlayPauseFadeEnabled()
) {
    cancelPendingPlayback()
    pausePlaybackInternal(withFade)
}

internal fun MusicPlaybackService.pauseAndPersistForNotificationClose() {
    cancelPendingPlayback()
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
    if (playbackShuttingDown) return
    cancelPendingPlayback()
    if (isShutdownCoordinatorInitialized()) {
        try { shutdownCoordinator.shutdown(options) }
        finally {
            if (!options.stopService) {
                playbackShuttingDown = false
                if (isProgressTickerInitialized()) progressTicker.start()
            }
        }
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


