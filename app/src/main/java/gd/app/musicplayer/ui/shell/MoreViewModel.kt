package gd.app.musicplayer.ui.shell

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.R
import gd.app.musicplayer.data.repo.UserPreferencesRepo
import gd.app.musicplayer.playback.SleepTimerManager
import gd.app.musicplayer.playback.SleepTimerState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MoreUiState(
    val playModeLabelRes: Int = R.string.play_mode_list,
    val sleepSummary: String = "",
    val isHiddenFoldersVisible: Boolean = false,
    val equalizerSummary: String = ""
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val preferencesRepo: UserPreferencesRepo
) : ViewModel() {

    val uiState: StateFlow<MoreUiState> = combine(
        preferencesRepo.observePreferenceChanges().map {
            MoreUiState(
                playModeLabelRes = playModeLabel(),
                sleepSummary = "",
                isHiddenFoldersVisible = preferencesRepo.shouldShowHiddenFolders(),
                equalizerSummary = preferencesRepo.getEqualizerPresetName()
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
            isHiddenFoldersVisible = preferencesRepo.shouldShowHiddenFolders(),
            equalizerSummary = preferencesRepo.getEqualizerPresetName()
        )
    )

    fun onPlayModeClicked() {
        preferencesRepo.cyclePlayMode()
    }

    private fun playModeLabel(): Int =
        when (preferencesRepo.getPlayMode()) {
            PLAY_MODE_LOOP_ALL -> R.string.play_mode_list_cycle
            PLAY_MODE_SHUFFLE_ALL -> R.string.play_mode_list_rand
            else -> R.string.play_mode_list
        }

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

    private companion object {
        const val PLAY_MODE_LOOP_ALL = 2
        const val PLAY_MODE_SHUFFLE_ALL = 3
    }
}
