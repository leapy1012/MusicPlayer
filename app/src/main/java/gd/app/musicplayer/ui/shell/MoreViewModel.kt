package gd.app.musicplayer.ui.shell

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.domain.usecase.playmode.CyclePlayModeUseCase
import gd.app.musicplayer.domain.usecase.playmode.GetPlayModeUseCase
import gd.app.musicplayer.domain.usecase.playmode.ObservePlayModeUseCase
import gd.app.musicplayer.domain.usecase.preferences.GetEqualizerPresetNameUseCase
import gd.app.musicplayer.domain.usecase.preferences.GetHiddenFoldersVisibleUseCase
import gd.app.musicplayer.domain.usecase.preferences.ObservePreferenceChangesUseCase
import gd.app.musicplayer.R
import gd.app.musicplayer.playback.SleepTimerManager
import gd.app.musicplayer.playback.SleepTimerState
import gd.app.musicplayer.ui.common.playback.PlayModeUiMapper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MoreUiState(
    val playModeLabelRes: Int = R.string.play_mode_list,
    val playModeIconRes: Int = R.drawable.vector_mode_order,
    val sleepSummary: String = "",
    val isHiddenFoldersVisible: Boolean = false,
    val equalizerSummary: String = ""
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val observePreferenceChangesUseCase: ObservePreferenceChangesUseCase,
    private val observePlayModeUseCase: ObservePlayModeUseCase,
    private val getPlayModeUseCase: GetPlayModeUseCase,
    private val cyclePlayModeUseCase: CyclePlayModeUseCase,
    private val getHiddenFoldersVisibleUseCase: GetHiddenFoldersVisibleUseCase,
    private val getEqualizerPresetNameUseCase: GetEqualizerPresetNameUseCase
) : ViewModel() {

    val uiState: StateFlow<MoreUiState> = combine(
        combine(observePreferenceChangesUseCase(), observePlayModeUseCase()) { _, mode -> mode }.map { mode ->
            MoreUiState(
                playModeLabelRes = PlayModeUiMapper.labelRes(mode),
                playModeIconRes = PlayModeUiMapper.iconRes(mode),
                sleepSummary = "",
                isHiddenFoldersVisible = getHiddenFoldersVisibleUseCase(),
                equalizerSummary = getEqualizerPresetNameUseCase()
            )
        },
        SleepTimerManager.state
    ) { preferenceState, sleepState ->
        preferenceState.copy(sleepSummary = formatRemainingTime(sleepState))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MoreUiState(
            playModeLabelRes = playModeLabel(),
            playModeIconRes = PlayModeUiMapper.iconRes(getPlayModeUseCase()),
            isHiddenFoldersVisible = getHiddenFoldersVisibleUseCase(),
            equalizerSummary = getEqualizerPresetNameUseCase()
        )
    )

    fun onPlayModeClicked() {
        cyclePlayModeUseCase()
    }

    private fun playModeLabel(): Int =
        PlayModeUiMapper.labelRes(getPlayModeUseCase())

    private fun formatRemainingTime(state: SleepTimerState): String = when {
        !state.isActive -> ""
        state.stopAfterCurrentTrack -> appContext.getString(R.string.sleep_end_stop)
        state.action == SleepTimerState.ACTION_EXIT_PLAYER -> appContext.getString(R.string.sleep_end_exit)
        else -> {
            val totalSeconds = (state.remainingMs / 1000L).coerceAtLeast(0L)
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
            else "%02d:%02d".format(minutes, seconds)
        }
    }
}
