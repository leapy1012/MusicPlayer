package gd.app.musicplayer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import gd.app.musicplayer.core.database.dao.DatabaseMaintenanceDao
import gd.app.musicplayer.core.database.dao.LibraryDao
import gd.app.musicplayer.core.database.dao.MusicDao
import gd.app.musicplayer.core.database.dao.PlaybackQueueDao
import gd.app.musicplayer.core.database.dao.PlaylistDao
import gd.app.musicplayer.core.database.dao.SearchDao
import gd.app.musicplayer.core.database.entity.AlbumPictureEntity
import gd.app.musicplayer.core.database.entity.EffectPresetEntity
import gd.app.musicplayer.core.database.entity.EffectTenPresetEntity
import gd.app.musicplayer.core.database.entity.HiddenFolderEntity
import gd.app.musicplayer.core.database.entity.MusicEntity
import gd.app.musicplayer.core.database.entity.MusicPlaylistEntity
import gd.app.musicplayer.core.database.entity.PlaylistEntity

@Database(
    entities = [
        MusicEntity::class,
        HiddenFolderEntity::class,
        AlbumPictureEntity::class,
        PlaylistEntity::class,
        MusicPlaylistEntity::class,
        EffectPresetEntity::class,
        EffectTenPresetEntity::class
    ],
    version = MusicDatabase.VERSION,
    exportSchema = true
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun databaseMaintenanceDao(): DatabaseMaintenanceDao

    abstract fun musicDao(): MusicDao

    abstract fun libraryDao(): LibraryDao

    abstract fun playlistDao(): PlaylistDao
    abstract fun searchDao(): SearchDao

    abstract fun playbackQueueDao(): PlaybackQueueDao

    companion object {
        const val DATABASE_NAME = "musicplayer.db"
        const val VERSION = 3006
    }
}