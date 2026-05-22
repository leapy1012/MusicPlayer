package gd.app.musicplayer.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gd.app.musicplayer.core.database.DefaultDatabaseStartupGateway
import gd.app.musicplayer.domain.repository.DatabaseStartupGateway
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StartupModule {

    @Binds
    @Singleton
    abstract fun bindDatabaseStartupGateway(
        impl: DefaultDatabaseStartupGateway
    ): DatabaseStartupGateway
}
