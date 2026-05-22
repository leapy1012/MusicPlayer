package gd.app.musicplayer.domain.usecase.scan

import gd.app.musicplayer.core.database.entity.MusicEntity
import gd.app.musicplayer.domain.repository.MediaLibraryScanner
import javax.inject.Inject

class QueryMediaStoreTracksUseCase @Inject constructor(
    private val mediaLibraryScanner: MediaLibraryScanner
) {
    operator fun invoke(modifiedSinceMs: Long? = null): List<MusicEntity> {
        return mediaLibraryScanner.queryMusic(modifiedSinceMs = modifiedSinceMs)
    }

    fun queryIds(): Set<Long> = mediaLibraryScanner.queryMusicIds()
}
