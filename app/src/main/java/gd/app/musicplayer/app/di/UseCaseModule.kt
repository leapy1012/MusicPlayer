package gd.app.musicplayer.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import gd.app.musicplayer.data.repo.PlaylistRepo
import gd.app.musicplayer.data.repo.TrackMutationRepo
import gd.app.musicplayer.data.repo.HiddenRepo
import gd.app.musicplayer.data.repo.MainRepo
import gd.app.musicplayer.domain.usecase.playback.EnqueueTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.ClearQueueUseCase
import gd.app.musicplayer.domain.usecase.playback.FormatPlaybackTimeUseCase
import gd.app.musicplayer.domain.usecase.playback.ObservePlaybackStateUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayNextTrackUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayNextTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayPreviousTrackUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.ReplaceQueueUseCase
import gd.app.musicplayer.domain.usecase.playback.RestartCurrentTrackUseCase
import gd.app.musicplayer.domain.usecase.playback.SeekToPositionUseCase
import gd.app.musicplayer.domain.usecase.playback.ShuffleTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.TogglePlayPauseUseCase
import gd.app.musicplayer.domain.usecase.library.GetAllTracksByCurrentSortUseCase
import gd.app.musicplayer.domain.usecase.library.ClearMusicSetUseCase
import gd.app.musicplayer.domain.usecase.playmode.CyclePlayModeUseCase
import gd.app.musicplayer.domain.usecase.playmode.GetPlayModeUseCase
import gd.app.musicplayer.domain.usecase.playmode.ObservePlayModeUseCase
import gd.app.musicplayer.domain.usecase.playlist.AddTracksToPlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.CreatePlaylistUseCase
import gd.app.musicplayer.domain.usecase.playlist.DeleteEmptyPlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.DeletePlaylistUseCase
import gd.app.musicplayer.domain.usecase.playlist.GetAllPlaylistNamesUseCase
import gd.app.musicplayer.domain.usecase.playlist.GetPlaylistSongMatchCountsUseCase
import gd.app.musicplayer.domain.usecase.playlist.ObservePlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.ObserveSelectablePlaylistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.PlaylistNameExistsUseCase
import gd.app.musicplayer.domain.usecase.playlist.RenamePlaylistUseCase
import gd.app.musicplayer.domain.usecase.playlist.ToggleFavoriteTrackUseCase
import gd.app.musicplayer.domain.usecase.playlist.UpdatePlaylistOrderUseCase
import gd.app.musicplayer.domain.usecase.track.DeleteTracksUseCase
import gd.app.musicplayer.domain.usecase.track.HideTracksUseCase
import gd.app.musicplayer.domain.usecase.track.RemoveTracksFromLibraryUseCase
import gd.app.musicplayer.data.repo.UserPreferencesRepo
import gd.app.musicplayer.domain.usecase.hidden.HideSelectionUseCase
import gd.app.musicplayer.domain.usecase.hidden.ObserveHiddenFoldersUseCase
import gd.app.musicplayer.domain.usecase.hidden.ObserveHiddenSongsUseCase
import gd.app.musicplayer.domain.usecase.hidden.ObserveVisibleFoldersUseCase
import gd.app.musicplayer.domain.usecase.hidden.ObserveVisibleSongsUseCase
import gd.app.musicplayer.domain.usecase.hidden.RemoveHiddenFolderUseCase
import gd.app.musicplayer.domain.usecase.hidden.UnhideSongsUseCase
import gd.app.musicplayer.domain.usecase.preferences.GetEqualizerPresetNameUseCase
import gd.app.musicplayer.domain.usecase.preferences.GetHiddenFoldersVisibleUseCase
import gd.app.musicplayer.domain.usecase.preferences.ObservePlaylistSortUseCase
import gd.app.musicplayer.domain.usecase.preferences.ObservePreferenceChangesUseCase
import gd.app.musicplayer.domain.usecase.preferences.ResetPlaylistSortUseCase
import gd.app.musicplayer.domain.usecase.setting.SettingPreferencesUseCase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

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
    fun provideHideTracksUseCase(hiddenRepo: HiddenRepo): HideTracksUseCase =
        HideTracksUseCase(hiddenRepo)

    @Provides
    @Singleton
    fun provideRemoveTracksFromLibraryUseCase(trackMutationRepo: TrackMutationRepo): RemoveTracksFromLibraryUseCase =
        RemoveTracksFromLibraryUseCase(trackMutationRepo)

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
    fun provideClearQueueUseCase(): ClearQueueUseCase = ClearQueueUseCase()

    @Provides
    @Singleton
    fun provideReplaceQueueUseCase(): ReplaceQueueUseCase = ReplaceQueueUseCase()

    @Provides
    @Singleton
    fun provideObservePlaybackStateUseCase(): ObservePlaybackStateUseCase = ObservePlaybackStateUseCase()

    @Provides
    @Singleton
    fun provideFormatPlaybackTimeUseCase(): FormatPlaybackTimeUseCase = FormatPlaybackTimeUseCase()

    @Provides
    @Singleton
    fun provideRestartCurrentTrackUseCase(): RestartCurrentTrackUseCase = RestartCurrentTrackUseCase()

    @Provides
    @Singleton
    fun provideObservePlayModeUseCase(preferencesRepo: UserPreferencesRepo): ObservePlayModeUseCase =
        ObservePlayModeUseCase(preferencesRepo)

    @Provides
    @Singleton
    fun provideGetPlayModeUseCase(preferencesRepo: UserPreferencesRepo): GetPlayModeUseCase =
        GetPlayModeUseCase(preferencesRepo)

    @Provides
    @Singleton
    fun provideCyclePlayModeUseCase(preferencesRepo: UserPreferencesRepo): CyclePlayModeUseCase =
        CyclePlayModeUseCase(preferencesRepo)

    @Provides
    @Singleton
    fun provideGetAllTracksByCurrentSortUseCase(mainRepo: MainRepo): GetAllTracksByCurrentSortUseCase =
        GetAllTracksByCurrentSortUseCase(mainRepo)

    @Provides
    @Singleton
    fun provideClearMusicSetUseCase(trackMutationRepo: TrackMutationRepo): ClearMusicSetUseCase =
        ClearMusicSetUseCase(trackMutationRepo)

    @Provides
    @Singleton
    fun provideObserveHiddenFoldersUseCase(hiddenRepo: HiddenRepo): ObserveHiddenFoldersUseCase =
        ObserveHiddenFoldersUseCase(hiddenRepo)

    @Provides
    @Singleton
    fun provideObserveHiddenSongsUseCase(hiddenRepo: HiddenRepo): ObserveHiddenSongsUseCase =
        ObserveHiddenSongsUseCase(hiddenRepo)

    @Provides
    @Singleton
    fun provideObserveVisibleFoldersUseCase(hiddenRepo: HiddenRepo): ObserveVisibleFoldersUseCase =
        ObserveVisibleFoldersUseCase(hiddenRepo)

    @Provides
    @Singleton
    fun provideObserveVisibleSongsUseCase(hiddenRepo: HiddenRepo): ObserveVisibleSongsUseCase =
        ObserveVisibleSongsUseCase(hiddenRepo)

    @Provides
    @Singleton
    fun provideRemoveHiddenFolderUseCase(hiddenRepo: HiddenRepo): RemoveHiddenFolderUseCase =
        RemoveHiddenFolderUseCase(hiddenRepo)

    @Provides
    @Singleton
    fun provideUnhideSongsUseCase(hiddenRepo: HiddenRepo): UnhideSongsUseCase =
        UnhideSongsUseCase(hiddenRepo)

    @Provides
    @Singleton
    fun provideHideSelectionUseCase(hiddenRepo: HiddenRepo): HideSelectionUseCase =
        HideSelectionUseCase(hiddenRepo)

    @Provides
    @Singleton
    fun provideObservePreferenceChangesUseCase(preferencesRepo: UserPreferencesRepo): ObservePreferenceChangesUseCase =
        ObservePreferenceChangesUseCase(preferencesRepo)

    @Provides
    @Singleton
    fun provideGetHiddenFoldersVisibleUseCase(preferencesRepo: UserPreferencesRepo): GetHiddenFoldersVisibleUseCase =
        GetHiddenFoldersVisibleUseCase(preferencesRepo)

    @Provides
    @Singleton
    fun provideGetEqualizerPresetNameUseCase(preferencesRepo: UserPreferencesRepo): GetEqualizerPresetNameUseCase =
        GetEqualizerPresetNameUseCase(preferencesRepo)

    @Provides
    @Singleton
    fun provideSettingPreferencesUseCase(@ApplicationContext context: Context): SettingPreferencesUseCase =
        SettingPreferencesUseCase(gd.app.musicplayer.util.PreferenceUtil.getInstance(context))

    @Provides
    @Singleton
    fun provideObservePlaylistsUseCase(playlistRepo: PlaylistRepo): ObservePlaylistsUseCase =
        ObservePlaylistsUseCase(playlistRepo)

    @Provides
    @Singleton
    fun provideObserveSelectablePlaylistsUseCase(playlistRepo: PlaylistRepo): ObserveSelectablePlaylistsUseCase =
        ObserveSelectablePlaylistsUseCase(playlistRepo)

    @Provides
    @Singleton
    fun provideGetPlaylistSongMatchCountsUseCase(playlistRepo: PlaylistRepo): GetPlaylistSongMatchCountsUseCase =
        GetPlaylistSongMatchCountsUseCase(playlistRepo)

    @Provides
    @Singleton
    fun provideUpdatePlaylistOrderUseCase(playlistRepo: PlaylistRepo): UpdatePlaylistOrderUseCase =
        UpdatePlaylistOrderUseCase(playlistRepo)

    @Provides
    @Singleton
    fun providePlaylistNameExistsUseCase(playlistRepo: PlaylistRepo): PlaylistNameExistsUseCase =
        PlaylistNameExistsUseCase(playlistRepo)

    @Provides
    @Singleton
    fun provideGetAllPlaylistNamesUseCase(playlistRepo: PlaylistRepo): GetAllPlaylistNamesUseCase =
        GetAllPlaylistNamesUseCase(playlistRepo)

    @Provides
    @Singleton
    fun provideObservePlaylistSortUseCase(@ApplicationContext context: Context): ObservePlaylistSortUseCase =
        ObservePlaylistSortUseCase(gd.app.musicplayer.util.PreferenceUtil.getInstance(context))

    @Provides
    @Singleton
    fun provideResetPlaylistSortUseCase(@ApplicationContext context: Context): ResetPlaylistSortUseCase =
        ResetPlaylistSortUseCase(gd.app.musicplayer.util.PreferenceUtil.getInstance(context))
}
