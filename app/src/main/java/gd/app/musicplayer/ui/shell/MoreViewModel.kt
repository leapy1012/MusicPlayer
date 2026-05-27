package gd.app.musicplayer.ui.shell

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.usecase.playmode.CyclePlayModeUseCase
import gd.app.musicplayer.domain.usecase.playmode.ObservePlayModeUseCase
import gd.app.musicplayer.domain.usecase.preferences.ObserveEqualizerPresetNameUseCase
import gd.app.musicplayer.domain.usecase.preferences.ObserveShowHiddenFoldersUseCase
import gd.app.musicplayer.playback.PlaybackMode
import gd.app.musicplayer.playback.timer.SleepTimerManager
import gd.app.musicplayer.playback.timer.SleepTimerState
import gd.app.musicplayer.ui.common.playback.PlayModeUiMapper
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MoreUiState(
    val playModeLabelRes: Int = R.string.play_mode_list,
    val playModeIconRes: Int = R.drawable.vector_mode_order,
    val sleepSummary: String = "",
    val isHiddenFoldersVisible: Boolean = true,
    val equalizerSummary: String = ""
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    observePlayModeUseCase: ObservePlayModeUseCase,
    observeShowHiddenFoldersUseCase: ObserveShowHiddenFoldersUseCase,
    observeEqualizerPresetNameUseCase: ObserveEqualizerPresetNameUseCase,
    private val cyclePlayModeUseCase: CyclePlayModeUseCase
) : ViewModel() {

    val uiState: StateFlow<MoreUiState> =
        combine(
            observePlayModeUseCase(),
            observeShowHiddenFoldersUseCase(),
            observeEqualizerPresetNameUseCase(),
            SleepTimerManager.state
        ) { playMode, showHiddenFolders, equalizerPresetName, sleepState ->
            MoreUiState(
                playModeLabelRes = PlayModeUiMapper.labelRes(playMode),
                playModeIconRes = PlayModeUiMapper.iconRes(playMode),
                sleepSummary = formatRemainingTime(sleepState),
                isHiddenFoldersVisible = showHiddenFolders,
                equalizerSummary = equalizerPresetName
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MoreUiState(
                playModeLabelRes = PlayModeUiMapper.labelRes(PlaybackMode.ORDER),
                playModeIconRes = PlayModeUiMapper.iconRes(PlaybackMode.ORDER),
                sleepSummary = "",
                isHiddenFoldersVisible = true,
                equalizerSummary = ""
            )
        )

    fun onPlayModeClicked() {
        viewModelScope.launch {
            cyclePlayModeUseCase()
        }
    }

    private fun formatRemainingTime(state: SleepTimerState): String {
        return when {
            !state.isActive -> ""

            state.isPendingTrackEnd -> {
                if (state.action == SleepTimerState.ACTION_EXIT_PLAYER) {
                    appContext.getString(R.string.sleep_end_exit)
                } else {
                    appContext.getString(R.string.sleep_end_stop)
                }
            }

            state.remainingMs > 0L -> {
                val totalSeconds = (state.remainingMs / 1000L).coerceAtLeast(0L)
                val hours = totalSeconds / 3600L
                val minutes = (totalSeconds % 3600L) / 60L
                val seconds = totalSeconds % 60L

                if (hours > 0L) {
                    "%d:%02d:%02d".format(hours, minutes, seconds)
                } else {
                    "%02d:%02d".format(minutes, seconds)
                }
            }

            state.stopAfterCurrentTrack -> {
                appContext.getString(R.string.sleep_end_stop)
            }

            else -> appContext.getString(R.string.sleep_end_stop)
        }
    }
}
