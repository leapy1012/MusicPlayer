package gd.app.musicplayer.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import gd.app.musicplayer.core.database.DatabaseMigrations
import gd.app.musicplayer.core.database.MusicDatabase
import gd.app.musicplayer.core.database.MusicDatabaseSeedProvider
import gd.app.musicplayer.core.database.MusicDatabaseSeeder
import gd.app.musicplayer.core.database.ResourceMusicDatabaseSeedProvider
import gd.app.musicplayer.core.database.dao.DatabaseMaintenanceDao
import gd.app.musicplayer.core.database.dao.LibraryDao
import gd.app.musicplayer.core.database.dao.MusicDao
import gd.app.musicplayer.core.database.dao.PlaybackQueueDao
import gd.app.musicplayer.core.database.dao.PlaylistDao
import gd.app.musicplayer.core.database.dao.SearchDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMusicDatabaseSeedProvider(
        @ApplicationContext context: Context
    ): MusicDatabaseSeedProvider {
        return ResourceMusicDatabaseSeedProvider(context)
    }

    @Provides
    @Singleton
    fun provideMusicDatabase(
        @ApplicationContext context: Context,
        seedProvider: MusicDatabaseSeedProvider
    ): MusicDatabase {
        return Room.databaseBuilder(
            context,
            MusicDatabase::class.java,
            MusicDatabase.DATABASE_NAME
        )
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(*DatabaseMigrations.all)
            .addCallback(MusicDatabaseSeeder(context, seedProvider))
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)

                        /*
                         * Usually do NOT disable foreign keys.
                         * Room enables foreign key constraints by default.
                         *
                         * If your old app required this, keep it temporarily,
                         * but the better long-term fix is proper migrations.
                         */
                        // db.execSQL("PRAGMA foreign_keys=OFF")
                    }
                }
            )
            .build()
    }

    @Provides
    fun provideDatabaseMaintenanceDao(
        database: MusicDatabase
    ): DatabaseMaintenanceDao {
        return database.databaseMaintenanceDao()
    }

    @Provides
    fun provideMusicDao(
        database: MusicDatabase
    ): MusicDao {
        return database.musicDao()
    }

    @Provides
    fun provideLibraryDao(
        database: MusicDatabase
    ): LibraryDao {
        return database.libraryDao()
    }

    @Provides
    fun providePlaylistDao(
        database: MusicDatabase
    ): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    fun providePlaybackQueueDao(
        database: MusicDatabase
    ): PlaybackQueueDao {
        return database.playbackQueueDao()
    }

    @Provides
    @Singleton
    fun provideSearchDao(
        database: MusicDatabase
    ): SearchDao {
        return database.searchDao()
    }
}