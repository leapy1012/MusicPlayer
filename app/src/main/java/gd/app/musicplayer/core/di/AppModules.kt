package gd.app.musicplayer.core.di

import android.content.Context
import gd.app.musicplayer.app.AppDispatchers
import gd.app.musicplayer.data.db.MusicDatabase
import gd.app.musicplayer.data.db.dao.MusicDao
import gd.app.musicplayer.data.repo.ArtworkRepo
import gd.app.musicplayer.data.repo.HiddenRepo
import gd.app.musicplayer.data.repo.LibraryRepo
import gd.app.musicplayer.data.repo.MainRepo
import gd.app.musicplayer.data.repo.PlaylistRepo
import gd.app.musicplayer.data.repo.ScanRepo
import gd.app.musicplayer.data.repo.SearchRepo
import gd.app.musicplayer.data.repo.ThemeRepo
import gd.app.musicplayer.data.repo.TrackMetadataRepo
import gd.app.musicplayer.data.repo.TrackMutationRepo
import gd.app.musicplayer.data.repo.UserPreferencesRepo
import gd.app.musicplayer.core.theme.ThemeManager
import gd.app.musicplayer.core.theme.ThemeRegistry
import gd.app.musicplayer.domain.usecase.playback.EnqueueTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayNextTrackUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayNextTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayPreviousTrackUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.SeekToPositionUseCase
import gd.app.musicplayer.domain.usecase.playback.ShuffleTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.TogglePlayPauseUseCase
import gd.app.musicplayer.domain.usecase.playlist.AddTracksToPlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.CreatePlaylistUseCase
import gd.app.musicplayer.domain.usecase.playlist.DeleteEmptyPlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.DeletePlaylistUseCase
import gd.app.musicplayer.domain.usecase.playlist.RenamePlaylistUseCase
import gd.app.musicplayer.domain.usecase.playlist.ToggleFavoriteTrackUseCase
import gd.app.musicplayer.domain.usecase.track.DeleteTracksUseCase
import gd.app.musicplayer.ui.theme.ThemeEngine
import gd.app.musicplayer.util.PreferenceUtil
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModules {

    @Provides
    @Singleton
    fun provideMusicDatabase(@ApplicationContext context: Context): MusicDatabase =
        MusicDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideMusicDao(database: MusicDatabase): MusicDao =
        database.musicDao()

    @Provides
    @Singleton
    fun providePreferenceUtil(@ApplicationContext context: Context): PreferenceUtil =
        PreferenceUtil.getInstance(context)

    @Provides
    @Singleton
    fun provideThemeRegistry(): ThemeRegistry = ThemeRegistry()

    @Provides
    @Singleton
    fun provideThemeManager(
        @ApplicationContext context: Context,
        preferenceUtil: PreferenceUtil,
        themeRegistry: ThemeRegistry
    ): ThemeManager = ThemeManager(preferenceUtil, themeRegistry).also {
        themeRegistry.installProvider(it, true)
        themeRegistry.refreshTheme(context)
    }

    @Provides
    @Singleton
    fun provideThemeRepo(
        themeRegistry: ThemeRegistry,
        themeManager: ThemeManager,
        preferenceUtil: PreferenceUtil
    ): ThemeRepo = ThemeRepo(themeRegistry, themeManager, preferenceUtil)

    @Provides
    @Singleton
    fun provideThemeEngine(themeRegistry: ThemeRegistry): ThemeEngine = ThemeEngine(themeRegistry)

    @Provides
    @Singleton
    fun provideUserPreferencesRepo(
        @ApplicationContext context: Context,
        preferenceUtil: PreferenceUtil
    ): UserPreferencesRepo = UserPreferencesRepo(context, preferenceUtil)

    @Provides
    @Singleton
    fun provideLibraryRepo(
        musicDao: MusicDao,
        preferenceUtil: PreferenceUtil
    ): LibraryRepo = LibraryRepo(musicDao, preferenceUtil)

    @Provides
    @Singleton
    fun providePlaylistRepo(
        musicDao: MusicDao,
        artworkRepo: ArtworkRepo
    ): PlaylistRepo = PlaylistRepo(musicDao, artworkRepo)

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
    fun provideArtworkRepo(musicDao: MusicDao): ArtworkRepo = ArtworkRepo(musicDao)

    @Provides
    @Singleton
    fun provideAddTracksToPlaylistsUseCase(playlistRepo: PlaylistRepo): AddTracksToPlaylistsUseCase =
        AddTracksToPlaylistsUseCase(playlistRepo)

    @Provides
    @Singleton
    fun provideCreatePlaylistUseCase(playlistRepo: PlaylistRepo): CreatePlaylistUseCase =
        CreatePlaylistUseCase(playlistRepo)

    @Provides
    @Singleton
    fun provideRenamePlaylistUseCase(playlistRepo: PlaylistRepo): RenamePlaylistUseCase =
        RenamePlaylistUseCase(playlistRepo)

    @Provides
    @Singleton
    fun provideDeletePlaylistUseCase(playlistRepo: PlaylistRepo): DeletePlaylistUseCase =
        DeletePlaylistUseCase(playlistRepo)

    @Provides
    @Singleton
    fun provideDeleteEmptyPlaylistsUseCase(playlistRepo: PlaylistRepo): DeleteEmptyPlaylistsUseCase =
        DeleteEmptyPlaylistsUseCase(playlistRepo)

    @Provides
    @Singleton
    fun provideToggleFavoriteTrackUseCase(playlistRepo: PlaylistRepo): ToggleFavoriteTrackUseCase =
        ToggleFavoriteTrackUseCase(playlistRepo)

    @Provides
    @Singleton
    fun provideDeleteTracksUseCase(
        @ApplicationContext context: Context,
        trackMutationRepo: TrackMutationRepo
    ): DeleteTracksUseCase = DeleteTracksUseCase(context, trackMutationRepo)

    @Provides
    @Singleton
    fun providePlayTracksUseCase(): PlayTracksUseCase = PlayTracksUseCase()

    @Provides
    @Singleton
    fun provideEnqueueTracksUseCase(): EnqueueTracksUseCase = EnqueueTracksUseCase()

    @Provides
    @Singleton
    fun providePlayNextTracksUseCase(): PlayNextTracksUseCase = PlayNextTracksUseCase()

    @Provides
    @Singleton
    fun provideTogglePlayPauseUseCase(): TogglePlayPauseUseCase = TogglePlayPauseUseCase()

    @Provides
    @Singleton
    fun providePlayPreviousTrackUseCase(): PlayPreviousTrackUseCase = PlayPreviousTrackUseCase()

    @Provides
    @Singleton
    fun providePlayNextTrackUseCase(): PlayNextTrackUseCase = PlayNextTrackUseCase()

    @Provides
    @Singleton
    fun provideSeekToPositionUseCase(): SeekToPositionUseCase = SeekToPositionUseCase()

    @Provides
    @Singleton
    fun provideShuffleTracksUseCase(): ShuffleTracksUseCase = ShuffleTracksUseCase()

    @Provides
    @Singleton
    fun provideAppDispatchers(): AppDispatchers = AppDispatchers()
}
