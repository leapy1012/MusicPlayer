package gd.app.musicplayer.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gd.app.musicplayer.domain.repository.ThemeRepo

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ThemeEntryPoint {
    val themeRepo: ThemeRepo
}