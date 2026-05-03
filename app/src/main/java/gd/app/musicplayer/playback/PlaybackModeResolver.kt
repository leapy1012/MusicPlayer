package gd.app.musicplayer.playback

import android.content.Context
import gd.app.musicplayer.playback.PlaybackMode
import gd.app.musicplayer.util.PreferenceUtil

class PlaybackModeResolver(private val context: Context) {
    fun resolveNextIndex(queueSize: Int, currentIndex: Int): Int? {
        if (queueSize <= 0) return null
        val activeIndex = currentIndex.takeIf { it in 0 until queueSize } ?: return 0
        return when (PreferenceUtil.getInstance(context).getPlayMode()) {
            PlaybackMode.SINGLE -> activeIndex
            PlaybackMode.SHUFFLE_ALL -> randomOtherIndex(queueSize, activeIndex) ?: activeIndex
            PlaybackMode.LOOP_ALL -> (activeIndex + 1) % queueSize
            else -> if (activeIndex == queueSize - 1) null else activeIndex + 1
        }
    }

    fun resolvePreviousIndex(queueSize: Int, currentIndex: Int, shouldRestartCurrent: Boolean): Int? {
        if (queueSize <= 0) return null
        val activeIndex = currentIndex.takeIf { it in 0 until queueSize } ?: return 0
        if (shouldRestartCurrent) return activeIndex
        return when (PreferenceUtil.getInstance(context).getPlayMode()) {
            PlaybackMode.SINGLE -> activeIndex
            PlaybackMode.SHUFFLE_ALL -> randomOtherIndex(queueSize, activeIndex) ?: activeIndex
            PlaybackMode.LOOP_ALL -> if (activeIndex == 0) queueSize - 1 else activeIndex - 1
            else -> if (activeIndex == 0) 0 else activeIndex - 1
        }
    }

    fun cyclePlaybackMode() {
        val preference = PreferenceUtil.getInstance(context)
        val nextMode = when (preference.getPlayMode()) {
            PlaybackMode.SINGLE -> PlaybackMode.ORDER
            PlaybackMode.ORDER -> PlaybackMode.LOOP_ALL
            PlaybackMode.LOOP_ALL -> PlaybackMode.SHUFFLE_ALL
            else -> PlaybackMode.SINGLE
        }
        preference.setPlayMode(nextMode)
    }

    fun setPlaybackMode(mode: Int) {
        PreferenceUtil.getInstance(context).setPlayMode(mode)
    }

    private fun randomOtherIndex(queueSize: Int, currentIndex: Int): Int? {
        if (queueSize <= 1) return null
        return (0 until queueSize).filterNot { it == currentIndex }.random()
    }
}
