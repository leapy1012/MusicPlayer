package gd.app.musicplayer.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gd.app.musicplayer.data.local.preference.TrackLyricPreferenceStore

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TrackLyricPreferenceStoreEntryPoint {
    fun trackLyricPreferenceStore(): TrackLyricPreferenceStore
}
