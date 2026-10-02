package gd.app.musicplayer.playback

import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import gd.app.musicplayer.di.ApplicationScope
import gd.app.musicplayer.domain.model.Music
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Singleton
class PlaybackModeResolver @Inject constructor(
    private val settingsPreferenceOps: SettingPreferencesDataStore,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) {

    @Volatile
    private var currentPlayMode: Int = PlaybackMode.ORDER

    /**
     * Original [y6.y.f16757i]: after Previous, auto-next also walks backward until cleared.
     */
    @Volatile
    private var reverseSticky: Boolean = false

    private val shuffleBag = ShuffleBag()

    init {
        observePlayMode()
    }

    fun resolveNextIndex(
        queue: List<Music>,
        currentIndex: Int,
        fromAutoTransition: Boolean
    ): Int? {
        if (queue.isEmpty()) return null

        if (reverseSticky && fromAutoTransition) {
            return resolvePreviousIndex(
                queue = queue,
                currentIndex = currentIndex,
                shouldRestartCurrent = false
            )
        }

        if (!fromAutoTransition) {
            clearReverseSticky()
        }

        val activeIndex = currentIndex.takeIf { it in queue.indices } ?: return 0

        return when (currentPlayMode) {
            PlaybackMode.SINGLE -> {
                if (fromAutoTransition) {
                    activeIndex
                } else {
                    if (activeIndex == queue.lastIndex) activeIndex else activeIndex + 1
                }
            }

            PlaybackMode.SHUFFLE_ALL -> {
                ensureShuffleBag(queue, activeIndex)
                shuffleBag.moveToNext(queue, fromAutoTransition)
            }

            PlaybackMode.LOOP_ALL -> {
                (activeIndex + 1) % queue.size
            }

            else -> {
                if (activeIndex == queue.lastIndex) null else activeIndex + 1
            }
        }
    }

    fun resolvePreviousIndex(
        queue: List<Music>,
        currentIndex: Int,
        shouldRestartCurrent: Boolean
    ): Int? {
        if (queue.isEmpty()) return null

        val activeIndex = currentIndex.takeIf { it in queue.indices } ?: return 0

        if (shouldRestartCurrent) {
            return activeIndex
        }

        return when (currentPlayMode) {
            PlaybackMode.SHUFFLE_ALL -> {
                ensureShuffleBag(queue, activeIndex)
                shuffleBag.moveToPrevious(queue)
            }

            // Original [a7.c.h]: always wrap to previous, including SINGLE.
            PlaybackMode.SINGLE,
            PlaybackMode.LOOP_ALL -> {
                if (activeIndex == 0) queue.lastIndex else activeIndex - 1
            }

            else -> {
                if (activeIndex == 0) 0 else activeIndex - 1
            }
        }
    }

    fun markPreviousNavigated() {
        reverseSticky = true
    }

    fun clearReverseSticky() {
        reverseSticky = false
    }

    fun onQueueInitialized(queue: List<Music>, currentIndex: Int) {
        clearReverseSticky()
        if (currentPlayMode == PlaybackMode.SHUFFLE_ALL) {
            shuffleBag.initialize(queue, currentIndex)
        } else {
            shuffleBag.clear()
        }
    }

    fun onQueueCleared() {
        clearReverseSticky()
        shuffleBag.onQueueCleared()
    }

    fun onTracksAppended(queue: List<Music>, added: List<Music>) {
        if (currentPlayMode != PlaybackMode.SHUFFLE_ALL) return
        if (!shuffleBag.isInitialized) {
            shuffleBag.initialize(queue, (queue.size - added.size).coerceAtLeast(0))
        } else {
            shuffleBag.onAppended(added)
        }
    }

    fun onTracksInsertedForNext(added: List<Music>) {
        if (currentPlayMode != PlaybackMode.SHUFFLE_ALL) return
        shuffleBag.onInsertedForNext(added)
    }

    fun onQueueMutated(queue: List<Music>, currentIndex: Int) {
        clearReverseSticky()
        if (currentPlayMode == PlaybackMode.SHUFFLE_ALL) {
            shuffleBag.rebuildIfStale(queue, currentIndex)
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
        clearReverseSticky()
        if (mode != PlaybackMode.SHUFFLE_ALL) {
            shuffleBag.clear()
        } else {
            // Bag initializes lazily on next resolve with a live queue.
            shuffleBag.clear()
        }

        applicationScope.launch {
            settingsPreferenceOps.updatePlayMode(mode)
        }
    }

    fun getPlaybackMode(): Int {
        return currentPlayMode
    }

    private fun ensureShuffleBag(queue: List<Music>, currentIndex: Int) {
        if (!shuffleBag.isInitialized) {
            shuffleBag.initialize(queue, currentIndex)
        } else {
            shuffleBag.rebuildIfStale(queue, currentIndex)
        }
    }

    private fun observePlayMode() {
        applicationScope.launch {
            settingsPreferenceOps.observePlayMode()
                .distinctUntilChanged()
                .collect { mode ->
                    if (mode != currentPlayMode) {
                        currentPlayMode = mode
                        clearReverseSticky()
                        if (mode != PlaybackMode.SHUFFLE_ALL) {
                            shuffleBag.clear()
                        } else {
                            shuffleBag.clear()
                        }
                    }
                }
        }
    }
}
