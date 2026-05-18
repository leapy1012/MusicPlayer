package gd.app.musicplayer.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.ui.editor.data.AudioTrimRepository
import gd.app.musicplayer.ui.editor.data.WaveformRepository
import gd.app.musicplayer.ui.editor.model.AudioClipRange
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AudioEditorViewModel @Inject constructor(
    private val waveformRepository: WaveformRepository,
    private val audioTrimRepository: AudioTrimRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioEditorUiState())
    val uiState: StateFlow<AudioEditorUiState> = _uiState

    private val _events = Channel<AudioEditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var sourceTrack: Music? = null

    fun loadTrack(track: Music) {
        sourceTrack = track

        val sourcePath = track.data

        if (sourcePath.isNullOrBlank()) {
            sendEvent(AudioEditorEvent.ShowError("Invalid audio file"))
            sendEvent(AudioEditorEvent.CloseScreen)
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            waveformRepository.loadWaveform(sourcePath)
                .onSuccess { waveform ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            waveformData = waveform,
                            clipRange = AudioClipRange.fullDuration(waveform.durationMs),
                            progressMs = 0,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message
                        )
                    }

                    sendEvent(
                        AudioEditorEvent.ShowError(
                            error.message ?: "Unable to load audio"
                        )
                    )
                    sendEvent(AudioEditorEvent.CloseScreen)
                }
        }
    }

    fun onClipStartChanged(timeMs: Int) {
        _uiState.update { state ->
            val durationMs = state.waveformData?.durationMs ?: 0
            val safeStart = timeMs.coerceIn(0, state.clipRange.endMs)

            state.copy(
                clipRange = state.clipRange.copy(startMs = safeStart).clampTo(durationMs),
                progressMs = state.progressMs.coerceAtLeast(safeStart)
            )
        }
    }

    fun onClipEndChanged(timeMs: Int) {
        _uiState.update { state ->
            val durationMs = state.waveformData?.durationMs ?: timeMs
            val safeEnd = timeMs.coerceIn(state.clipRange.startMs, durationMs)

            state.copy(
                clipRange = state.clipRange.copy(endMs = safeEnd).clampTo(durationMs),
                progressMs = state.progressMs.coerceAtMost(safeEnd)
            )
        }
    }

    fun onProgressChanged(timeMs: Int) {
        _uiState.update { state ->
            state.copy(
                progressMs = timeMs.coerceIn(
                    state.clipRange.startMs,
                    state.clipRange.endMs
                )
            )
        }
    }

    fun onPlaybackStateChanged(isPlaying: Boolean) {
        _uiState.update {
            it.copy(isPlaying = isPlaying)
        }
    }

    fun saveClip(fileName: String) {
        val state = uiState.value
        val waveform = state.waveformData

        if (waveform == null) {
            sendEvent(AudioEditorEvent.ShowError("Audio is not ready"))
            return
        }

        if (!state.clipRange.isValid()) {
            sendEvent(AudioEditorEvent.ShowError("Invalid clip range"))
            return
        }

        _uiState.update {
            it.copy(isSaving = true)
        }

        viewModelScope.launch {
            audioTrimRepository.exportClip(
                sourcePath = waveform.sourcePath,
                displayNameWithoutExtension = fileName,
                range = state.clipRange
            ).onSuccess {
                _uiState.update {
                    it.copy(isSaving = false)
                }

                sendEvent(AudioEditorEvent.ClipSaved)
                sendEvent(AudioEditorEvent.CloseScreen)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isSaving = false)
                }

                sendEvent(
                    AudioEditorEvent.ShowError(
                        error.message ?: "Unable to save clip"
                    )
                )
            }
        }
    }

    private fun sendEvent(event: AudioEditorEvent) {
        viewModelScope.launch {
            _events.send(event)
        }
    }
}