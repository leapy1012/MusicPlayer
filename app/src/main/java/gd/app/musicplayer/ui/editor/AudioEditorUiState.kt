package gd.app.musicplayer.ui.editor

import gd.app.musicplayer.ui.editor.model.AudioClipRange
import gd.app.musicplayer.ui.editor.model.WaveformData

data class AudioEditorUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isPlaying: Boolean = false,
    val waveformData: WaveformData? = null,
    val clipRange: AudioClipRange = AudioClipRange(0, 0),
    val progressMs: Int = 0,
    val errorMessage: String? = null
) {
    val isEditorEnabled: Boolean
        get() = !isLoading && !isSaving && waveformData != null

    val clipDurationMs: Int
        get() = clipRange.durationMs
}