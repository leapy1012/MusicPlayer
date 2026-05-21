package gd.app.musicplayer.playback.queue

import androidx.media3.common.C
import gd.app.musicplayer.domain.model.Music

class QueueActionController(
    private val queueManager: PlaybackQueueManager,
    private val playerQueueController: PlayerQueueController,
    private val callbacks: QueueMutationCallbacks
) {

    fun playNext(fromAutoTransition: Boolean = false) {
        val currentState = queueManager.state

        if (currentState.queue.isEmpty()) return

        val nextIndex = callbacks.resolveNextIndex(
            queueSize = currentState.queue.size,
            currentIndex = currentState.currentIndex,
            fromAutoTransition = fromAutoTransition
        ) ?: run {
            if (fromAutoTransition) {
                callbacks.onAutoTransitionReachedQueueEnd()
            }

            return
        }

        if (!callbacks.requestAudioFocus()) return

        playResolvedIndex(
            index = nextIndex,
            playWhenReady = true
        )
    }

    fun playPrevious() {
        val currentState = queueManager.state

        if (currentState.queue.isEmpty()) return

        if (callbacks.currentPlayerPositionIsAfterPreviousRestartWindow()) {
            callbacks.seekCurrentToStart()
            return
        }

        val previousIndex = callbacks.resolvePreviousIndex(
            queueSize = currentState.queue.size,
            currentIndex = currentState.currentIndex,
            shouldRestartCurrent = false
        ) ?: return

        if (!callbacks.requestAudioFocus()) return

        playResolvedIndex(
            index = previousIndex,
            playWhenReady = true
        )
    }

    private fun playResolvedIndex(
        index: Int,
        playWhenReady: Boolean
    ) {
        if (index !in queueManager.queue.indices) return

        callbacks.resetTimedTransition()

        if (!playerQueueController.isPlayerPlaylistSynced()) {
            playerQueueController.setPlayerQueue(
                queue = queueManager.queue,
                startIndex = index,
                startPositionMs = 0L,
                playWhenReady = playWhenReady
            )
        } else {
            playerQueueController.seekTo(
                index = index,
                positionMs = 0L
            )

            playerQueueController.setPlayWhenReady(playWhenReady)

            if (playWhenReady) {
                playerQueueController.play()
            }
        }

        queueManager.updateCurrentIndex(index)

        callbacks.resetPlaybackStatistics()
        callbacks.applyVolumeForPlaybackStart(playWhenReady)

        callbacks.refreshArtworkAndSession(force = true)
        callbacks.publishPlayerEvent(forceNotification = true)
    }

    fun removeQueueItem(index: Int) {
        val currentState = queueManager.state

        if (index !in currentState.queue.indices) return

        val wasPlaying = callbacks.isEffectivelyPlaying()
        val removedCurrent = index == currentState.currentIndex

        queueManager.removeAt(index)

        if (queueManager.queue.isEmpty()) {
            callbacks.onQueueBecameEmpty()
            return
        }

        queueManager.save()
        callbacks.updateNotificationSessionQueue()

        if (playerQueueController.isPlayerPlaylistSynced()) {
            playerQueueController.removeMediaItem(index)

            if (removedCurrent) {
                playerQueueController.seekTo(
                    index = queueManager.currentIndex,
                    positionMs = 0L
                )

                playerQueueController.setPlayWhenReady(wasPlaying)
                playerQueueController.prepare()
            }
        } else {
            playerQueueController.setPlayerQueue(
                queue = queueManager.queue,
                startIndex = queueManager.currentIndex,
                startPositionMs = if (removedCurrent) {
                    0L
                } else {
                    callbacks.currentPlayerPositionMs()
                },
                playWhenReady = wasPlaying
            )
        }

        callbacks.refreshArtworkAndSession(force = true)
        callbacks.publishPlayerEvent(forceNotification = true)
    }

    fun seekTo(positionMs: Int) {
        val currentState = queueManager.state

        if (!currentState.hasCurrentTrack) return

        val durationMs = playerQueueController.currentDurationMs()
            .takeIf { duration ->
                duration != C.TIME_UNSET && duration > 0L
            }
            ?.toInt()
            ?: callbacks.currentTrackDurationMs()

        val target = positionMs.coerceIn(
            0,
            durationMs
        )

        playerQueueController.seekTo(target.toLong())

        callbacks.persistSessionFromCurrentStateAsync()
        callbacks.publishPlayerEvent(forceNotification = true)
    }

    fun moveQueueItem(
        fromIndex: Int,
        toIndex: Int
    ) {
        val currentState = queueManager.state

        if (
            fromIndex !in currentState.queue.indices ||
            toIndex !in currentState.queue.indices ||
            fromIndex == toIndex
        ) {
            return
        }

        val movedTrackId = currentState.queue[fromIndex].id
        val currentTrackId = currentState.currentTrack?.id

        queueManager.move(
            fromIndex = fromIndex,
            toIndex = toIndex
        )

        queueManager.save()
        callbacks.updateNotificationSessionQueue()

        if (playerQueueController.isPlayerPlaylistSynced()) {
            playerQueueController.moveMediaItem(
                fromIndex = fromIndex,
                toIndex = toIndex
            )
        } else {
            playerQueueController.setPlayerQueue(
                queue = queueManager.queue,
                startIndex = queueManager.currentIndex,
                startPositionMs = callbacks.currentPlayerPositionMs(),
                playWhenReady = callbacks.isEffectivelyPlaying()
            )
        }

        if (movedTrackId == currentTrackId) {
            callbacks.refreshArtworkAndSession(force = true)
        }

        callbacks.publishPlayerEvent(forceNotification = true)
    }

    fun enqueue(incomingQueue: List<Music>) {
        val playableIncomingQueue = playableQueue(incomingQueue)
        if (playableIncomingQueue.isEmpty()) return

        val currentState = queueManager.state
        val wasEmpty = currentState.queue.isEmpty()
        val wasPlayerQueueSynced = playerQueueController.isPlayerPlaylistSynced()

        val nextQueue = currentState.queue + playableIncomingQueue
        val nextIndex = if (wasEmpty) {
            0
        } else {
            currentState.currentIndex
        }

        callbacks.markPlaybackRestored()

        queueManager.setQueue(
            newQueue = nextQueue,
            requestedIndex = nextIndex
        )

        queueManager.save()
        callbacks.updateNotificationSessionQueue()

        if (wasEmpty || !wasPlayerQueueSynced) {
            playerQueueController.setPlayerQueue(
                queue = queueManager.queue,
                startIndex = queueManager.currentIndex.coerceAtLeast(0),
                startPositionMs = callbacks.currentPlayerPositionMs(),
                playWhenReady = callbacks.isEffectivelyPlaying()
            )
        } else {
            playerQueueController.addMediaItems(playableIncomingQueue)
        }

        callbacks.publishPlayerEvent(forceNotification = false)
    }

    fun playNextQueue(incomingQueue: List<Music>) {
        val playableIncomingQueue = playableQueue(incomingQueue)
        if (playableIncomingQueue.isEmpty()) return

        callbacks.markPlaybackRestored()

        if (queueManager.queue.isEmpty()) {
            playIncomingQueueFromEmptyState(playableIncomingQueue)
            return
        }

        insertAfterCurrentTrack(playableIncomingQueue)
    }

    private fun playIncomingQueueFromEmptyState(incomingQueue: List<Music>) {
        queueManager.setQueue(
            newQueue = incomingQueue,
            requestedIndex = 0
        )

        queueManager.save()
        callbacks.updateNotificationSessionQueue()

        if (!callbacks.requestAudioFocus()) {
            return
        }

        playerQueueController.setPlayerQueue(
            queue = queueManager.queue,
            startIndex = 0,
            startPositionMs = 0L,
            playWhenReady = true
        )

        callbacks.refreshArtworkAndSession(force = true)
        callbacks.publishPlayerEvent(forceNotification = true)
    }

    private fun insertAfterCurrentTrack(incomingQueue: List<Music>) {
        val currentState = queueManager.state
        val wasPlayerQueueSynced = playerQueueController.isPlayerPlaylistSynced()

        val insertIndex = if (currentState.currentIndex == currentState.queue.lastIndex) {
            currentState.queue.size
        } else {
            currentState.currentIndex + 1
        }

        val nextQueue = currentState.queue
            .toMutableList()
            .apply {
                addAll(
                    index = insertIndex,
                    elements = incomingQueue
                )
            }
            .toList()

        queueManager.setQueue(
            newQueue = nextQueue,
            requestedIndex = currentState.currentIndex
        )

        queueManager.save()
        callbacks.updateNotificationSessionQueue()

        if (wasPlayerQueueSynced) {
            playerQueueController.addMediaItems(
                index = insertIndex,
                queue = incomingQueue
            )
        } else {
            playerQueueController.setPlayerQueue(
                queue = queueManager.queue,
                startIndex = queueManager.currentIndex,
                startPositionMs = callbacks.currentPlayerPositionMs(),
                playWhenReady = callbacks.isEffectivelyPlaying()
            )
        }

        callbacks.publishPlayerEvent(forceNotification = false)
    }

    fun replaceQueue(
        newQueue: List<Music>,
        requestedIndex: Int
    ) {
        val playableNewQueue = playableQueue(newQueue)

        if (playableNewQueue.isEmpty()) {
            callbacks.onReplaceWithEmptyQueue()
            return
        }

        callbacks.markPlaybackRestored()

        val previousQueue = queueManager.queue
        val previousTrackId = queueManager.currentTrack?.id
        val previousPositionMs = callbacks.currentPlayerPositionMs()
        val wasPlaying = callbacks.isEffectivelyPlaying()
        val wasPlayerQueueSynced = playerQueueController.isPlayerPlaylistSynced()

        val preservedIndex = previousTrackId
            ?.let { trackId ->
                playableNewQueue.indexOfFirst { music ->
                    music.id == trackId
                }
            }
            ?.takeIf { index ->
                index >= 0
            }

        val targetIndex = preservedIndex ?: remapRequestedIndex(
            originalQueue = newQueue,
            playableQueue = playableNewQueue,
            requestedIndex = requestedIndex
        )

        queueManager.setQueue(
            newQueue = playableNewQueue,
            requestedIndex = targetIndex
        )

        queueManager.save()
        callbacks.updateNotificationSessionQueue()

        val reorderedInPlace = if (
            wasPlayerQueueSynced &&
            callbacks.playerPlaybackStateIsNotIdle() &&
            playerQueueController.haveSameQueueContents(
                previousQueue = previousQueue,
                newQueue = playableNewQueue
            )
        ) {
            playerQueueController.applyInPlaceQueueReorder(
                previousQueue = previousQueue,
                newQueue = playableNewQueue
            )
        } else {
            false
        }

        if (!reorderedInPlace) {
            val shouldPreservePosition =
                previousTrackId != null &&
                        queueManager.currentTrack?.id == previousTrackId &&
                        callbacks.playerPlaybackStateIsNotIdle()

            playerQueueController.setPlayerQueue(
                queue = queueManager.queue,
                startIndex = queueManager.currentIndex,
                startPositionMs = if (shouldPreservePosition) {
                    previousPositionMs
                } else {
                    0L
                },
                playWhenReady = wasPlaying
            )
        }

        callbacks.refreshArtworkAndSession(force = true)
        callbacks.publishPlayerEvent(forceNotification = true)
    }

    fun playFromQueue(
        incomingQueue: List<Music>,
        incomingIndex: Int
    ) {
        val playableIncomingQueue = playableQueue(incomingQueue)
        if (playableIncomingQueue.isEmpty()) return

        callbacks.markPlaybackRestored()

        queueManager.setQueue(
            newQueue = playableIncomingQueue,
            requestedIndex = remapRequestedIndex(
                originalQueue = incomingQueue,
                playableQueue = playableIncomingQueue,
                requestedIndex = incomingIndex
            )
        )

        queueManager.save()
        callbacks.updateNotificationSessionQueue()

        if (!callbacks.requestAudioFocus()) {
            callbacks.publishPlayerEvent(forceNotification = false)
            return
        }

        playerQueueController.setPlayerQueue(
            queue = queueManager.queue,
            startIndex = queueManager.currentIndex,
            startPositionMs = 0L,
            playWhenReady = true
        )

        callbacks.refreshArtworkAndSession(force = true)
        callbacks.publishPlayerEvent(forceNotification = true)
    }

    fun playIndex(
        index: Int,
        playWhenReady: Boolean
    ) {
        if (index !in queueManager.queue.indices) return

        if (playWhenReady && !callbacks.requestAudioFocus()) {
            return
        }

        playResolvedIndex(
            index = index,
            playWhenReady = playWhenReady
        )
    }

    fun restartCurrentTrack() {
        val currentState = queueManager.state

        if (!currentState.hasCurrentTrack) return

        callbacks.resetTimedTransition()

        playerQueueController.seekTo(0L)

        if (!callbacks.isEffectivelyPlaying()) {
            callbacks.resumePlaybackInternal()
        } else {
            callbacks.publishPlayerEvent(forceNotification = false)
        }
    }

    private fun playableQueue(queue: List<Music>): List<Music> {
        return playerQueueController.filterPlayable(queue)
    }

    private fun remapRequestedIndex(
        originalQueue: List<Music>,
        playableQueue: List<Music>,
        requestedIndex: Int
    ): Int {
        val requestedTrackId = originalQueue.getOrNull(requestedIndex)?.id

        val requestedPlayableIndex = requestedTrackId
            ?.let { trackId ->
                playableQueue.indexOfFirst { music -> music.id == trackId }
            }
            ?.takeIf { index -> index >= 0 }

        if (requestedPlayableIndex != null) return requestedPlayableIndex

        return requestedIndex.coerceIn(
            0,
            playableQueue.lastIndex
        )
    }
}
