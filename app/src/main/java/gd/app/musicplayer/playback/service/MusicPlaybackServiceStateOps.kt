package gd.app.musicplayer.playback.service

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.feature.widget.provider.WidgetPlaybackSnapshot
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import gd.app.musicplayer.playback.restore.RestoreStatus
import gd.app.musicplayer.playback.state.PlaybackSnapshot as StatePlaybackSnapshot
import gd.app.musicplayer.playback.state.PublishReason
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface PersistenceEvent {
    data class PlaybackSnapshot(
        val snapshot: StatePlaybackSnapshot,
        val persistQueue: Boolean
    ) : PersistenceEvent

    data class TrackProgress(
        val track: Music?,
        val positionMs: Long,
        val currentIndex: Int
    ) : PersistenceEvent

    data class QueueMutation(
        val snapshot: StatePlaybackSnapshot
    ) : PersistenceEvent

    data class Pause(
        val snapshot: StatePlaybackSnapshot
    ) : PersistenceEvent

    data class Transition(
        val snapshot: StatePlaybackSnapshot
    ) : PersistenceEvent

    data class Seek(
        val snapshot: StatePlaybackSnapshot
    ) : PersistenceEvent

    data class Stop(
        val snapshot: StatePlaybackSnapshot,
        val clearQueue: Boolean
    ) : PersistenceEvent
}

internal fun MusicPlaybackService.handleProgressTick() {
    timedTransitionControllerOrNull()?.maybeHandleTimedTransition()

    playerOrNull()?.takeIf { it.isPlaying }?.let { basePlayer ->
        playbackStatsTracker.onProgress(
            positionMs = basePlayer.currentPosition.coerceAtLeast(0L),
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
    measurePlaybackRuntimePhase("ensure_playback_restored") {
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

    runtimeCacheState.pendingRestoreTrackId = restoredTrackId

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
    runtimeCacheState.pendingRestoreTrackId = null
    clearQueueState()

    syncControllersAfterQueueRestore(forceArtwork = false)

    publishPlaybackState(
        reason = PublishReason.Restore,
        forceNotification = true
    )
}

private fun MusicPlaybackService.handlePlaybackRestoreFailed() {
    runtimeCacheState.pendingRestoreTrackId = null

    publishPlaybackState(
        reason = PublishReason.Restore,
        forceNotification = true
    )
}

internal fun MusicPlaybackService.syncControllersAfterQueueRestore(
    forceArtwork: Boolean
) {
    withNotificationSessionBridge {
        notificationSessionBridge.updateQueue()
    }

    refreshArtworkAndSession(force = forceArtwork)

    withNotificationSessionBridge {
        notificationSessionBridge.updatePlaybackState()
    }

    updateNotification(force = true)
}

internal fun MusicPlaybackService.publishStateAfterShutdown(
    snapshot: StatePlaybackSnapshot? = null
) {
    dispatchPlaybackEvent(PlaybackEvent.ShutdownStatePublished(snapshot = snapshot))
}

internal fun MusicPlaybackService.publishAllRuntimeState(
    forceNotification: Boolean = false
) {
    dispatchPlaybackEvent(
        PlaybackEvent.RuntimeStateChanged(forceNotification = forceNotification)
    )
}

internal fun MusicPlaybackService.publishPlaybackState(
    reason: PublishReason,
    forceNotification: Boolean = false,
    forceWidgetUpdate: Boolean = forceNotification
) {
    dispatchPlaybackEvent(
        PlaybackEvent.PlaybackStateChanged(
            reason = reason,
            forceNotification = forceNotification,
            forceWidgetUpdate = forceWidgetUpdate
        )
    )
}

internal fun MusicPlaybackService.onQueueMetadataChanged() {
    dispatchPlaybackEvent(PlaybackEvent.QueueMetadataChanged)
}

internal fun MusicPlaybackService.updateWidgetsFromRuntimeState() {
    if (playbackRuntimeStateStoreOrNull() == null) return
    updateWidgetSnapshot(runtimeWidgetSnapshot())
}

internal fun MusicPlaybackService.updateWidgetSnapshot(
    snapshot: WidgetPlaybackSnapshot
) {
    val widgetCoordinator = widgetUpdateCoordinatorOrNull() ?: return
    val scope = serviceScopeOrNull() ?: return

    jobState.widgetUpdateJob?.cancel()
    jobState.widgetUpdateJob = scope.launch {
        withContext(NonCancellable) {
            widgetCoordinator.updateAll(snapshot)
        }
    }
}

internal fun MusicPlaybackService.updateWidgetSnapshotBlocking(
    snapshot: WidgetPlaybackSnapshot
) {
    if (widgetUpdateCoordinatorOrNull() == null) return
    updateWidgetSnapshot(snapshot)
}

fun MusicPlaybackState.toWidgetPlaybackSnapshot(playMode: Int): WidgetPlaybackSnapshot {
    return WidgetPlaybackSnapshot(
        queue = queue,
        currentTrack = currentTrack,
        currentIndex = currentIndex,
        positionMs = positionMs,
        isPlaying = isPlaying,
        playMode = playMode
    )
}

fun StatePlaybackSnapshot.toWidgetPlaybackSnapshot(playMode: Int): WidgetPlaybackSnapshot {
    return WidgetPlaybackSnapshot(
        queue = queue,
        currentTrack = currentTrack,
        currentIndex = currentIndex,
        positionMs = positionMs,
        isPlaying = false,
        playMode = playMode
    )
}

internal fun MusicPlaybackService.runtimeWidgetSnapshot(): WidgetPlaybackSnapshot {
    return playbackRuntimeStateStore.state.value.toWidgetPlaybackSnapshot(
        playMode = runtimeCacheState.latestSettingPreferences.playMode
    )
}

internal fun MusicPlaybackService.shutdownWidgetSnapshot(
    snapshot: StatePlaybackSnapshot?
): WidgetPlaybackSnapshot {
    val playMode = runtimeCacheState.latestSettingPreferences.playMode
    return snapshot?.toWidgetPlaybackSnapshot(playMode = playMode)
        ?: runtimeWidgetSnapshot()
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
        requestedIndex = remapQueueIndexUseCase(
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
    // Obfuscated parity: do not autosave playback progress on timer ticks.
}

internal fun MusicPlaybackService.capturePlaybackSnapshot(): StatePlaybackSnapshot {
    return snapshotManager.capture(
        player = playerOrNull(),
        queueState = queueManager.state
    )
}

internal fun MusicPlaybackService.clearPersistedPlaybackState() {
    serviceScopeOrNull()?.launch {
        snapshotManager.clearProgress()
    }
}

internal fun MusicPlaybackService.clearPersistedQueueAsync() {
    serviceScopeOrNull()?.launch {
        withContext(dispatchers.io) {
            playbackQueueRepo.clearQueue()
        }
    }
}

internal fun MusicPlaybackService.resolveCurrentSnapshotPositionMs(): Long {
    return snapshotManager.resolveCurrentPositionMs(
        player = playerOrNull(),
        queueState = queueManager.state
    )
}

internal fun MusicPlaybackService.resolveCurrentSnapshotDurationMs(
    track: Music?
): Long {
    return snapshotManager.resolveCurrentDurationMs(
        player = playerOrNull(),
        track = track
    )
}

internal fun MusicPlaybackService.publishAllRuntimeStateDirect(
    forceNotification: Boolean = false
) {
    publishStateWithStartupGate(
        reason = null,
        forceNotification = forceNotification
    )
}

internal fun MusicPlaybackService.publishPlaybackStateDirect(
    reason: PublishReason,
    forceNotification: Boolean = false,
    forceWidgetUpdate: Boolean = forceNotification
) {
    publishStateWithStartupGate(
        reason = reason,
        forceNotification = forceNotification,
        forceWidgetUpdate = forceWidgetUpdate
    )
}

internal fun MusicPlaybackService.publishStateAfterShutdownDirect(
    snapshot: StatePlaybackSnapshot? = null
) {
    stateUpdateCoordinatorOrNull()?.publishStateAfterShutdown(snapshot)
}

internal fun MusicPlaybackService.dispatchPlaybackEvent(event: PlaybackEvent) {
    playbackEventDispatcherOrNull()?.let { dispatcher ->
        dispatcher.dispatch(event)
        return
    }

    when (event) {
        is PlaybackEvent.RuntimeStateChanged -> {
            publishAllRuntimeStateDirect(event.forceNotification)
        }

        is PlaybackEvent.PlaybackStateChanged -> {
            publishPlaybackStateDirect(
                reason = event.reason,
                forceNotification = event.forceNotification,
                forceWidgetUpdate = event.forceWidgetUpdate
            )
        }

        is PlaybackEvent.ShutdownStatePublished -> {
            publishStateAfterShutdownDirect(event.snapshot)
        }

        PlaybackEvent.QueueMetadataChanged -> {
            if (notificationSessionBridgeOrNull() == null) return
            notificationSessionBridge.updateQueue()
            refreshArtworkAndSession(force = true)
            persistFor(
                PersistenceEvent.QueueMutation(
                    snapshot = capturePlaybackSnapshot()
                )
            )
            publishPlaybackStateDirect(
                reason = PublishReason.QueueChanged,
                forceNotification = true,
                forceWidgetUpdate = true
            )
        }

        is PlaybackEvent.NotificationUpdateRequested -> {
            updateNotificationDirect(force = event.force)
        }
    }
}

private fun MusicPlaybackService.publishStateWithStartupGate(
    reason: PublishReason?,
    forceNotification: Boolean,
    forceWidgetUpdate: Boolean = forceNotification
) {
    val coordinator = stateUpdateCoordinatorOrNull() ?: return

    val effectiveForceNotification = forceNotification && !startupState.deferForcedStartupUiUpdates
    val effectiveForceWidgetUpdate = forceWidgetUpdate && !startupState.deferForcedStartupUiUpdates

    if (reason == null) {
        coordinator.publishAllRuntimeState(
            forceNotification = effectiveForceNotification
        )
        return
    }

    coordinator.publishPlaybackState(
        reason = reason,
        forceNotification = effectiveForceNotification,
        forceWidgetUpdate = effectiveForceWidgetUpdate
    )
}

internal fun MusicPlaybackService.updateNotificationDirect(force: Boolean = false) {
    val coordinator = stateUpdateCoordinatorOrNull() ?: return
    val scope = serviceScopeOrNull() ?: return
    jobState.scheduleNotificationUpdate(
        scope = scope,
        force = force,
        delayMs = NOTIFICATION_COALESCE_WINDOW_MS
    ) { updateForce ->
        coordinator.updateNotification(force = updateForce)
    }
}

internal fun MusicPlaybackService.persistFor(event: PersistenceEvent) {
    val scope = serviceScopeOrNull() ?: return

    for (operation in resolvePersistencePlan(event)) {
        when (operation) {
            is PersistenceOp.PersistProgress -> {
                val track = operation.track ?: continue
                scope.launch {
                    snapshotManager.persistProgress(
                        track = track,
                        positionMs = operation.positionMs.coerceAtLeast(0L),
                        currentIndex = operation.currentIndex
                    )
                }
            }

            is PersistenceOp.PersistSnapshot -> {
                scope.launch {
                    withContext(NonCancellable) {
                        snapshotManager.persist(
                            snapshot = operation.snapshot,
                            persistQueue = operation.persistQueue
                        )
                    }
                }
            }
        }
    }
}
