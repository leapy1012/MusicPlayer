package gd.app.musicplayer.playback.service

import gd.app.musicplayer.core.common.extension.toMediaItemOrNull
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.queue.QueueActionController
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import gd.app.musicplayer.playback.shutdown.ShutdownOptions
import gd.app.musicplayer.playback.state.PublishReason

private inline fun MusicPlaybackService.launchAfterPlaybackRestore(
    operation: String,
    crossinline block: suspend MusicPlaybackService.() -> Unit
) {
    serviceScope.launch {
        measurePlaybackRuntimePhase("restore_then_$operation") {
            ensurePlaybackRestored()
            block()
        }
    }
}

private inline fun MusicPlaybackService.withQueueActionController(
    block: (QueueActionController) -> Unit
) {
    queueActionControllerOrNull()?.let(block)
}

private inline fun MusicPlaybackService.runQueueAction(
    operation: String,
    phase: String,
    crossinline block: QueueActionController.() -> Unit
) {
    launchAfterPlaybackRestore(operation = operation) {
        withQueueActionController { controller ->
            measurePlaybackRuntimePhase(phase) {
                controller.block()
            }
        }
    }
}

internal fun MusicPlaybackService.updateStopAfterCurrentTrackMode(enabled: Boolean) {
    sessionFlags.stopAfterCurrentTrack = enabled
    playerOrNull()?.pauseAtEndOfMediaItems = enabled
}

internal fun MusicPlaybackService.resumeWithDefaultQueue() {
    if (jobState.defaultQueueRestoreJob?.isActive == true) return

    jobState.defaultQueueRestoreJob = serviceScope.launch {
        val playableTracks = startupState.cachedPlayableDefaultTracks.takeIf { it.isNotEmpty() }
            ?: startupState.cachedDefaultTracks
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
            startupState.pendingResumeAfterDefaultQueue = false
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
        startupState.pendingQueueSessionSyncAfterStartupPlay = true

        val shouldAutoResume = startupState.pendingResumeAfterDefaultQueue
        startupState.pendingResumeAfterDefaultQueue = false

        if (!audioFocusController.request()) {
            return@launch
        }

        startupState.deferForcedStartupUiUpdates = true

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
    runQueueAction(
        operation = "play_from_queue",
        phase = "queue_action_play_from_queue"
    ) {
        playFromQueue(
            incomingQueue = incomingQueue,
            incomingIndex = incomingIndex
        )
    }
}

internal fun MusicPlaybackService.handleEnqueue(incomingQueue: List<Music>) {
    runQueueAction(
        operation = "enqueue",
        phase = "queue_action_enqueue"
    ) {
        enqueue(incomingQueue)
    }
}

internal fun MusicPlaybackService.handlePlayNextQueue(incomingQueue: List<Music>) {
    runQueueAction(
        operation = "play_next_queue",
        phase = "queue_action_play_next_queue"
    ) {
        playNextQueue(incomingQueue)
    }
}

internal fun MusicPlaybackService.removeQueueItem(index: Int) {
    runQueueAction(
        operation = "remove_queue_item",
        phase = "queue_action_remove_item"
    ) {
        removeQueueItem(index)
    }
}

internal fun MusicPlaybackService.moveQueueItem(
    fromIndex: Int,
    toIndex: Int
) {
    runQueueAction(
        operation = "move_queue_item",
        phase = "queue_action_move_item"
    ) {
        moveQueueItem(
            fromIndex = fromIndex,
            toIndex = toIndex
        )
    }
}

internal fun MusicPlaybackService.replaceQueue(
    newQueue: List<Music>,
    requestedIndex: Int
) {
    runQueueAction(
        operation = "replace_queue",
        phase = "queue_action_replace_queue"
    ) {
        replaceQueue(
            newQueue = newQueue,
            requestedIndex = requestedIndex
        )
    }
}

internal fun MusicPlaybackService.playIndexFromCommand(index: Int) {
    launchAfterPlaybackRestore(operation = "play_index_from_command") {
        measurePlaybackRuntimePhase("queue_action_play_index") {
            playIndex(
                index = index,
                playWhenReady = true
            )
        }
    }
}

internal fun MusicPlaybackService.togglePlayPause() {
    launchAfterPlaybackRestore(operation = "toggle_play_pause") {
        measurePlaybackRuntimePhase("toggle_play_pause") {
            if (isEffectivelyPlaying()) {
                pausePlaybackInternal()
            } else {
                resumePlaybackInternal()
            }
        }
    }
}

internal fun MusicPlaybackService.resumePlayback() {
    if (jobState.resumeJob?.isActive == true) return

    jobState.resumeJob = serviceScope.launch {
        measurePlaybackRuntimePhase("resume_playback") {
            ensurePlaybackRestored()
            resumePlaybackInternal()
        }
    }
}

internal fun MusicPlaybackService.pausePlayback(
    withFade: Boolean = playbackTuningController.isPlayPauseFadeEnabled()
) {
    pausePlaybackInternal(withFade)
}

internal fun MusicPlaybackService.pauseAndPersistForNotificationClose() {
    notificationCloseControllerOrNull()?.pauseAndCloseNotification()
}

internal fun MusicPlaybackService.exitService() {
    shutdownPlayback(ShutdownOptions.ExitService)
}

internal fun MusicPlaybackService.clearQueueKeepingNotification() {
    shutdownPlayback(ShutdownOptions.ClearQueueKeepingNotification)
}

internal fun MusicPlaybackService.shutdownPlayback(options: ShutdownOptions) {
    shutdownCoordinatorOrNull()?.shutdown(options)
}

internal fun MusicPlaybackService.handleNotificationFavoriteToggle() {
    toggleCurrentFavorite()
}

internal fun MusicPlaybackService.toggleCurrentFavorite() {
    launchAfterPlaybackRestore(operation = "toggle_favorite") {
        favoriteControllerOrNull()?.let { controller ->
            measurePlaybackRuntimePhase("favorite_toggle") {
                controller.toggleCurrent()
            }
        }
    }
}

internal fun MusicPlaybackService.setCurrentFavorite(isFavorite: Boolean) {
    launchAfterPlaybackRestore(operation = "set_favorite") {
        favoriteControllerOrNull()?.let { controller ->
            measurePlaybackRuntimePhase("favorite_set") {
                controller.setCurrentFavorite(isFavorite)
            }
        }
    }
}

internal fun MusicPlaybackService.refreshArtworkAndSession(force: Boolean = false) {
    withArtworkController {
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

    onQueueMetadataChanged()
}

internal fun MusicPlaybackService.updateEditedTracksMetadata(
    music: List<Music>
) {
    val changed = queueManager.updateTracksMetadata(music)
    if (!changed) return

    onQueueMetadataChanged()
}

internal fun MusicPlaybackService.filterPlayableQueue(queue: List<Music>): List<Music> {
    return playerQueueControllerOrNull()?.filterPlayable(queue) ?: queue
}

internal fun MusicPlaybackService.clearArtworkState() {
    artworkControllerOrNull()?.clear() ?: artworkLoaderOrNull()?.clear()
}
