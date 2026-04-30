package gd.app.musicplayer.domain.usecase.scan

import android.content.Context
import gd.app.musicplayer.data.db.MediaStoreMusicImporter
import gd.app.musicplayer.data.db.entity.MusicEntity
import javax.inject.Inject

class QueryMediaStoreTracksUseCase @Inject constructor() {
    private val importer = MediaStoreMusicImporter()

    operator fun invoke(context: Context): List<MusicEntity> = importer.queryMusic(context)
}
