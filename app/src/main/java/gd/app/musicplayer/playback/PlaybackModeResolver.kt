package gd.app.musicplayer.playback

import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import gd.app.musicplayer.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.random.Random

@Singleton
class PlaybackModeResolver @Inject constructor(
    private val settingsPreferenceOps: SettingPreferencesDataStore,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) {

    @Volatile
    private var currentPlayMode: Int = PlaybackMode.ORDER
    private val shuffleHistory = mutableListOf<Int>()
    private var shuffleHistoryCursor = -1

    init {
        observePlayMode()
    }

    fun resolveNextIndex(
        queueSize: Int,
        currentIndex: Int,
        fromAutoTransition: Boolean
    ): Int? {
        if (queueSize <= 0) return null

        val activeIndex = currentIndex.takeIf { it in 0 until queueSize } ?: return 0

        return when (currentPlayMode) {
            PlaybackMode.SINGLE -> {
                if (fromAutoTransition) {
                    activeIndex
                } else {
                    if (activeIndex >= queueSize - 1) 0 else activeIndex + 1
                }
            }

            PlaybackMode.SHUFFLE_ALL -> {
                resolveShuffleNextIndex(queueSize, activeIndex)
            }

            PlaybackMode.LOOP_ALL -> {
                (activeIndex + 1) % queueSize
            }

            else -> {
                (activeIndex + 1).coerceAtMost(queueSize - 1)
            }
        }
    }

    fun resolvePreviousIndex(
        queueSize: Int,
        currentIndex: Int,
        shouldRestartCurrent: Boolean
    ): Int? {
        if (queueSize <= 0) return null

        val activeIndex = currentIndex.takeIf { it in 0 until queueSize } ?: return 0

        if (shouldRestartCurrent) {
            return activeIndex
        }

        return when (currentPlayMode) {
            PlaybackMode.SINGLE -> {
                if (activeIndex <= 0) queueSize - 1 else activeIndex - 1
            }

            PlaybackMode.SHUFFLE_ALL -> {
                resolveShufflePreviousIndex(queueSize, activeIndex)
            }

            PlaybackMode.LOOP_ALL -> {
                if (activeIndex == 0) queueSize - 1 else activeIndex - 1
            }

            else -> {
                if (activeIndex == 0) 0 else activeIndex - 1
            }
        }
    }

    fun cyclePlaybackMode() {
        val nextMode = when (currentPlayMode) {
            PlaybackMode.SINGLE -> PlaybackMode.ORDER
            PlaybackMode.ORDER -> PlaybackMode.LOOP_ALL
            PlaybackMode.LOOP_ALL -> PlaybackMode.SHUFFLE_ALL
            else -> PlaybackMode.SINGLE
        }

        setPlaybackMode(nextMode)
    }

    fun setPlaybackMode(mode: Int) {
        currentPlayMode = mode

        applicationScope.launch {
            settingsPreferenceOps.updatePlayMode(mode)
        }
    }

    fun getPlaybackMode(): Int {
        return currentPlayMode
    }

    private fun observePlayMode() {
        applicationScope.launch {
            settingsPreferenceOps.observePlayMode()
                .distinctUntilChanged()
                .collect { mode ->
                    currentPlayMode = mode
                }
        }
    }

    private fun randomOtherIndex(
        queueSize: Int,
        currentIndex: Int
    ): Int? {
        if (queueSize <= 1) return null

        var next: Int
        do {
            next = Random.nextInt(queueSize)
        } while (next == currentIndex)
        return next
    }

    private fun resolveShuffleNextIndex(
        queueSize: Int,
        currentIndex: Int
    ): Int {
        if (queueSize <= 1) return 0

        if (shuffleHistoryCursor < shuffleHistory.lastIndex) {
            shuffleHistoryCursor++
            return shuffleHistory[shuffleHistoryCursor].coerceIn(0, queueSize - 1)
        }

        val next = randomOtherIndex(queueSize, currentIndex) ?: 0
        shuffleHistory += next
        shuffleHistoryCursor = shuffleHistory.lastIndex
        trimShuffleHistory()
        return next
    }

    private fun resolveShufflePreviousIndex(
        queueSize: Int,
        currentIndex: Int
    ): Int {
        if (queueSize <= 1) return 0

        if (shuffleHistoryCursor > 0) {
            shuffleHistoryCursor--
            return shuffleHistory[shuffleHistoryCursor].coerceIn(0, queueSize - 1)
        }

        val previous = randomOtherIndex(queueSize, currentIndex) ?: 0
        shuffleHistory.add(0, previous)
        shuffleHistoryCursor = 0
        trimShuffleHistory()
        return previous
    }

    private fun trimShuffleHistory() {
        if (shuffleHistory.size <= MAX_SHUFFLE_HISTORY_SIZE) return

        val overflow = shuffleHistory.size - MAX_SHUFFLE_HISTORY_SIZE
        repeat(overflow) {
            shuffleHistory.removeAt(0)
        }

        shuffleHistoryCursor = (shuffleHistoryCursor - overflow).coerceAtLeast(0)
    }

    companion object {
        private const val MAX_SHUFFLE_HISTORY_SIZE = 100
    }
}
