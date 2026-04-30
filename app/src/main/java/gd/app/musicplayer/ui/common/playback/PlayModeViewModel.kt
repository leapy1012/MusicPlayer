package gd.app.musicplayer.ui.common.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.domain.usecase.playmode.CyclePlayModeUseCase
import gd.app.musicplayer.domain.usecase.playmode.GetPlayModeUseCase
import gd.app.musicplayer.domain.usecase.playmode.ObservePlayModeUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PlayModeUiState(
    val mode: Int,
    val iconRes: Int,
)

@HiltViewModel
class PlayModeViewModel @Inject constructor(
    observePlayModeUseCase: ObservePlayModeUseCase,
    getPlayModeUseCase: GetPlayModeUseCase,
    private val cyclePlayModeUseCase: CyclePlayModeUseCase,
) : ViewModel() {

    val uiState: StateFlow<PlayModeUiState> = observePlayModeUseCase()
        .map { mode ->
            PlayModeUiState(
                mode = mode,
                iconRes = PlayModeUiMapper.iconRes(mode),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = run {
                val mode = getPlayModeUseCase()
                PlayModeUiState(
                    mode = mode,
                    iconRes = PlayModeUiMapper.iconRes(mode),
                )
            },
        )

    fun cyclePlayMode() {
        cyclePlayModeUseCase()
    }
}
