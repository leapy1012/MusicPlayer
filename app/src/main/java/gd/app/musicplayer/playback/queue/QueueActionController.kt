package gd.app.musicplayer.playback.queue

import androidx.media3.common.C
import gd.app.musicplayer.domain.model.Music

class QueueActionController(
    private val queueManager: PlaybackQueueManager,
    private val playerQueueController: PlayerQueueController,
    private val callbacks: QueueMutationCallbacks,
    private val remapQueueIndex: (List<Music>, List<Music>, Int) -> Int
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

        // App queue index is source of truth (original e0 cursor), then load ONE track.
        queueManager.updateCurrentIndex(index)

        playerQueueController.setPlayerQueue(
            queue = queueManager.queue,
            startIndex = index,
            startPositionMs = 0L,
            playWhenReady = playWhenReady
        )

        callbacks.resetPlaybackStatistics()
        callbacks.applyVolumeForPlaybackStart(playWhenReady)

        // Obfuscated parity intent: keep play-start path thin and let player transition
        // callbacks perform the heavy artwork/session/notification updates.
        callbacks.publishPlayerEvent(forceNotification = false)
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

        if (removedCurrent) {
            playerQueueController.setPlayerQueue(
                queue = queueManager.queue,
                startIndex = queueManager.currentIndex,
                startPositionMs = 0L,
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

        callbacks.persistCurrentTrackProgress(target)
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

        val movedCurrentTrack = fromIndex == currentState.currentIndex

        queueManager.move(
            fromIndex = fromIndex,
            toIndex = toIndex
        )

        queueManager.save()
        callbacks.updateNotificationSessionQueue()

        // Single-track player: reorder is memory-only; reload only if current track identity changed.
        val currentStillLoaded = playerQueueController.isCurrentTrackLoaded(queueManager.currentIndex)
        if (!currentStillLoaded) {
            playerQueueController.setPlayerQueue(
                queue = queueManager.queue,
                startIndex = queueManager.currentIndex,
                startPositionMs = callbacks.currentPlayerPositionMs(),
                playWhenReady = callbacks.isEffectivelyPlaying()
            )
        }

        if (movedCurrentTrack) {
            callbacks.refreshArtworkAndSession(force = true)
        }

        callbacks.publishPlayerEvent(forceNotification = true)
    }

    fun enqueue(incomingQueue: List<Music>) {
        val playableIncomingQueue = playableQueue(incomingQueue)
        if (playableIncomingQueue.isEmpty()) return

        val currentState = queueManager.state
        val wasEmpty = currentState.queue.isEmpty()

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

        // Original append: only load player when queue was empty; otherwise memory-only.
        if (wasEmpty) {
            playerQueueController.setPlayerQueue(
                queue = queueManager.queue,
                startIndex = queueManager.currentIndex.coerceAtLeast(0),
                startPositionMs = callbacks.currentPlayerPositionMs(),
                playWhenReady = callbacks.isEffectivelyPlaying()
            )
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

        // Keep current ExoPlayer item; inserted tracks load when they become current.
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

        val previousTrackIdentity = queueManager.currentTrack?.queueIdentity()
        val previousPositionMs = callbacks.currentPlayerPositionMs()
        val wasPlaying = callbacks.isEffectivelyPlaying()

        val targetIndex = remapQueueIndex(newQueue, playableNewQueue, requestedIndex)

        queueManager.setQueue(
            newQueue = playableNewQueue,
            requestedIndex = targetIndex
        )

        queueManager.save()
        callbacks.updateNotificationSessionQueue()

        val shouldPreservePosition =
            previousTrackIdentity != null &&
                queueManager.currentTrack?.queueIdentity() == previousTrackIdentity &&
                callbacks.playerPlaybackStateIsNotIdle() &&
                playerQueueController.isCurrentTrackLoaded(queueManager.currentIndex)

        if (!shouldPreservePosition) {
            playerQueueController.setPlayerQueue(
                queue = queueManager.queue,
                startIndex = queueManager.currentIndex,
                startPositionMs = if (
                    previousTrackIdentity != null &&
                    queueManager.currentTrack?.queueIdentity() == previousTrackIdentity
                ) {
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

        val remappedIndex = remapQueueIndex(
            incomingQueue,
            playableIncomingQueue,
            incomingIndex
        ).coerceIn(0, playableIncomingQueue.lastIndex)

        // Original e0.B + com.lb.library.c.a: same id-order list → index-only, no DB rewrite.
        val sameOrderedList = playerQueueController.haveSameOrderedTrackIds(
            previousQueue = queueManager.queue,
            newQueue = playableIncomingQueue
        )

        if (sameOrderedList && queueManager.queue.isNotEmpty()) {
            if (!callbacks.requestAudioFocus()) {
                callbacks.publishPlayerEvent(forceNotification = false)
                return
            }

            playResolvedIndex(
                index = remappedIndex,
                playWhenReady = true
            )
            callbacks.refreshArtworkAndSession(force = true)
            return
        }

        queueManager.setQueue(
            newQueue = playableIncomingQueue,
            requestedIndex = remappedIndex
        )

        // Original QueueSaver: persist only when queue content changed (debounced).
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

}
