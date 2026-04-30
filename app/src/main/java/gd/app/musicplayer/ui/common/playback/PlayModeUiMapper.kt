package gd.app.musicplayer.ui.common.playback

import gd.app.musicplayer.R
import gd.app.musicplayer.playback.PlaybackMode

object PlayModeUiMapper {

    fun iconRes(mode: Int): Int {
        return when (mode) {
            PlaybackMode.SINGLE -> R.drawable.vector_mode_single
            PlaybackMode.LOOP_ALL -> R.drawable.vector_mode_circle
            PlaybackMode.SHUFFLE_ALL -> R.drawable.vector_mode_random
            else -> R.drawable.vector_mode_order
        }
    }

    fun labelRes(mode: Int): Int {
        return when (mode) {
            PlaybackMode.SINGLE -> R.string.play_mode_single
            PlaybackMode.LOOP_ALL -> R.string.play_mode_list_cycle
            PlaybackMode.SHUFFLE_ALL -> R.string.play_mode_list_rand
            else -> R.string.play_mode_list
        }
    }
}
