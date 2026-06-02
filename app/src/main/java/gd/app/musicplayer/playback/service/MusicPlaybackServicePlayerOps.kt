package gd.app.musicplayer.playback.service

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import gd.app.musicplayer.core.common.extension.parseTrackIdFromQueueMediaId
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.timer.SleepTimerManager
import kotlinx.coroutines.launch
import gd.app.musicplayer.playback.player.PlaybackEngine
import gd.app.musicplayer.playback.state.PlaybackSnapshot
import gd.app.musicplayer.playback.shutdown.ShutdownOptions
import gd.app.musicplayer.playback.state.PublishReason
import gd.app.musicplayer.playback.timer.SleepTimerState


internal fun MusicPlaybackService.configurePlayer() {
    playbackEngine = PlaybackEngine(
        context = this,
        musicPlayerFactory = musicPlayerFactory
    )

    val components = playbackEngine.create(
        callbacks = object : PlaybackEngine.Callbacks {

            override fun onPlayerReady() {
                applyAudioEffectsFromPreferences()
                publishAllRuntimeState()
            }

            override fun onTrackEnded() {
                handleTrackEnded()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startupState.deferForcedStartupUiUpdates = false
                    applyAudioEffectsFromPreferences()

                    playbackStatsTracker.onTrackStarted(
                        music = queue.getOrNull(currentIndex)
                    )
                }

                publishAllRuntimeState(forceNotification = true)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
                if (
                    playWhenReady &&
                    (audioFocusControllerOrNull()?.request() == false)
                ) {
                    player.pause()
                    return
                }

                publishAllRuntimeState(forceNotification = true)
            }

            override fun onMediaItemTransition(reason: Int) {
                handleMediaItemTransition(reason)
            }

            override fun onPlayerError(error: PlaybackException) {
                playNextInternal()
            }
        }
    )

    player = components.player
    crossfadePlayer = components.crossfadePlayer
    playerEventHandler = components.playerEventHandler
    stereoBalanceAudioProcessor = components.stereoBalanceAudioProcessor
    crossfadeStereoBalanceAudioProcessor = components.crossfadeStereoBalanceAudioProcessor
}

internal fun MusicPlaybackService.handleMediaItemTransition(reason: Int) {
    if (
        media3TransportState.suppressNextCrossfadeCommitTransition &&
        reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
    ) {
        media3TransportState.suppressNextCrossfadeCommitTransition = false
        return
    }

    if (maybeCorrectExternalMediaItemTransition(reason)) {
        return
    }
    startupState.deferForcedStartupUiUpdates = false

    val playerIndex = player.currentMediaItemIndex

    if (playerIndex in queue.indices) {
        queueManager.updateCurrentIndex(playerIndex)
    }
    if (startupState.pendingQueueSessionSyncAfterStartupPlay) {
        startupState.pendingQueueSessionSyncAfterStartupPlay = false
        notificationSessionBridge.updateQueue()
    }

    resetTimedTransitionState()
    volumeFaderOrNull()?.applyResolvedVolume() ?: playbackTuningController.applyResolvedPlayerVolume()
    refreshArtworkAndSession(force = true)

    if (player.isPlaying) {
        playbackStatsTracker.onTrackStarted(
            music = queue.getOrNull(currentIndex),
            force = true
        )
    }

    persistFor(
        PersistenceEvent.Transition(
            snapshot = capturePlaybackSnapshot()
        )
    )
    playbackSessionOrNull()?.updateMedia3CommandButtons()
    publishAllRuntimeState(forceNotification = true)
}

internal fun MusicPlaybackService.maybeCorrectExternalMediaItemTransition(reason: Int): Boolean {
    if (media3TransportState.correctingMediaItemTransition) {
        media3TransportState.correctingMediaItemTransition = false
        clearPendingMedia3TransportCommand()
        return false
    }

    val playerIndex = player.currentMediaItemIndex

    val expectedIndex = when {
        isPendingMedia3TransportTransition(reason) -> {
            resolvePendingMedia3TransportTargetIndex()
        }

        reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> {
            playbackModeResolver.resolveNextIndex(
                queueSize = queue.size,
                currentIndex = currentIndex,
                fromAutoTransition = true
            )
        }

        else -> return false
    }

    clearPendingMedia3TransportCommand()

    if (expectedIndex == null) {
        stopAtQueueStart()
        return true
    }

    if (expectedIndex !in queue.indices || playerIndex == expectedIndex) {
        return false
    }

    media3TransportState.correctingMediaItemTransition = true
    player.seekTo(
        expectedIndex,
        0L
    )
    return true
}

internal fun MusicPlaybackService.isPendingMedia3TransportTransition(reason: Int): Boolean {
    return Media3TransportTransitionResolver.isPendingTransportTransition(
        reason = reason,
        pendingCommand = media3TransportState.pendingCommand
    )
}

internal fun MusicPlaybackService.resolvePendingMedia3TransportTargetIndex(): Int? {
    return Media3TransportTransitionResolver.resolvePendingTargetIndex(
        pendingCommand = media3TransportState.pendingCommand,
        pendingStartIndex = media3TransportState.pendingStartIndex,
        pendingStartPositionMs = media3TransportState.pendingStartPositionMs,
        currentIndex = currentIndex,
        queueSize = queue.size,
        resolveNextIndex = { queueSize, currentIndex, fromAutoTransition ->
            playbackModeResolver.resolveNextIndex(
                queueSize = queueSize,
                currentIndex = currentIndex,
                fromAutoTransition = fromAutoTransition
            )
        },
        resolvePreviousIndex = { queueSize, currentIndex, shouldRestartCurrent ->
            playbackModeResolver.resolvePreviousIndex(
                queueSize = queueSize,
                currentIndex = currentIndex,
                shouldRestartCurrent = shouldRestartCurrent
            )
        }
    )
}

internal fun MusicPlaybackService.clearPendingMedia3TransportCommand() {
    media3TransportState.clearPendingCommand()
}

internal fun MusicPlaybackService.handleMedia3PlayerInteractionFinished() {
    media3TransportCommandTracker.consumePendingStopSnapshot()?.let { snapshot ->
        handleMedia3StopFinished(snapshot)
        clearPendingMedia3TransportCommand()
        return
    }

    syncCurrentIndexWithPlayer()
    persistFor(
        PersistenceEvent.Transition(
            snapshot = capturePlaybackSnapshot()
        )
    )
    playbackSessionOrNull()?.updateMedia3CommandButtons()
    publishAllRuntimeState(forceNotification = true)
    clearPendingMedia3TransportCommand()
}

internal fun MusicPlaybackService.handleMedia3StopFinished(snapshot: PlaybackSnapshot) {
    persistFor(
        PersistenceEvent.Stop(
            snapshot = snapshot,
            clearQueue = false
        )
    )
    shutdownPlayback(ShutdownOptions.StopInPlace)
    playbackSessionOrNull()?.updateMedia3CommandButtons()
}

internal fun MusicPlaybackService.prepareRestoredPlayerState(
    index: Int,
    positionMs: Long
) {
    if (index !in queue.indices) return

    setPlayerQueue(
        queue = queue,
        startIndex = index,
        startPositionMs = positionMs,
        playWhenReady = false
    )

    volumeFaderOrNull()?.applyResolvedVolume() ?: playbackTuningController.applyResolvedPlayerVolume()
}

internal fun MusicPlaybackService.handleTrackEnded() {
    if (
        timedTransitionControllerOrNull()?.consumeTrackEndedDuringCrossfade() == true
    ) {
        return
    }

    resetTimedTransitionState()

    playbackStatsTracker.onTrackEnded(
        music = queue.getOrNull(currentIndex)
    )

    if (sessionFlags.stopAfterCurrentTrack) {
        updateStopAfterCurrentTrackMode(false)
        val timerAction = SleepTimerManager.state.value.action
        if (timerAction == SleepTimerState.ACTION_STOP_PLAYBACK) {
            SleepTimerManager.finishPendingTrackEnd(executeAction = false)
            pauseAtTrackEndForSleepTimer()
        } else {
            SleepTimerManager.finishPendingTrackEnd()
        }
        return
    }

    playNextInternal(fromAutoTransition = true)
}

internal fun MusicPlaybackService.pauseAtTrackEndForSleepTimer() {
    volumeFaderOrNull()?.run {
        cancel()
        resetToFullVolume()
    }
    audioFocusControllerOrNull()?.abandon()
    playerOrNull()?.playWhenReady = false

    persistFor(
        PersistenceEvent.Pause(
            snapshot = capturePlaybackSnapshot()
        )
    )
    playbackSessionOrNull()?.updateMedia3CommandButtons()
    publishAllRuntimeState(forceNotification = true)
}

internal fun MusicPlaybackService.playIndex(
    index: Int,
    playWhenReady: Boolean
) {
    queueActionControllerOrNull()?.playIndex(
        index = index,
        playWhenReady = playWhenReady
    ) ?: return
}

internal fun MusicPlaybackService.setPlayerQueue(
    queue: List<Music>,
    startIndex: Int,
    startPositionMs: Long = 0L,
    playWhenReady: Boolean
) {
    playerQueueControllerOrNull()?.setPlayerQueue(
        queue = queue,
        startIndex = startIndex,
        startPositionMs = startPositionMs,
        playWhenReady = playWhenReady
    ) ?: return
}

internal fun MusicPlaybackService.isPlayerPlaylistSynced(): Boolean {
    return playerQueueControllerOrNull()?.isPlayerPlaylistSynced() == true
}

internal fun MusicPlaybackService.applyVolumeForPlaybackStart(playWhenReady: Boolean) {
    if (
        playWhenReady &&
        playbackTuningController.isPlayPauseFadeEnabled()
    ) {
        volumeFader.fadeIn(
            durationMs = PLAY_PAUSE_FADE_DURATION_MS
        )
    } else {
        volumeFader.resetToFullVolume()
    }
}

internal fun MusicPlaybackService.resumePlaybackInternal() {
    measurePlaybackRuntimePhaseSync("resume_playback_internal") {
        if (queue.isEmpty()) {
            startupState.pendingResumeAfterDefaultQueue = true
            resumeWithDefaultQueue()
            return@measurePlaybackRuntimePhaseSync
        }

        syncCurrentIndexWithPlayer()

        if (!audioFocusController.request()) return@measurePlaybackRuntimePhaseSync

        if (currentIndex !in queue.indices) {
            playIndex(
                index = 0,
                playWhenReady = true
            )
            return@measurePlaybackRuntimePhaseSync
        }

        if (!isPlayerPlaylistSynced()) {
            startupState.deferForcedStartupUiUpdates = true
            setPlayerQueue(
                queue = queue,
                startIndex = currentIndex,
                startPositionMs = player.currentPosition.coerceAtLeast(0L),
                playWhenReady = true
            )

            applyVolumeForPlaybackStart(playWhenReady = true)
            publishPlaybackState(
                reason = PublishReason.Restore,
                forceNotification = false
            )
            return@measurePlaybackRuntimePhaseSync
        }

        if (player.playbackState == Player.STATE_IDLE) {
            startupState.deferForcedStartupUiUpdates = true
            playIndex(
                index = currentIndex,
                playWhenReady = true
            )
            return@measurePlaybackRuntimePhaseSync
        }

        if (!player.isPlaying) {
            startupState.deferForcedStartupUiUpdates = true
            playbackTuningController.applyPlaybackTuning()

            if (playbackTuningController.isPlayPauseFadeEnabled()) {
                volumeFader.muteImmediately()
                player.play()
                volumeFader.fadeIn(
                    durationMs = PLAY_PAUSE_FADE_DURATION_MS
                )
            } else {
                volumeFader.resetToFullVolume()
                player.play()
            }

            publishPlaybackState(
                reason = PublishReason.PlayerEvent,
                forceNotification = false
            )
        }
    }
}

internal fun MusicPlaybackService.pausePlaybackInternal(
    withFade: Boolean = playbackTuningController.isPlayPauseFadeEnabled()
) {
    if (!isEffectivelyPlaying()) return

    cancelTimedTransitionAndRestoreVolume()

    if (withFade && player.isPlaying) {
        volumeFader.fadeOut(
            durationMs = PLAY_PAUSE_FADE_DURATION_MS
        ) {
            player.pause()

            // Restore normal volume while paused so the next play starts from a clean state.
            volumeFader.resetToFullVolume()

            persistFor(
                PersistenceEvent.Pause(
                    snapshot = capturePlaybackSnapshot()
                )
            )
            playbackSessionOrNull()?.updateMedia3CommandButtons()
            publishAllRuntimeState(forceNotification = true)
        }
        return
    }

    player.pause()
    persistFor(
        PersistenceEvent.Pause(
            snapshot = capturePlaybackSnapshot()
        )
    )
    playbackSessionOrNull()?.updateMedia3CommandButtons()
    publishAllRuntimeState(forceNotification = true)
}

internal fun MusicPlaybackService.restartCurrentTrack() {
    serviceScope.launch {
        ensurePlaybackRestored()

        queueActionControllerOrNull()?.restartCurrentTrack()
    }
}

internal fun MusicPlaybackService.playNext(fromAutoTransition: Boolean = false) {
    serviceScope.launch {
        ensurePlaybackRestored()
        playNextInternal(fromAutoTransition)
    }
}

internal fun MusicPlaybackService.playNextInternal(fromAutoTransition: Boolean = false) {
    val controller = queueActionControllerOrNull() ?: return
    measurePlaybackRuntimePhaseSync("play_next_internal") {
        controller.playNext(
            fromAutoTransition = fromAutoTransition
        )
    }
}

internal fun MusicPlaybackService.commitCrossfadeTransition(
    nextIndex: Int,
    positionMs: Long
) {
    if (
        playerQueueControllerOrNull() == null ||
        nextIndex !in queue.indices
    ) {
        return
    }

    if (!isPlayerPlaylistSynced()) {
        setPlayerQueue(
            queue = queue,
            startIndex = nextIndex,
            startPositionMs = positionMs,
            playWhenReady = true
        )
    } else {
        media3TransportState.suppressNextCrossfadeCommitTransition = true
        playerQueueController.seekTo(
            index = nextIndex,
            positionMs = positionMs
        )
        playerQueueController.setPlayWhenReady(true)
        playerQueueController.play()
    }

    queueManager.updateCurrentIndex(nextIndex)
    persistFor(
        PersistenceEvent.TrackProgress(
            track = queueManager.currentTrack,
            positionMs = positionMs.coerceAtLeast(0L),
            currentIndex = queueManager.currentIndex
        )
    )
    playbackStatsTracker.reset()
    playbackTuningController.applyPlaybackTuning()

    refreshArtworkAndSession(force = true)
    persistFor(
        PersistenceEvent.Transition(
            snapshot = capturePlaybackSnapshot()
        )
    )
    playbackSessionOrNull()?.updateMedia3CommandButtons()
    publishAllRuntimeState(forceNotification = true)
}

internal fun MusicPlaybackService.playPrevious() {
    serviceScope.launch {
        ensurePlaybackRestored()
        playPreviousInternal()
    }
}

internal fun MusicPlaybackService.playPreviousInternal() {
    val controller = queueActionControllerOrNull() ?: return
    measurePlaybackRuntimePhaseSync("play_previous_internal") {
        controller.playPrevious()
    }
}

internal fun MusicPlaybackService.seekTo(positionMs: Int) {
    serviceScope.launch {
        ensurePlaybackRestored()
        seekToInternal(positionMs)
    }
}

internal fun MusicPlaybackService.seekToInternal(positionMs: Int) {
    val controller = queueActionControllerOrNull() ?: return
    measurePlaybackRuntimePhaseSync("seek_to_internal") {
        controller.seekTo(positionMs)
    }
}

internal fun MusicPlaybackService.applyAudioEffectsFromPreferences() {
    serviceScope.launch {
        playbackTuningController.refreshSoundBalanceFromPreferences()
        audioEffectsManager.applyFromPreferences(player)
        volumeFaderOrNull()?.applyResolvedVolume()
            ?: playbackTuningController.applyResolvedPlayerVolumeOnMain()
    }
}

internal fun MusicPlaybackService.stopPlayback() {
    val snapshot = capturePlaybackSnapshot()

    persistFor(
        PersistenceEvent.Stop(
            snapshot = snapshot,
            clearQueue = false
        )
    )
    shutdownPlayback(ShutdownOptions.StopInPlace)
}

internal fun MusicPlaybackService.stopAndClearQueue() {
    sessionFlags.notificationDismissedByUser = false
    shutdownPlayback(ShutdownOptions.StopAndClearQueue)
}

internal fun MusicPlaybackService.stopPlaybackWithoutClearingQueue() {
    sessionFlags.notificationDismissedByUser = false
    shutdownPlayback(ShutdownOptions.StopWithoutClearingQueue)
}

internal fun MusicPlaybackService.stopAtQueueStart() {
    sessionFlags.notificationDismissedByUser = false
    updateStopAfterCurrentTrackMode(false)

    if (queue.isEmpty()) {
        stopAndClearQueue()
        return
    }

    playbackStatsTracker.reset()
    resetTimedTransitionState()

    volumeFaderOrNull()?.cancel()

    queueManager.updateCurrentIndex(0)

    playerQueueControllerOrNull()?.let { queueController ->
        queueController.setPlayerQueue(
            queue = queue,
            startIndex = 0,
            startPositionMs = 0L,
            playWhenReady = false
        )
    } ?: run {
        playerOrNull()?.let { basePlayer ->
            basePlayer.pause()
            basePlayer.seekTo(0L)
        }
    }

    audioFocusControllerOrNull()?.abandon()

    val snapshot = capturePlaybackSnapshot()
    persistFor(
        PersistenceEvent.Stop(
            snapshot = snapshot.copy(positionMs = 0L),
            clearQueue = true
        )
    )

    refreshArtworkAndSession(force = true)
    publishAllRuntimeState(forceNotification = true)
}

internal fun MusicPlaybackService.resetTimedTransitionState() {
    timedTransitionControllerOrNull()?.reset()
}

internal fun MusicPlaybackService.cancelTimedTransitionAndRestoreVolume() {
    timedTransitionControllerOrNull()?.cancelAndRestoreVolume()
}

internal fun MusicPlaybackService.isEffectivelyPlaying(): Boolean {
    return player.isPlaying ||
            (
                    player.playWhenReady &&
                            currentIndex in queue.indices &&
                            player.playbackState != Player.STATE_IDLE
                    )
}

internal fun MusicPlaybackService.syncCurrentIndexWithPlayer() {
    val restoreTrackId = runtimeCacheState.pendingRestoreTrackId
    if (restoreTrackId != null) {
        val currentPlayerTrackId = currentPlayerMediaId()
        if (currentPlayerTrackId != restoreTrackId) {
            return
        }
        runtimeCacheState.pendingRestoreTrackId = null
    }

    val resolvedIndex = snapshotManager.resolvePlayerIndex(
        player = playerOrNull(),
        queue = queue
    ) ?: return

    if (resolvedIndex != currentIndex) {
        queueManager.updateCurrentIndex(resolvedIndex)
    }
}

internal fun MusicPlaybackService.currentPlayerMediaId(): Long? {
    return runCatching {
        playerOrNull()?.currentMediaItem?.mediaId?.parseTrackIdFromQueueMediaId()
    }.getOrNull()
}
