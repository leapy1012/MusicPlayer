package gd.app.musicplayer.core.database

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.domain.repository.DatabaseStartupGateway
import gd.app.musicplayer.domain.repository.MediaLibraryScanner
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultDatabaseStartupGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val seedProvider: MusicDatabaseSeedProvider,
    private val mediaLibraryScanner: MediaLibraryScanner
) : DatabaseStartupGateway {

    override suspend fun reseedEffectPresets() {
        MusicDatabaseSeeder(context, seedProvider)
            .reseedEffectPresets(database.openHelper.writableDatabase)
    }

    override suspend fun importAllMusic() {
        val music = mediaLibraryScanner.queryMusic(modifiedSinceMs = null)
        database.musicDao().upsertAll(music)
    }
}
