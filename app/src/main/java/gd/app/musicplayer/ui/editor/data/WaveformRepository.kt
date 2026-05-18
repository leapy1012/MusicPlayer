package gd.app.musicplayer.ui.editor.data

import gd.app.musicplayer.ui.editor.model.WaveformData

interface WaveformRepository {
    suspend fun loadWaveform(sourcePath: String): Result<WaveformData>
}