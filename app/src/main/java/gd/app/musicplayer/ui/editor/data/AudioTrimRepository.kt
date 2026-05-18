package gd.app.musicplayer.ui.editor.data

import gd.app.musicplayer.ui.editor.model.AudioClipRange

interface AudioTrimRepository {
    suspend fun exportClip(
        sourcePath: String,
        displayNameWithoutExtension: String,
        range: AudioClipRange
    ): Result<Long>
}