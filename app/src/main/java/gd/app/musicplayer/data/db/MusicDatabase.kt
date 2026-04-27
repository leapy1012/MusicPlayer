package gd.app.musicplayer.data.db

import android.content.ContentUris
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import gd.app.musicplayer.data.db.dao.DatabaseMaintenanceDao
import gd.app.musicplayer.data.db.dao.MusicDao
import gd.app.musicplayer.data.db.entity.AlbumPictureEntity
import gd.app.musicplayer.data.db.entity.EffectPresetEntity
import gd.app.musicplayer.data.db.entity.EffectTenPresetEntity
import gd.app.musicplayer.data.db.entity.HiddenFolderEntity
import gd.app.musicplayer.data.db.entity.MusicEntity
import gd.app.musicplayer.data.db.entity.MusicPlaylistEntity
import gd.app.musicplayer.data.db.entity.PlaylistEntity
import gd.app.musicplayer.util.PreferenceUtil

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

    companion object {
        const val DATABASE_NAME = "musicplayer.db"
        const val VERSION = 3006
        private const val EFFECT_PRESET_SCHEMA_VERSION = 2
        private const val KEY_EFFECT_PRESET_SCHEMA_VERSION = "effect_preset_schema_version"

        @Volatile
        private var instance: MusicDatabase? = null

        fun getInstance(
            context: Context,
            seedProvider: MusicDatabaseSeedProvider = ResourceMusicDatabaseSeedProvider(context)
        ): MusicDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext, seedProvider).also { instance = it }
            }
        }

        suspend fun runStartupSync(
            context: Context,
            seedProvider: MusicDatabaseSeedProvider = ResourceMusicDatabaseSeedProvider(context)
        ) {
            val appContext = context.applicationContext
            val preferenceUtil = PreferenceUtil.getInstance(appContext)
            val database = getInstance(appContext, seedProvider)

            if (preferenceUtil.getIntPreference(KEY_EFFECT_PRESET_SCHEMA_VERSION, 0) < EFFECT_PRESET_SCHEMA_VERSION) {
                MusicDatabaseSeeder(appContext, seedProvider)
                    .reseedEffectPresets(database.openHelper.writableDatabase)
                preferenceUtil.putIntPreference(KEY_EFFECT_PRESET_SCHEMA_VERSION, EFFECT_PRESET_SCHEMA_VERSION)
            }

            if (preferenceUtil.isFirstStart()) {
                database.musicDao().upsertAll(MediaStoreMusicImporter().queryMusic(appContext))
                preferenceUtil.setFirstStart(false)
            }
        }

        private fun buildDatabase(
            context: Context,
            seedProvider: MusicDatabaseSeedProvider
        ): MusicDatabase {
            return Room.databaseBuilder(context, MusicDatabase::class.java, DATABASE_NAME)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(*DatabaseMigrations.all)
                .addCallback(MusicDatabaseSeeder(context, seedProvider))
                .build()
        }
    }
}
