package gd.app.musicplayer.playback

import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import gd.app.musicplayer.di.ApplicationScope
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
                    if (activeIndex == queueSize - 1) activeIndex else activeIndex + 1
                }
            }

            PlaybackMode.SHUFFLE_ALL -> {
                randomOtherIndex(queueSize, activeIndex) ?: activeIndex
            }

            PlaybackMode.LOOP_ALL -> {
                (activeIndex + 1) % queueSize
            }

            else -> {
                if (activeIndex == queueSize - 1) null else activeIndex + 1
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
                activeIndex
            }

            PlaybackMode.SHUFFLE_ALL -> {
                randomOtherIndex(queueSize, activeIndex) ?: activeIndex
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

        return (0 until queueSize)
            .filterNot { index -> index == currentIndex }
            .random()
    }
}
