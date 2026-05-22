package gd.app.musicplayer.domain.usecase.scan

import android.content.Context
import gd.app.musicplayer.core.mediastore.MediaStoreMusicImporter
import gd.app.musicplayer.core.database.entity.MusicEntity
import javax.inject.Inject

class QueryMediaStoreTracksUseCase @Inject constructor() {
    private val importer = MediaStoreMusicImporter()

    operator fun invoke(
        context: Context,
        modifiedSinceMs: Long? = null
    ): List<MusicEntity> = importer.queryMusic(
        context = context,
        modifiedSinceMs = modifiedSinceMs
    )

    fun queryIds(context: Context): Set<Long> = importer.queryMusicIds(context)
}
