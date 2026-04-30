package gd.app.musicplayer.playback.queue

import android.os.Looper
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.playback.PlaybackMode
import kotlin.random.Random

class PlaybackQueue(
    private val itemMatcher: QueueItemMatcher<Music> = MusicQueueItemMatcher,
    private val requireMainThread: Boolean = true,
) {
    private val mutableQueue = mutableListOf<Music>()
    private var playMode: Int = PlaybackMode.ORDER

    var currentIndex: Int = -1
        private set

    var currentItem: Music? = null
        private set

    val queue: List<Music>
        get() = mutableQueue.toList()

    val size: Int
        get() = mutableQueue.size

    fun setQueue(items: List<Music>, startIndex: Int): QueueUpdateResult {
        checkThread()
        val oldQueue = mutableQueue.toList()
        val oldItem = currentItem
        val queueChanged = oldQueue != items
        if (queueChanged) {
            mutableQueue.clear()
            mutableQueue.addAll(items)
        }
        select(startIndex)
        return QueueUpdateResult.success(
            currentItemChanged = queueChanged || !itemMatcher.areSame(oldItem, currentItem),
            queueChanged = queueChanged,
        )
    }

    fun moveTo(index: Int): QueueUpdateResult {
        checkThread()
        val oldItem = currentItem
        select(index)
        return QueueUpdateResult.success(
            currentItemChanged = !itemMatcher.areSame(oldItem, currentItem),
            queueChanged = false,
        )
    }

    fun moveToNext(usePlayMode: Boolean): QueueUpdateResult {
        checkThread()
        if (mutableQueue.isEmpty()) return QueueUpdateResult.NoChange
        val oldIndex = currentIndex
        val nextIndex = resolveNextIndex(usePlayMode, playMode) ?: return QueueUpdateResult(
            currentItemChanged = oldIndex != currentIndex,
            dataChanged = oldIndex != currentIndex,
            queueChanged = false,
        )
        select(nextIndex)
        return QueueUpdateResult.success(
            currentItemChanged = oldIndex != currentIndex,
            queueChanged = false,
        )
    }

    fun moveToPrevious(): QueueUpdateResult {
        checkThread()
        if (mutableQueue.isEmpty()) return QueueUpdateResult.NoChange
        val oldItem = currentItem
        select(resolvePreviousIndex(playMode))
        return QueueUpdateResult.success(
            currentItemChanged = !itemMatcher.areSame(oldItem, currentItem),
            queueChanged = false,
        )
    }

    fun addToEnd(item: Music): QueueUpdateResult {
        checkThread()
        val wasEmpty = mutableQueue.isEmpty()
        mutableQueue.add(item)
        if (wasEmpty) select(0)
        return QueueUpdateResult.success(currentItemChanged = wasEmpty, queueChanged = true)
    }

    fun addAllToEnd(items: List<Music>): QueueUpdateResult {
        checkThread()
        if (items.isEmpty()) return QueueUpdateResult.NoChange
        val wasEmpty = mutableQueue.isEmpty()
        mutableQueue.addAll(items)
        if (wasEmpty) select(0)
        return QueueUpdateResult.success(currentItemChanged = wasEmpty, queueChanged = true)
    }

    fun addNext(item: Music): QueueUpdateResult {
        return addAllNext(listOf(item))
    }

    fun addAllNext(items: List<Music>): QueueUpdateResult {
        checkThread()
        if (items.isEmpty()) return QueueUpdateResult.NoChange
        if (mutableQueue.isEmpty()) {
            mutableQueue.addAll(items)
            select(0)
            return QueueUpdateResult.success(currentItemChanged = true, queueChanged = true)
        }
        val insertIndex = if (currentIndex == mutableQueue.lastIndex) {
            mutableQueue.size
        } else {
            currentIndex + 1
        }
        mutableQueue.addAll(insertIndex, items)
        return QueueUpdateResult.success(currentItemChanged = false, queueChanged = true)
    }

    fun playOrAppend(item: Music): QueueUpdateResult {
        checkThread()
        val wasEmpty = mutableQueue.isEmpty()
        var targetIndex = mutableQueue.indexOfFirst { itemMatcher.areSame(it, item) }
        val queueChanged = targetIndex == -1
        if (queueChanged) {
            mutableQueue.add(item)
            targetIndex = mutableQueue.lastIndex
        }
        val currentChanged = wasEmpty || currentIndex != targetIndex
        if (currentChanged) select(targetIndex)
        return QueueUpdateResult.success(currentItemChanged = currentChanged, queueChanged = queueChanged)
    }

    fun removeAt(index: Int): QueueUpdateResult {
        return removeWhere { _, itemIndex -> itemIndex == index }
    }

    fun removeItems(items: List<Music>): QueueUpdateResult {
        if (items.isEmpty()) return QueueUpdateResult.NoChange
        return removeWhere { item, _ -> items.any { itemMatcher.areSame(item, it) } }
    }

    fun removeWhere(predicate: (Music, Int) -> Boolean): QueueUpdateResult {
        checkThread()
        if (mutableQueue.isEmpty()) return QueueUpdateResult.NoChange
        val oldItem = currentItem
        var newCurrentIndex = currentIndex
        var removedCurrent = false
        val removedItems = mutableListOf<Music>()
        var index = 0
        while (index < mutableQueue.size) {
            if (predicate(mutableQueue[index], index)) {
                removedItems.add(mutableQueue.removeAt(index))
                when {
                    index < newCurrentIndex -> newCurrentIndex--
                    index == newCurrentIndex -> removedCurrent = true
                }
            } else {
                index++
            }
        }
        if (removedItems.isEmpty()) return QueueUpdateResult.NoChange
        if (removedCurrent) {
            select(newCurrentIndex)
        } else {
            currentIndex = if (mutableQueue.isEmpty()) -1 else newCurrentIndex.coerceIn(0, mutableQueue.lastIndex)
            currentItem = mutableQueue.getOrNull(currentIndex)
        }
        return QueueUpdateResult.success(
            currentItemChanged = !itemMatcher.areSame(oldItem, currentItem),
            queueChanged = true,
        )
    }

    fun clear(): QueueUpdateResult {
        checkThread()
        if (mutableQueue.isEmpty()) return QueueUpdateResult.NoChange
        mutableQueue.clear()
        select(0)
        return QueueUpdateResult.success(currentItemChanged = true, queueChanged = true)
    }

    fun swap(fromIndex: Int, toIndex: Int): QueueUpdateResult {
        checkThread()
        if (fromIndex == toIndex || fromIndex !in mutableQueue.indices || toIndex !in mutableQueue.indices) {
            return QueueUpdateResult.NoChange
        }
        java.util.Collections.swap(mutableQueue, fromIndex, toIndex)
        currentIndex = when (currentIndex) {
            fromIndex -> toIndex
            toIndex -> fromIndex
            else -> currentIndex
        }
        currentItem = mutableQueue.getOrNull(currentIndex)
        return QueueUpdateResult.success(currentItemChanged = false, queueChanged = false)
    }

    fun shuffleQueue(items: List<Music>, playMode: Int): QueueUpdateResult {
        checkThread()
        setPlayMode(playMode)
        mutableQueue.clear()
        if (items.isEmpty()) {
            select(0)
        } else {
            mutableQueue.addAll(items)
            select(Random(System.nanoTime()).nextInt(mutableQueue.size))
        }
        return QueueUpdateResult.success(currentItemChanged = true, queueChanged = true)
    }

    fun setPlayMode(playMode: Int) {
        checkThread()
        this.playMode = playMode
    }

    fun nextIndex(mode: Int = playMode, fromAutoTransition: Boolean = false): Int? {
        return resolveNextIndex(usePlayMode = true, mode = mode)
    }

    fun previousIndex(mode: Int = playMode, restartAtBeginning: Boolean = false): Int? {
        return if (restartAtBeginning) {
            currentIndex.takeIf { it in mutableQueue.indices } ?: 0
        } else {
            resolvePreviousIndex(mode)
        }
    }

    fun updateItem(item: Music, updater: (old: Music, new: Music) -> Music): QueueUpdateResult {
        checkThread()
        if (mutableQueue.isEmpty()) return QueueUpdateResult.NoChange
        var currentChanged = false
        var changed = false
        mutableQueue.replaceAll { old ->
            if (itemMatcher.areSame(old, item)) {
                changed = true
                if (itemMatcher.areSame(old, currentItem)) currentChanged = true
                updater(old, item)
            } else {
                old
            }
        }
        if (currentChanged) currentItem = mutableQueue.getOrNull(currentIndex)
        return QueueUpdateResult.success(currentItemChanged = currentChanged, queueChanged = changed)
    }

    fun updateItems(items: List<Music>, updater: (old: Music, new: Music) -> Music): QueueUpdateResult {
        checkThread()
        if (items.isEmpty() || mutableQueue.isEmpty()) return QueueUpdateResult.NoChange
        var currentChanged = false
        var changed = false
        mutableQueue.replaceAll { old ->
            val newItem = items.firstOrNull { itemMatcher.areSame(old, it) }
            if (newItem != null) {
                changed = true
                if (itemMatcher.areSame(old, currentItem)) currentChanged = true
                updater(old, newItem)
            } else {
                old
            }
        }
        if (currentChanged) currentItem = mutableQueue.getOrNull(currentIndex)
        return QueueUpdateResult.success(currentItemChanged = currentChanged, queueChanged = changed)
    }

    fun replacePreservingCurrent(items: List<Music>, requestedIndex: Int): QueueUpdateResult {
        checkThread()
        val oldItem = currentItem
        val oldQueue = mutableQueue.toList()
        mutableQueue.clear()
        mutableQueue.addAll(items)
        val preservedIndex = oldItem?.let { current ->
            mutableQueue.indexOfFirst { itemMatcher.areSame(it, current) }.takeIf { it >= 0 }
        }
        select(preservedIndex ?: requestedIndex)
        return QueueUpdateResult.success(
            currentItemChanged = !itemMatcher.areSame(oldItem, currentItem),
            queueChanged = oldQueue != mutableQueue,
        )
    }

    private fun resolveNextIndex(usePlayMode: Boolean, mode: Int): Int? {
        val activeIndex = currentIndex.takeIf { it in mutableQueue.indices } ?: return 0
        if (!usePlayMode) {
            return if (activeIndex == mutableQueue.lastIndex) null else activeIndex + 1
        }
        return when (mode) {
            PlaybackMode.SINGLE -> activeIndex
            PlaybackMode.SHUFFLE_ALL -> randomOtherIndex(activeIndex) ?: activeIndex
            PlaybackMode.LOOP_ALL -> (activeIndex + 1) % mutableQueue.size
            else -> if (activeIndex == mutableQueue.lastIndex) null else activeIndex + 1
        }
    }

    private fun resolvePreviousIndex(mode: Int): Int {
        val activeIndex = currentIndex.takeIf { it in mutableQueue.indices } ?: return 0
        return when (mode) {
            PlaybackMode.SINGLE -> activeIndex
            PlaybackMode.SHUFFLE_ALL -> randomOtherIndex(activeIndex) ?: activeIndex
            PlaybackMode.LOOP_ALL -> if (activeIndex == 0) mutableQueue.lastIndex else activeIndex - 1
            else -> if (activeIndex == 0) 0 else activeIndex - 1
        }
    }

    private fun select(index: Int) {
        if (mutableQueue.isEmpty()) {
            currentIndex = -1
            currentItem = null
        } else {
            currentIndex = index.coerceIn(0, mutableQueue.lastIndex)
            currentItem = mutableQueue[currentIndex]
        }
    }

    private fun randomOtherIndex(currentIndex: Int): Int? {
        if (mutableQueue.size <= 1) return null
        return mutableQueue.indices.filterNot { it == currentIndex }.random(Random(System.nanoTime()))
    }

    private fun checkThread() {
        if (requireMainThread && Looper.myLooper() != Looper.getMainLooper()) {
            throw IllegalThreadStateException("PlaybackQueue must be mutated on the main thread.")
        }
    }
}
