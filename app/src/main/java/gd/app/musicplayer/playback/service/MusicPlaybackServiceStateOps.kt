package gd.app.musicplayer.playback.service

import android.os.SystemClock
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.feature.widget.provider.WidgetPlaybackSnapshot
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import gd.app.musicplayer.playback.restore.RestoreStatus
import gd.app.musicplayer.playback.state.PlaybackSnapshot
import gd.app.musicplayer.playback.state.PublishReason
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal fun MusicPlaybackService.handleProgressTick() {
    if (isTimedTransitionControllerInitialized()) {
        timedTransitionController.maybeHandleTimedTransition()
    }

    if (isPlayerInitialized() && player.isPlaying) {
        playbackStatsTracker.onProgress(
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = resolveCurrentSnapshotDurationMs(
                track = queue.getOrNull(currentIndex)
            )
        )
    }

    if (!shouldPublishProgressState()) return

    publishPlaybackState(
        reason = PublishReason.ProgressTick,
        forceNotification = false,
        forceWidgetUpdate = false
    )

    maybePersistSessionFromProgressTick()
}

internal fun MusicPlaybackService.restoreLastSessionIntoRuntimeStateIfNeeded() {
    serviceScope.launch {
        ensurePlaybackRestored()
    }
}

internal suspend fun MusicPlaybackService.ensurePlaybackRestored() {
    restoreManager.ensureRestored(
        onRestored = { restored ->
            handlePlaybackRestored(restored)
        },
        onEmpty = {
            handlePlaybackRestoreEmpty()
        },
        onFailed = {
            handlePlaybackRestoreFailed()
        }
    )
}

private fun MusicPlaybackService.handlePlaybackRestored(
    restored: gd.app.musicplayer.playback.restore.RestoredPlayback
) {
    val restoredTrackId = restored.queue.getOrNull(restored.index)?.id

    setQueueState(
        newQueue = restored.queue,
        requestedIndex = restored.index
    )

    val restoredIndex = currentIndex
    val restoredPositionMs = if (queue.getOrNull(restoredIndex)?.id == restoredTrackId) {
        restored.positionMs
    } else {
        0L
    }

    pendingRestoreTrackId = restoredTrackId

    prepareRestoredPlayerState(
        index = restoredIndex,
        positionMs = restoredPositionMs
    )

    syncControllersAfterQueueRestore(forceArtwork = true)

    stateOrchestrator.publishRestored(
        index = restoredIndex,
        positionMs = restoredPositionMs,
        queue = queue
    )
}

private fun MusicPlaybackService.handlePlaybackRestoreEmpty() {
    pendingRestoreTrackId = null
    clearQueueState()

    syncControllersAfterQueueRestore(forceArtwork = false)

    publishPlaybackState(
        reason = PublishReason.Restore,
        forceNotification = true
    )
}

private fun MusicPlaybackService.handlePlaybackRestoreFailed() {
    pendingRestoreTrackId = null

    publishPlaybackState(
        reason = PublishReason.Restore,
        forceNotification = true
    )
}

internal fun MusicPlaybackService.syncControllersAfterQueueRestore(
    forceArtwork: Boolean
) {
    if (isNotificationSessionBridgeInitialized()) {
        notificationSessionBridge.updateQueue()
    }

    refreshArtworkAndSession(force = forceArtwork)

    if (isNotificationSessionBridgeInitialized()) {
        notificationSessionBridge.updatePlaybackState()
    }

    updateNotification(force = true)
}

internal fun MusicPlaybackService.publishStateAfterShutdown(
    snapshot: PlaybackSnapshot? = null
) {
    if (isStateUpdateCoordinatorInitialized()) {
        stateUpdateCoordinator.publishStateAfterShutdown(snapshot)
        return
    }

    if (isStateOrchestratorInitialized()) {
        stateOrchestrator.publishStateAfterShutdown(
            snapshot = snapshot,
            notifyWidgets = false
        )
    }

    val widgetSnapshot = snapshot?.toWidgetPlaybackSnapshot()
        ?: playbackRuntimeStateStore.state.value.toWidgetPlaybackSnapshot()

    updateWidgetSnapshotBlocking(widgetSnapshot)
}

internal fun MusicPlaybackService.publishAllRuntimeState(
    forceNotification: Boolean = false
) {
    if (isStateUpdateCoordinatorInitialized()) {
        stateUpdateCoordinator.publishAllRuntimeState(
            forceNotification = forceNotification
        )
        return
    }

    if (isStateOrchestratorInitialized()) {
        stateOrchestrator.publishAllRuntimeState(
            forceNotification = forceNotification
        )
    }

    updateWidgetsFromRuntimeState()
}

internal fun MusicPlaybackService.publishPlaybackState(
    reason: PublishReason,
    forceNotification: Boolean = false,
    forceWidgetUpdate: Boolean = forceNotification
) {
    if (isStateUpdateCoordinatorInitialized()) {
        stateUpdateCoordinator.publishPlaybackState(
            reason = reason,
            forceNotification = forceNotification,
            forceWidgetUpdate = forceWidgetUpdate
        )
        return
    }

    if (isStateOrchestratorInitialized()) {
        stateOrchestrator.publishPlaybackState(
            reason = reason,
            forceNotification = forceNotification,
            forceWidgetUpdate = forceWidgetUpdate
        )
    }

    if (reason != PublishReason.ProgressTick || forceWidgetUpdate) {
        updateWidgetsFromRuntimeState()
    }
}

internal fun MusicPlaybackService.updateWidgetsFromRuntimeState() {
    if (!isPlaybackRuntimeStateStoreInitialized()) return

    updateWidgetSnapshot(
        playbackRuntimeStateStore.state.value.toWidgetPlaybackSnapshot()
    )
}

internal fun MusicPlaybackService.updateWidgetSnapshot(
    snapshot: WidgetPlaybackSnapshot
) {
    if (!isWidgetUpdateCoordinatorInitialized()) return
    if (!isServiceScopeInitialized()) return

    widgetUpdateJob?.cancel()
    widgetUpdateJob = serviceScope.launch {
        withContext(NonCancellable) {
            widgetUpdateCoordinator.updateAll(snapshot)
        }
    }
}

internal fun MusicPlaybackService.updateWidgetSnapshotBlocking(
    snapshot: WidgetPlaybackSnapshot
) {
    if (!isWidgetUpdateCoordinatorInitialized()) return

    widgetUpdateJob?.cancel()

    runBlocking {
        withContext(NonCancellable) {
            widgetUpdateCoordinator.updateAll(snapshot)
        }
    }
}

private fun MusicPlaybackService.toWidgetPlaybackSnapshot(
    state: MusicPlaybackState
): WidgetPlaybackSnapshot {
    return WidgetPlaybackSnapshot(
        queue = state.queue,
        currentTrack = state.currentTrack,
        currentIndex = state.currentIndex,
        positionMs = state.positionMs,
        isPlaying = state.isPlaying,
        playMode = latestSettingPreferences.playMode
    )
}

private fun MusicPlaybackService.toWidgetPlaybackSnapshot(
    snapshot: PlaybackSnapshot
): WidgetPlaybackSnapshot {
    return WidgetPlaybackSnapshot(
        queue = snapshot.queue,
        currentTrack = snapshot.currentTrack,
        currentIndex = snapshot.currentIndex,
        positionMs = snapshot.positionMs,
        isPlaying = false,
        playMode = latestSettingPreferences.playMode
    )
}

fun MusicPlaybackState.toWidgetPlaybackSnapshot(): WidgetPlaybackSnapshot {
    return WidgetPlaybackSnapshot(
        queue = queue,
        currentTrack = currentTrack,
        currentIndex = currentIndex,
        positionMs = positionMs,
        isPlaying = isPlaying,
        playMode = 0
    )
}

fun PlaybackSnapshot.toWidgetPlaybackSnapshot(): WidgetPlaybackSnapshot {
    return WidgetPlaybackSnapshot(
        queue = queue,
        currentTrack = currentTrack,
        currentIndex = currentIndex,
        positionMs = positionMs,
        isPlaying = false,
        playMode = 0
    )
}

internal fun MusicPlaybackService.setQueueState(
    newQueue: List<Music>,
    requestedIndex: Int
) {
    val playableQueue = filterPlayableQueue(newQueue)

    if (playableQueue.isEmpty()) {
        clearQueueState()
        return
    }

    queueManager.setQueue(
        newQueue = playableQueue,
        requestedIndex = remapRequestedIndex(
            originalQueue = newQueue,
            playableQueue = playableQueue,
            requestedIndex = requestedIndex
        )
    )

    resetTimedTransitionState()
}

internal fun MusicPlaybackService.clearQueueState() {
    queueManager.clear()
    resetTimedTransitionState()
}

internal fun MusicPlaybackService.shouldPublishProgressState(): Boolean {
    return when (restoreManager.status) {
        RestoreStatus.NotStarted -> {
            queue.isNotEmpty() || playbackRuntimeStateStore.isInitialized()
        }

        RestoreStatus.Restoring -> false

        RestoreStatus.Restored,
        RestoreStatus.Empty,
        RestoreStatus.Failed -> true
    }
}

internal fun MusicPlaybackService.maybePersistSessionFromProgressTick() {
    if (!isPlayerInitialized()) return
    if (!player.isPlaying) return

    val now = SystemClock.elapsedRealtime()

    if (now - lastSessionAutoSaveElapsedMs < SESSION_AUTO_SAVE_INTERVAL_MS) {
        return
    }

    lastSessionAutoSaveElapsedMs = now

    persistPlaybackSnapshotAsync(
        snapshot = capturePlaybackSnapshot(),
        persistQueue = false
    )
}

internal fun MusicPlaybackService.capturePlaybackSnapshot(): PlaybackSnapshot {
    return snapshotManager.capture(
        player = if (isPlayerInitialized()) player else null,
        queueState = queueManager.state
    )
}

internal fun MusicPlaybackService.persistPlaybackSnapshotBlocking(
    snapshot: PlaybackSnapshot,
    persistQueue: Boolean
) {
    runBlocking {
        snapshotManager.persist(
            snapshot = snapshot,
            persistQueue = persistQueue
        )
    }
}

internal fun MusicPlaybackService.persistPlaybackSnapshotAsync(
    snapshot: PlaybackSnapshot,
    persistQueue: Boolean
) {
    if (!isServiceScopeInitialized()) return

    serviceScope.launch {
        withContext(NonCancellable) {
            snapshotManager.persist(
                snapshot = snapshot,
                persistQueue = persistQueue
            )
        }
    }
}

internal fun MusicPlaybackService.persistSessionFromCurrentStateAsync() {
    persistPlaybackSnapshotAsync(
        snapshot = capturePlaybackSnapshot(),
        persistQueue = false
    )
}

internal fun MusicPlaybackService.persistCurrentTrackProgressAsync(
    positionMs: Int
) {
    val track = queueManager.currentTrack ?: return

    if (!isServiceScopeInitialized()) return

    serviceScope.launch {
        snapshotManager.persistProgress(
            track = track,
            positionMs = positionMs.toLong().coerceAtLeast(0L),
            currentIndex = queueManager.currentIndex
        )
    }
}

internal fun MusicPlaybackService.persistCurrentTrackProgressFromPlayerAsync() {
    val track = queueManager.currentTrack ?: return
    val positionMs = resolveCurrentSnapshotPositionMs()
    val currentIndex = queueManager.currentIndex

    if (!isServiceScopeInitialized()) return

    serviceScope.launch {
        snapshotManager.persistProgress(
            track = track,
            positionMs = positionMs,
            currentIndex = currentIndex
        )
    }
}

internal fun MusicPlaybackService.persistCurrentTrackProgressBlocking(
    track: Music?,
    positionMs: Long,
    currentIndex: Int
) {
    if (track == null) return

    runBlocking {
        snapshotManager.persistProgress(
            track = track,
            positionMs = positionMs,
            currentIndex = currentIndex
        )
    }
}

internal fun MusicPlaybackService.persistSessionFromCurrentState() {
    persistPlaybackSnapshotBlocking(
        snapshot = capturePlaybackSnapshot(),
        persistQueue = false
    )
}

internal fun MusicPlaybackService.clearPersistedPlaybackState() {
    runBlocking {
        snapshotManager.clearProgress()
    }
}

internal fun MusicPlaybackService.clearPersistedQueueBlocking() {
    runBlocking {
        withContext(dispatchers.io) {
            playbackQueueRepo.clearQueue()
        }
    }
}

internal fun MusicPlaybackService.resolveCurrentSnapshotPositionMs(): Long {
    return snapshotManager.resolveCurrentPositionMs(
        player = if (isPlayerInitialized()) player else null,
        queueState = queueManager.state
    )
}

internal fun MusicPlaybackService.resolveCurrentSnapshotDurationMs(
    track: Music?
): Long {
    return snapshotManager.resolveCurrentDurationMs(
        player = if (isPlayerInitialized()) player else null,
        track = track
    )
}