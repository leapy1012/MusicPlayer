package gd.app.musicplayer.domain.repository

import gd.app.musicplayer.core.database.entity.MusicEntity

interface MediaLibraryScanner {
    fun queryMusic(modifiedSinceMs: Long? = null): List<MusicEntity>

    fun queryMusicIds(): Set<Long>

    suspend fun scanAudioFiles(
        selectedPaths: List<String>,
        onFindingFile: suspend (String) -> Unit,
        onParseProgress: suspend (Int) -> Unit
    ): List<String>
}
