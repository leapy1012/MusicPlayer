package gd.app.musicplayer.ui.editor.data

import gd.app.musicplayer.ui.editor.model.WaveformData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AndroidWaveformRepository @Inject constructor() : WaveformRepository {

    override suspend fun loadWaveform(sourcePath: String): Result<WaveformData> {
        return withContext(Dispatchers.IO) {
            runCatching {
                WaveformExtractor.load(sourcePath)
            }
        }
    }
}