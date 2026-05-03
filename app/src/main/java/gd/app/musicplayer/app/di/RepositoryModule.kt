package gd.app.musicplayer.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import gd.app.musicplayer.data.db.dao.MusicDao
import gd.app.musicplayer.data.db.dao.PlaybackQueueDao
import gd.app.musicplayer.data.repo.ArtworkRepo
import gd.app.musicplayer.data.repo.EqualizerPresetRepo
import gd.app.musicplayer.data.repo.HiddenRepo
import gd.app.musicplayer.data.repo.LibraryRepo
import gd.app.musicplayer.data.repo.MainRepo
import gd.app.musicplayer.data.repo.MusicSetMetadataRepo
import gd.app.musicplayer.data.repo.PlaybackQueueRepo
import gd.app.musicplayer.data.repo.PlaylistRepo
import gd.app.musicplayer.data.repo.ScanRepo
import gd.app.musicplayer.data.repo.SearchRepo
import gd.app.musicplayer.data.repo.TrackMetadataRepo
import gd.app.musicplayer.data.repo.TrackMutationRepo
import gd.app.musicplayer.data.repo.UserPreferencesRepo
import gd.app.musicplayer.util.PreferenceUtil
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideUserPreferencesRepo(
        @ApplicationContext context: Context,
        preferenceUtil: PreferenceUtil
    ): UserPreferencesRepo = UserPreferencesRepo(context, preferenceUtil)

    @Provides
    @Singleton
    fun provideLibraryRepo(musicDao: MusicDao, preferenceUtil: PreferenceUtil): LibraryRepo =
        LibraryRepo(musicDao, preferenceUtil)

    @Provides
    @Singleton
    fun providePlaylistRepo(
        musicDao: MusicDao,
        artworkRepo: ArtworkRepo,
        preferenceUtil: PreferenceUtil
    ): PlaylistRepo =
        PlaylistRepo(musicDao, artworkRepo, preferenceUtil)

    @Provides
    @Singleton
    fun providePlaybackQueueRepo(
        dao: PlaybackQueueDao,
        preferenceUtil: PreferenceUtil
    ): PlaybackQueueRepo = PlaybackQueueRepo(dao, preferenceUtil)

    @Provides
    @Singleton
    fun provideMainRepo(
        musicDao: MusicDao,
        preferenceUtil: PreferenceUtil,
        artworkRepo: ArtworkRepo
    ): MainRepo = MainRepo(musicDao, preferenceUtil, artworkRepo)

    @Provides
    @Singleton
    fun provideSearchRepo(musicDao: MusicDao): SearchRepo = SearchRepo(musicDao)

    @Provides
    @Singleton
    fun provideScanRepo(musicDao: MusicDao): ScanRepo = ScanRepo(musicDao)

    @Provides
    @Singleton
    fun provideTrackMutationRepo(musicDao: MusicDao): TrackMutationRepo = TrackMutationRepo(musicDao)

    @Provides
    @Singleton
    fun provideHiddenRepo(musicDao: MusicDao): HiddenRepo = HiddenRepo(musicDao)

    @Provides
    @Singleton
    fun provideTrackMetadataRepo(
        @ApplicationContext context: Context,
        musicDao: MusicDao
    ): TrackMetadataRepo = TrackMetadataRepo(context, musicDao)

    @Provides
    @Singleton
    fun provideMusicSetMetadataRepo(
        musicDao: MusicDao,
        artworkRepo: ArtworkRepo
    ): MusicSetMetadataRepo = MusicSetMetadataRepo(musicDao, artworkRepo)

    @Provides
    @Singleton
    fun provideArtworkRepo(musicDao: MusicDao): ArtworkRepo = ArtworkRepo(musicDao)

    @Provides
    @Singleton
    fun provideEqualizerPresetRepo(musicDao: MusicDao): EqualizerPresetRepo =
        EqualizerPresetRepo(musicDao)
}
