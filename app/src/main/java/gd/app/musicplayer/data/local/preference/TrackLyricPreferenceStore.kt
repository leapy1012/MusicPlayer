package gd.app.musicplayer.data.local.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class TrackLyricPreferenceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    suspend fun getTrackLyricPath(trackId: Long): String? {
        return dataStore.data.first()[lyricPathKey(trackId)]
    }

    suspend fun setTrackLyricPath(trackId: Long, path: String?) {
        dataStore.edit { preferences ->
            val key = lyricPathKey(trackId)
            if (path.isNullOrBlank()) {
                preferences.remove(key)
            } else {
                preferences[key] = path
            }
        }
    }

    suspend fun getTrackLyricOffset(trackId: Long): Int {
        return dataStore.data.first()[lyricOffsetKey(trackId)] ?: DEFAULT_LYRIC_OFFSET_MS
    }

    suspend fun setTrackLyricOffset(trackId: Long, offsetMs: Int) {
        dataStore.edit { preferences ->
            preferences[lyricOffsetKey(trackId)] = offsetMs
        }
    }

    suspend fun clearTrackLyricData(trackId: Long) {
        dataStore.edit { preferences ->
            preferences.remove(lyricPathKey(trackId))
            preferences.remove(lyricOffsetKey(trackId))
        }
    }

    private fun lyricPathKey(trackId: Long) =
        stringPreferencesKey("track_lyric_path_$trackId")

    private fun lyricOffsetKey(trackId: Long) =
        intPreferencesKey("track_lyric_offset_$trackId")

    private companion object {
        const val DEFAULT_LYRIC_OFFSET_MS = 0
    }
}
