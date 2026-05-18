package gd.app.musicplayer.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gd.app.musicplayer.ui.editor.data.AndroidWaveformRepository
import gd.app.musicplayer.ui.editor.data.AudioTrimRepository
import gd.app.musicplayer.ui.editor.data.MediaStoreAudioTrimRepository
import gd.app.musicplayer.ui.editor.data.WaveformRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioEditorModule {

    @Binds
    @Singleton
    abstract fun bindWaveformRepository(
        repository: AndroidWaveformRepository
    ): WaveformRepository

    @Binds
    @Singleton
    abstract fun bindAudioTrimRepository(
        repository: MediaStoreAudioTrimRepository
    ): AudioTrimRepository
}