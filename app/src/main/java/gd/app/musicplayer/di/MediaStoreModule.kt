package gd.app.musicplayer.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gd.app.musicplayer.core.mediastore.AndroidMediaLibraryScanner
import gd.app.musicplayer.core.mediastore.AndroidTrackDeletionGateway
import gd.app.musicplayer.domain.repository.MediaLibraryScanner
import gd.app.musicplayer.domain.repository.TrackDeletionGateway
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaStoreModule {

    @Binds
    @Singleton
    abstract fun bindMediaLibraryScanner(
        impl: AndroidMediaLibraryScanner
    ): MediaLibraryScanner

    @Binds
    @Singleton
    abstract fun bindTrackDeletionGateway(
        impl: AndroidTrackDeletionGateway
    ): TrackDeletionGateway
}
