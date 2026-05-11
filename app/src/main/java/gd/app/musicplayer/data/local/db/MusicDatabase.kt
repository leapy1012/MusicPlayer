package gd.app.musicplayer.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import gd.app.musicplayer.data.local.db.dao.DatabaseMaintenanceDao
import gd.app.musicplayer.data.local.db.dao.LibraryDao
import gd.app.musicplayer.data.local.db.dao.MusicDao
import gd.app.musicplayer.data.local.db.dao.PlaybackQueueDao
import gd.app.musicplayer.data.local.db.dao.PlaylistDao
import gd.app.musicplayer.data.local.db.dao.SearchDao
import gd.app.musicplayer.data.local.db.entity.AlbumPictureEntity
import gd.app.musicplayer.data.local.db.entity.EffectPresetEntity
import gd.app.musicplayer.data.local.db.entity.EffectTenPresetEntity
import gd.app.musicplayer.data.local.db.entity.HiddenFolderEntity
import gd.app.musicplayer.data.local.db.entity.MusicEntity
import gd.app.musicplayer.data.local.db.entity.MusicPlaylistEntity
import gd.app.musicplayer.data.local.db.entity.PlaylistEntity

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
    exportSchema = false
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