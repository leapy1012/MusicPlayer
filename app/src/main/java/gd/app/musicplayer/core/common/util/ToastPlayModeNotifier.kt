package gd.app.musicplayer.core.common.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.repository.PlayModeNotifier
import gd.app.musicplayer.playback.PlaybackMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToastPlayModeNotifier @Inject constructor(
    @ApplicationContext private val appContext: Context
) : PlayModeNotifier {

    override fun notifyModeChanged(mode: Int) {
        ToastUtil.show(appContext, labelRes(mode))
    }

    private fun labelRes(mode: Int): Int {
        return when (mode) {
            PlaybackMode.SINGLE -> R.string.loop_single
            PlaybackMode.LOOP_ALL -> R.string.play_mode_list_cycle
            PlaybackMode.SHUFFLE_ALL -> R.string.play_mode_list_rand
            else -> R.string.play_mode_list
        }
    }
}
