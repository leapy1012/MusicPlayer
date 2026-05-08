package gd.app.musicplayer.ui.common.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.domain.usecase.playmode.CyclePlayModeUseCase
import gd.app.musicplayer.domain.usecase.playmode.ObservePlayModeUseCase
import gd.app.musicplayer.playback.PlaybackMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayModeUiState(
    val mode: Int,
    val iconRes: Int,
)

@HiltViewModel
class PlayModeViewModel @Inject constructor(
    observePlayModeUseCase: ObservePlayModeUseCase,
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
            initialValue = PlayModeUiState(
                mode = PlaybackMode.ORDER,
                iconRes = PlayModeUiMapper.iconRes(PlaybackMode.ORDER),
            ),
        )

    fun cyclePlayMode() {
        viewModelScope.launch {
            cyclePlayModeUseCase()
        }
    }
}
