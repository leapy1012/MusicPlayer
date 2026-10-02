package gd.app.musicplayer.playback

import gd.app.musicplayer.domain.model.Music
import java.util.Collections
import java.util.Random

/**
 * Shuffle order bag matching original [a7.e]: a permutation of queue tokens with a cursor.
 * Next/prev walk the bag; at wrap the bag is reshuffled (original mode-param 1 path used for
 * UI shuffle). Tokens stay stable across list identity so duplicate tracks remain distinct.
 */
internal class ShuffleBag(
    private val random: Random = Random()
) {

    private val bag = mutableListOf<Int>()
    private var cursor: Int = 0
    private var originCursor: Int = 0
    private var pendingReshuffle: Boolean = false
    /** Original [a7.e.f219j] — previous steps delay wrap reshuffle once. */
    private var previousStepBudget: Int = 0

    val isInitialized: Boolean
        get() = bag.isNotEmpty()

    fun clear() {
        bag.clear()
        cursor = 0
        originCursor = 0
        pendingReshuffle = false
        previousStepBudget = 0
    }

    /**
     * Original [a7.e.m]: current first, shuffle the rest, rotate so current sits at [currentIndex].
     */
    fun initialize(queue: List<Music>, currentIndex: Int) {
        if (queue.isEmpty()) {
            clear()
            return
        }

        val safeIndex = currentIndex.coerceIn(0, queue.lastIndex)
        val currentToken = queue[safeIndex].queueToken

        bag.clear()
        bag.add(currentToken)
        for (i in queue.indices) {
            if (i != safeIndex) {
                bag.add(queue[i].queueToken)
            }
        }

        if (bag.size > 2) {
            partialShuffle(from = 1, toExclusive = bag.size)
        }

        if (safeIndex != 0) {
            Collections.rotate(bag, safeIndex)
        }

        cursor = safeIndex
        originCursor = safeIndex
        pendingReshuffle = false
        previousStepBudget = 0
    }

    fun syncCursorToCurrent(queue: List<Music>, currentIndex: Int) {
        if (!isInitialized || queue.isEmpty()) return
        val token = queue[currentIndex.coerceIn(0, queue.lastIndex)].queueToken
        val bagIndex = bag.indexOf(token)
        if (bagIndex >= 0) {
            cursor = bagIndex
        }
    }

    /**
     * Original [a7.e.i]: append then shuffle the newly added region after the cursor.
     */
    fun onAppended(added: List<Music>) {
        if (added.isEmpty()) return
        pendingReshuffle = false
        if (!isInitialized) return

        val start = bag.size
        bag.addAll(added.map { it.queueToken })
        if (bag.size > cursor + 1) {
            partialShuffle(from = cursor + 1, toExclusive = bag.size)
        } else if (start < bag.size) {
            partialShuffle(from = start, toExclusive = bag.size)
        }
    }

    /**
     * Original [a7.e.j]: insert immediately after the cursor (play-next).
     */
    fun onInsertedForNext(added: List<Music>) {
        if (added.isEmpty()) return
        pendingReshuffle = false
        if (!isInitialized) {
            return
        }
        val insertAt = if (bag.isEmpty()) 0 else cursor + 1
        bag.addAll(insertAt, added.map { it.queueToken })
    }

    fun onQueueCleared() {
        clear()
    }

    /**
     * Rebuild after removals / moves when bag tokens no longer match the live queue.
     */
    fun rebuildIfStale(queue: List<Music>, currentIndex: Int) {
        if (queue.isEmpty()) {
            clear()
            return
        }
        val liveTokens = queue.map { it.queueToken }.toHashSet()
        val bagStillValid = bag.isNotEmpty() &&
            bag.size == queue.size &&
            bag.all { it in liveTokens }
        if (!bagStillValid) {
            initialize(queue, currentIndex)
        } else {
            syncCursorToCurrent(queue, currentIndex)
        }
    }

    /**
     * @return queue index of the next track, or null when auto-transition should stop
     *         (order-style end). Manual next never returns null for a non-empty bag.
     */
    fun moveToNext(queue: List<Music>, fromAutoTransition: Boolean): Int? {
        if (queue.isEmpty()) return null
        rebuildIfStale(queue, currentIndexOfCursor(queue) ?: 0)

        if (pendingReshuffle) {
            reshuffleKeepingCurrent(queue)
            pendingReshuffle = false
        }

        val allowWrapReshuffle: Boolean
        if (previousStepBudget > 0) {
            previousStepBudget--
            allowWrapReshuffle = false
        } else {
            allowWrapReshuffle = true
        }

        // Original shuffle UI uses mode-param 1: always advance; reshuffle when wrapping to origin.
        cursor = (cursor + 1) % bag.size
        if (allowWrapReshuffle && cursor == originCursor) {
            reshuffleKeepingCurrent(queue)
        }

        return indexOfToken(queue, bag[cursor])
    }

    fun moveToPrevious(queue: List<Music>): Int? {
        if (queue.isEmpty()) return null
        rebuildIfStale(queue, currentIndexOfCursor(queue) ?: 0)

        previousStepBudget++
        cursor -= 1
        if (cursor < 0) {
            cursor = bag.lastIndex
        }
        return indexOfToken(queue, bag[cursor])
    }

    private fun reshuffleKeepingCurrent(queue: List<Music>) {
        val currentToken = bag.getOrNull(cursor) ?: return
        val currentQueueIndex = indexOfToken(queue, currentToken) ?: 0
        initialize(queue, currentQueueIndex)
    }

    private fun currentIndexOfCursor(queue: List<Music>): Int? {
        if (bag.isEmpty()) return null
        return indexOfToken(queue, bag[cursor.coerceIn(0, bag.lastIndex)])
    }

    private fun indexOfToken(queue: List<Music>, token: Int): Int? {
        val index = queue.indexOfFirst { it.queueToken == token }
        return index.takeIf { it >= 0 }
    }

    /** Original [a7.e.t] Fisher–Yates on [from, toExclusive). */
    private fun partialShuffle(from: Int, toExclusive: Int) {
        var end = toExclusive
        while (end > from + 1) {
            val last = end - 1
            val swapWith = random.nextInt(last - from) + from
            Collections.swap(bag, last, swapWith)
            end = last
        }
    }
}
