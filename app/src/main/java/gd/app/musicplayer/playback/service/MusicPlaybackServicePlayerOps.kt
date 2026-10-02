package gd.app.musicplayer.playback.service

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import gd.app.musicplayer.core.common.extension.parseTrackIdFromQueueMediaId
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.timer.SleepTimerManager
import kotlinx.coroutines.launch
import gd.app.musicplayer.playback.player.PlaybackEngine
import gd.app.musicplayer.playback.state.PlaybackSnapshot
import gd.app.musicplayer.playback.shutdown.ShutdownOptions
import gd.app.musicplayer.playback.state.PublishReason
import gd.app.musicplayer.playback.timer.SleepTimerState
import gd.app.musicplayer.playback.ProcessPlayerHolder
import gd.app.musicplayer.playback.effects.VolumeFader
import gd.app.musicplayer.playback.transition.TimedTransitionController


internal fun MusicPlaybackService.configurePlayer() {
    // Original: BassPlayer lives in AudioController process scope; service attaches later.
    processPlayerHolder.externalCallbacks = object : PlaybackEngine.Callbacks {

            override fun onPlayerReady() {
                applyAudioEffectsFromPreferences()
                publishAllRuntimeState()
            }

            override fun onTrackEnded() {
                handleTrackEnded()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                lockScreenController.setPlaying(isPlaying)

                if (isPlaying) {
                    deferForcedStartupUiUpdates = false
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
                    isAudioFocusControllerInitialized() &&
                    !audioFocusController.request()
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

    val components = processPlayerHolder.getOrCreate()

    playbackEngine = PlaybackEngine(
        context = this,
        musicPlayerFactory = musicPlayerFactory
    )

    player = components.player
    playerEventHandler = components.playerEventHandler
    stereoBalanceAudioProcessor = components.stereoBalanceAudioProcessor
    crossfadeStereoBalanceAudioProcessor = components.crossfadeStereoBalanceAudioProcessor
    // Crossfade ExoPlayer deferred — original secondary MediaPlayer only on crossfade.
}

internal fun MusicPlaybackService.handleMediaItemTransition(reason: Int) {
    if (
        suppressNextCrossfadeCommitTransition &&
        reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
    ) {
        suppressNextCrossfadeCommitTransition = false
        return
    }

    if (maybeCorrectExternalMediaItemTransition(reason)) {
        return
    }
    deferForcedStartupUiUpdates = false

    val playerIndex = player.currentMediaItemIndex

    // Original e0 owns the cursor. With single-track ExoPlayer, mediaItemIndex is always 0 —
    // never treat it as the app queue index unless the player holds the full timeline.
    if (player.mediaItemCount > 1 && playerIndex in queue.indices) {
        queueManager.updateCurrentIndex(playerIndex)
    }
    if (pendingQueueSessionSyncAfterStartupPlay) {
        pendingQueueSessionSyncAfterStartupPlay = false
        notificationSessionBridge.updateQueue()
    }

    resetTimedTransitionState()
    if (isVolumeFaderInitialized()) {
        volumeFader.applyResolvedVolume()
    } else {
        playbackTuningController.applyResolvedPlayerVolume()
    }
    refreshArtworkAndSession(force = true)

    if (player.isPlaying) {
        playbackStatsTracker.onTrackStarted(
            music = queue.getOrNull(currentIndex),
            force = true
        )
    }

    persistSessionFromCurrentStateAsync()
    updateMedia3CommandButtons()
    publishAllRuntimeState(forceNotification = true)
}

internal fun MusicPlaybackService.maybeCorrectExternalMediaItemTransition(reason: Int): Boolean {
    if (correctingMediaItemTransition) {
        correctingMediaItemTransition = false
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
                queue = queue,
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

    correctingMediaItemTransition = true
    if (player.mediaItemCount <= 1) {
        // Single-track player: load the app-queue target instead of ExoPlayer timeline seek.
        setPlayerQueue(
            queue = queue,
            startIndex = expectedIndex,
            startPositionMs = 0L,
            playWhenReady = player.playWhenReady
        )
        queueManager.updateCurrentIndex(expectedIndex)
    } else {
        player.seekTo(
            expectedIndex,
            0L
        )
    }
    return true
}

internal fun MusicPlaybackService.isPendingMedia3TransportTransition(reason: Int): Boolean {
    return pendingMedia3TransportCommand != NO_PLAYER_COMMAND &&
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
}

internal fun MusicPlaybackService.resolvePendingMedia3TransportTargetIndex(): Int? {
    val startIndex = pendingMedia3TransportStartIndex
        .takeIf { index -> index in queue.indices }
        ?: currentIndex

    return when (pendingMedia3TransportCommand) {
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
            playbackModeResolver.clearReverseSticky()
            playbackModeResolver.resolveNextIndex(
                queue = queue,
                currentIndex = startIndex,
                fromAutoTransition = false
            )
        }

        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
            // Original [y6.y.D0]: always previous — no 5s restart window.
            playbackModeResolver.markPreviousNavigated()
            playbackModeResolver.resolvePreviousIndex(
                queue = queue,
                currentIndex = startIndex,
                shouldRestartCurrent = false
            )
        }

        else -> null
    }
}

internal fun MusicPlaybackService.clearPendingMedia3TransportCommand() {
    pendingMedia3TransportCommand = NO_PLAYER_COMMAND
    pendingMedia3TransportStartIndex = NO_INDEX
    pendingMedia3TransportStartPositionMs = 0L
}

internal fun MusicPlaybackService.handleMedia3PlayerInteractionFinished() {
    pendingMedia3StopSnapshot?.let { snapshot ->
        handleMedia3StopFinished(snapshot)
        pendingMedia3StopSnapshot = null
        clearPendingMedia3TransportCommand()
        return
    }

    syncCurrentIndexWithPlayer()
    persistSessionFromCurrentStateAsync()
    updateMedia3CommandButtons()
    publishAllRuntimeState(forceNotification = true)
    clearPendingMedia3TransportCommand()
}

internal fun MusicPlaybackService.handleMedia3StopFinished(snapshot: PlaybackSnapshot) {
    keepIdleNotification = false
    updateStopAfterCurrentTrackMode(false)
    notificationDismissedByUser = false

    playbackStatsTracker.reset()
    resetTimedTransitionState()

    if (isAudioFocusControllerInitialized()) {
        audioFocusController.abandon()
    }

    persistPlaybackSnapshotAsync(
        snapshot = snapshot,
        persistQueue = false
    )
    updateMedia3CommandButtons()
    publishStateAfterShutdown(snapshot)
    updateNotification(force = true)
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

    if (isVolumeFaderInitialized()) {
        volumeFader.applyResolvedVolume()
    } else {
        playbackTuningController.applyResolvedPlayerVolume()
    }
}

internal fun MusicPlaybackService.handleTrackEnded() {
    if (
        isTimedTransitionControllerInitialized() &&
        timedTransitionController.consumeTrackEndedDuringCrossfade()
    ) {
        return
    }

    // Original [u6.e.u]: reuse gapless-prepared next MediaPlayer when it matches.
    if (
        isTimedTransitionControllerInitialized() &&
        timedTransitionController.consumeTrackEndedWithGaplessPrepare()
    ) {
        return
    }

    resetTimedTransitionState()

    playbackStatsTracker.onTrackEnded(
        music = queue.getOrNull(currentIndex)
    )

    if (stopAfterCurrentTrack) {
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
    if (isVolumeFaderInitialized()) {
        volumeFader.cancel()
        volumeFader.resetToFullVolume()
    }

    if (isAudioFocusControllerInitialized()) {
        audioFocusController.abandon()
    }

    if (isPlayerInitialized()) {
        player.playWhenReady = false
    }

    persistSessionFromCurrentStateAsync()
    updateMedia3CommandButtons()
    publishAllRuntimeState(forceNotification = true)
}

internal fun MusicPlaybackService.playIndex(
    index: Int,
    playWhenReady: Boolean
) {
    if (!isQueueActionControllerInitialized()) return

    queueActionController.playIndex(
        index = index,
        playWhenReady = playWhenReady
    )
}

internal fun MusicPlaybackService.setPlayerQueue(
    queue: List<Music>,
    startIndex: Int,
    startPositionMs: Long = 0L,
    playWhenReady: Boolean
) {
    if (!isPlayerQueueControllerInitialized()) return

    playerQueueController.setPlayerQueue(
        queue = queue,
        startIndex = startIndex,
        startPositionMs = startPositionMs,
        playWhenReady = playWhenReady
    )
}

internal fun MusicPlaybackService.isPlayerPlaylistSynced(): Boolean {
    if (!isPlayerQueueControllerInitialized()) return false

    return playerQueueController.isPlayerPlaylistSynced(currentIndex)
}

internal fun MusicPlaybackService.applyVolumeForPlaybackStart(playWhenReady: Boolean) {
    if (
        playWhenReady &&
        playbackFadeController.isPlayPauseFadeEnabled()
    ) {
        volumeFader.fadeIn(
            durationMs = PLAY_PAUSE_FADE_DURATION_MS
        )
    } else {
        volumeFader.resetToFullVolume()
    }
}

internal fun MusicPlaybackService.resumePlaybackInternal() {
    if (queue.isEmpty()) {
        pendingResumeAfterDefaultQueue = true
        resumeWithDefaultQueue()
        return
    }

    syncCurrentIndexWithPlayer()

    if (!audioFocusController.request()) return

    if (currentIndex !in queue.indices) {
        playIndex(
            index = 0,
            playWhenReady = true
        )
        return
    }

    if (!isPlayerPlaylistSynced()) {
        deferForcedStartupUiUpdates = true
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
        return
    }

    if (player.playbackState == Player.STATE_IDLE) {
        deferForcedStartupUiUpdates = true
        playIndex(
            index = currentIndex,
            playWhenReady = true
        )
        return
    }

    if (!player.isPlaying) {
        deferForcedStartupUiUpdates = true
        playbackTuningController.applyPlaybackTuning()

        if (playbackFadeController.isPlayPauseFadeEnabled()) {
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

internal fun MusicPlaybackService.pausePlaybackInternal(
    withFade: Boolean = playbackFadeController.isPlayPauseFadeEnabled()
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

            persistCurrentTrackProgressFromPlayerAsync()
            persistSessionFromCurrentStateAsync()
            updateMedia3CommandButtons()
            publishAllRuntimeState(forceNotification = true)
        }
        return
    }

    player.pause()
    persistCurrentTrackProgressFromPlayerAsync()
    persistSessionFromCurrentStateAsync()
    updateMedia3CommandButtons()
    publishAllRuntimeState(forceNotification = true)
}

internal fun MusicPlaybackService.restartCurrentTrack() {
    serviceScope.launch {
        ensurePlaybackRestored()

        if (!isQueueActionControllerInitialized()) return@launch

        queueActionController.restartCurrentTrack()
    }
}

internal fun MusicPlaybackService.playNext(fromAutoTransition: Boolean = false) {
    serviceScope.launch {
        ensurePlaybackRestored()
        playNextInternal(fromAutoTransition)
    }
}

internal fun MusicPlaybackService.playNextInternal(fromAutoTransition: Boolean = false) {
    if (!isQueueActionControllerInitialized()) return

    queueActionController.playNext(
        fromAutoTransition = fromAutoTransition
    )
}

internal fun MusicPlaybackService.playPrevious() {
    serviceScope.launch {
        ensurePlaybackRestored()
        playPreviousInternal()
    }
}

internal fun MusicPlaybackService.playPreviousInternal() {
    if (!isQueueActionControllerInitialized()) return

    queueActionController.playPrevious()
}

internal fun MusicPlaybackService.seekTo(positionMs: Int) {
    serviceScope.launch {
        ensurePlaybackRestored()
        seekToInternal(positionMs)
    }
}

internal fun MusicPlaybackService.seekToInternal(positionMs: Int) {
    if (!isQueueActionControllerInitialized()) return

    queueActionController.seekTo(positionMs)
}

internal fun MusicPlaybackService.applyAudioEffectsFromPreferences() {
    serviceScope.launch {
        playbackTuningController.refreshSoundBalanceFromPreferences()
        audioEffectsManager.applyFromPreferences(player)
        if (isVolumeFaderInitialized()) {
            volumeFader.applyResolvedVolume()
        } else {
            playbackTuningController.applyResolvedPlayerVolumeOnMain()
        }
    }
}

internal fun MusicPlaybackService.stopPlayback() {
    keepIdleNotification = false
    updateStopAfterCurrentTrackMode(false)
    notificationDismissedByUser = false
    val snapshot = capturePlaybackSnapshot()

    playbackStatsTracker.reset()
    resetTimedTransitionState()

    if (isVolumeFaderInitialized()) {
        volumeFader.cancel()
    }

    persistCurrentTrackProgressBlocking(
        track = snapshot.currentTrack,
        positionMs = snapshot.positionMs,
        currentIndex = snapshot.currentIndex
    )
    persistPlaybackSnapshotBlocking(
        snapshot = snapshot,
        persistQueue = false
    )

    if (isPlayerInitialized()) {
        player.pause()
        player.stop()
    }

    if (isAudioFocusControllerInitialized()) {
        audioFocusController.abandon()
    }

    publishStateAfterShutdown(snapshot)
    updateNotification(force = true)
}

internal fun MusicPlaybackService.stopAndClearQueue() {
    notificationDismissedByUser = false
    shutdownPlayback(ShutdownOptions.StopAndClearQueue)
}

internal fun MusicPlaybackService.stopPlaybackWithoutClearingQueue() {
    notificationDismissedByUser = false
    shutdownPlayback(ShutdownOptions.StopWithoutClearingQueue)
}

internal fun MusicPlaybackService.stopAtQueueStart() {
    notificationDismissedByUser = false
    updateStopAfterCurrentTrackMode(false)

    if (queue.isEmpty()) {
        stopAndClearQueue()
        return
    }

    playbackStatsTracker.reset()
    resetTimedTransitionState()

    if (isVolumeFaderInitialized()) {
        volumeFader.cancel()
    }

    queueManager.updateCurrentIndex(0)

    if (isPlayerQueueControllerInitialized()) {
        playerQueueController.setPlayerQueue(
            queue = queue,
            startIndex = 0,
            startPositionMs = 0L,
            playWhenReady = false
        )
    } else if (isPlayerInitialized()) {
        player.pause()
        player.seekTo(0L)
    }

    if (isAudioFocusControllerInitialized()) {
        audioFocusController.abandon()
    }

    persistPlaybackSnapshotBlocking(
        snapshot = capturePlaybackSnapshot(),
        persistQueue = true
    )
    persistCurrentTrackProgressAsync(0)

    refreshArtworkAndSession(force = true)
    publishAllRuntimeState(forceNotification = true)
}

internal fun MusicPlaybackService.resetTimedTransitionState() {
    if (isTimedTransitionControllerInitialized()) {
        timedTransitionController.reset()
    }
}

/**
 * Original [u6.e]: allocate secondary MediaPlayer only when crossfade starts.
 * Build Dream's crossfade ExoPlayer + TimedTransitionController on first need.
 */
internal fun MusicPlaybackService.ensureCrossfadePlaybackStack() {
    if (isTimedTransitionControllerInitialized()) return
    if (!isPlayerInitialized() || !isVolumeFaderInitialized()) return
    if (!isPlaybackModeResolverInitialized()) return

    crossfadePlayer = processPlayerHolder.ensureCrossfadePlayer()

    crossfadeVolumeFader = VolumeFader(
        player = crossfadePlayer,
        scope = serviceScope,
        targetVolumeProvider = {
            playbackTuningController.resolveTargetPlaybackVolume(
                if (isTimedTransitionControllerInitialized()) {
                    timedTransitionController.currentIncomingTrack()
                } else {
                    null
                }
            )
        }
    )

    timedTransitionController = TimedTransitionController(
        outgoingPlayer = player,
        incomingPlayer = crossfadePlayer,
        playbackModeResolver = playbackModeResolver,
        outgoingVolumeFader = volumeFader,
        incomingVolumeFader = crossfadeVolumeFader,
        queueProvider = { queue },
        currentIndexProvider = { currentIndex },
        preferencesProvider = { latestSettingPreferences },
        callbacks = object : TimedTransitionController.Callbacks {
            override fun onPlayNext(fromAutoTransition: Boolean) {
                playNextInternal(fromAutoTransition = fromAutoTransition)
            }

            override fun onCrossfadeStarted(nextIndex: Int) {
                // Original y.U → e0.s(true): cursor advances before audio swap.
                if (nextIndex in queue.indices) {
                    queueManager.updateCurrentIndex(nextIndex)
                    refreshArtworkAndSession(force = true)
                    publishAllRuntimeState(forceNotification = true)
                }
            }

            override fun onCrossfadeIncomingPrepared() {
                serviceScope.launch {
                    // Original shared audio session; apply EQ to incoming session too.
                    audioEffectsManager.applyFromPreferences(crossfadePlayer)
                }
            }

            override fun onCrossfadePromote(
                nextIndex: Int,
                incomingTrack: Music?
            ) {
                promoteCrossfadePlayerToPrimary(
                    nextIndex = nextIndex,
                    incomingTrack = incomingTrack
                )
            }
        }
    )
}

/**
 * Original [u6.e.v] / end of [u6.e.b]: release outgoing; incoming is already the
 * playing next track — promote it to primary without seek/reload.
 */
internal fun MusicPlaybackService.promoteCrossfadePlayerToPrimary(
    nextIndex: Int,
    incomingTrack: Music?
) {
    val outgoing = processPlayerHolder.promoteCrossfadeToPrimary()
    val newPrimary = processPlayerHolder.playerOrNull()
    if (newPrimary == null) {
        outgoing?.let { releaseOutgoingPlayer(it) }
        return
    }

    // Incoming becomes primary — full volume (original A.g(1) after release).
    val newPrimaryFader = playbackFadeController.bind(
        player = newPrimary,
        targetVolume = {
            playbackTuningController.resolveTargetPlaybackVolume(
                queue.getOrNull(currentIndex) ?: incomingTrack
            )
        },
        force = true
    )
    newPrimaryFader.resetToFullVolume()

    player = newPrimary
    playerEventHandler = processPlayerHolder.eventHandlerOrNull() ?: playerEventHandler
    volumeFader = newPrimaryFader

    playbackTuningController.replacePlayer(newPrimary)
    if (isPlayerQueueControllerInitialized()) {
        playerQueueController.replacePlayer(newPrimary)
    }
    if (isStatePublisherInitialized()) {
        statePublisher.replacePlayer(newPrimary)
    }

    // Fresh secondary slot for the next crossfade (original reallocates as needed).
    crossfadePlayer = processPlayerHolder.ensureCrossfadePlayer()
    crossfadeVolumeFader = VolumeFader(
        player = crossfadePlayer,
        scope = serviceScope,
        targetVolumeProvider = {
            playbackTuningController.resolveTargetPlaybackVolume(
                timedTransitionController.currentIncomingTrack()
            )
        }
    )

    if (isTimedTransitionControllerInitialized()) {
        timedTransitionController.bindPlayersAfterPromote(
            newOutgoingPlayer = newPrimary,
            newIncomingPlayer = crossfadePlayer,
            newOutgoingFader = volumeFader,
            newIncomingFader = crossfadeVolumeFader
        )
    }

    rebuildMedia3SessionAfterPlayerPromote()

    if (nextIndex in queue.indices) {
        queueManager.updateCurrentIndex(nextIndex)
    }

    val positionMs = newPrimary.currentPosition.coerceAtLeast(0L)
    persistCurrentTrackProgressAsync(
        positionMs = positionMs
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    )
    playbackStatsTracker.reset()
    playbackStatsTracker.onTrackStarted(
        music = queue.getOrNull(currentIndex) ?: incomingTrack,
        force = true
    )

    serviceScope.launch {
        audioEffectsManager.applyFromPreferences(newPrimary)
    }

    releaseOutgoingPlayer(outgoing)

    refreshArtworkAndSession(force = true)
    persistSessionFromCurrentStateAsync()
    updateMedia3CommandButtons()
    publishAllRuntimeState(forceNotification = true)
}

private fun MusicPlaybackService.releaseOutgoingPlayer(outgoing: ExoPlayer?) {
    if (outgoing == null) return
    runCatching {
        outgoing.playWhenReady = false
        outgoing.stop()
        outgoing.clearMediaItems()
        outgoing.release()
    }
}

private fun MusicPlaybackService.rebuildMedia3SessionAfterPlayerPromote() {
    media3Session?.release()
    media3Session = buildMedia3Session()
    updateMedia3CommandButtons()
}

internal fun MusicPlaybackService.cancelTimedTransitionAndRestoreVolume() {
    if (isTimedTransitionControllerInitialized()) {
        timedTransitionController.cancelAndRestoreVolume()
    }
}

internal fun MusicPlaybackService.isEffectivelyPlaying(): Boolean {
    val primaryPlaying = player.isPlaying ||
        (
            player.playWhenReady &&
                currentIndex in queue.indices &&
                player.playbackState != Player.STATE_IDLE
            )

    if (primaryPlaying) return true

    // During crossfade, incoming may be the audible source while outgoing is nearly silent.
    if (
        isTimedTransitionControllerInitialized() &&
        timedTransitionController.isCrossfadeActive() &&
        isCrossfadePlayerInitialized()
    ) {
        return crossfadePlayer.isPlaying || crossfadePlayer.playWhenReady
    }

    return false
}

internal fun MusicPlaybackService.syncCurrentIndexWithPlayer() {
    val restoreTrackId = pendingRestoreTrackId
    if (restoreTrackId != null) {
        val currentPlayerTrackId = currentPlayerMediaId()
        if (currentPlayerTrackId != restoreTrackId) {
            return
        }
        pendingRestoreTrackId = null
    }

    val resolvedIndex = snapshotManager.resolvePlayerIndex(
        player = if (isPlayerInitialized()) player else null,
        queue = queue
    ) ?: return

    if (resolvedIndex != currentIndex) {
        queueManager.updateCurrentIndex(resolvedIndex)
    }
}

internal fun MusicPlaybackService.currentPlayerMediaId(): Long? {
    if (!isPlayerInitialized()) return null

    return runCatching {
        player.currentMediaItem?.mediaId?.parseTrackIdFromQueueMediaId()
    }.getOrNull()
}
