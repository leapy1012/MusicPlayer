package gd.app.musicplayer.util

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import gd.app.musicplayer.data.local.preference.TrackLyricPreferenceStore
import gd.app.musicplayer.di.TrackLyricPreferenceStoreEntryPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class TrackLyricsStore @Inject constructor(
    private val trackLyricPreferenceStore: TrackLyricPreferenceStore
) {
    fun getTrackLyricPath(trackId: Long): String? =
        runBlocking { trackLyricPreferenceStore.getTrackLyricPath(trackId) }

    fun setTrackLyricPath(trackId: Long, path: String?) {
        runBlocking { trackLyricPreferenceStore.setTrackLyricPath(trackId, path) }
    }

    fun getTrackLyricOffset(trackId: Long): Int =
        runBlocking { trackLyricPreferenceStore.getTrackLyricOffset(trackId) }

    fun setTrackLyricOffset(trackId: Long, offsetMs: Int) {
        runBlocking { trackLyricPreferenceStore.setTrackLyricOffset(trackId, offsetMs) }
    }

    fun clearTrackLyricData(trackId: Long) {
        runBlocking { trackLyricPreferenceStore.clearTrackLyricData(trackId) }
    }

    companion object {
        fun from(context: Context): TrackLyricsStore =
            TrackLyricsStore(
                EntryPointAccessors
                    .fromApplication(
                        context.applicationContext,
                        TrackLyricPreferenceStoreEntryPoint::class.java
                    )
                    .trackLyricPreferenceStore()
            )
    }
}
