package gd.app.musicplayer.domain.usecase.playmode

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import gd.app.musicplayer.playback.PlaybackMode
import javax.inject.Inject

class CyclePlayModeUseCase @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val preferencesRepo: SettingPreferencesDataStore
) {
    suspend operator fun invoke() {
        val nextMode = preferencesRepo.cyclePlayMode()
        ToastUtil.show(
            appContext,
            playModeLabelRes(nextMode)
        )
    }

    private fun playModeLabelRes(mode: Int): Int {
        return when (mode) {
            PlaybackMode.SINGLE -> R.string.loop_single
            PlaybackMode.LOOP_ALL -> R.string.play_mode_list_cycle
            PlaybackMode.SHUFFLE_ALL -> R.string.play_mode_list_rand
            else -> R.string.play_mode_list
        }
    }
}
