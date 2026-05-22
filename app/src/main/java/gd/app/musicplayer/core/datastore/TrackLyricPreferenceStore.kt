package gd.app.musicplayer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class TrackLyricData(
    val path: String?,
    val offsetMs: Int,
    val revision: Int
)

@Singleton
class TrackLyricPreferenceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    fun observeTrackLyricData(trackId: Long): Flow<TrackLyricData> {
        return dataStore.data
            .map { preferences -> preferences.trackLyricData(trackId) }
            .distinctUntilChanged()
    }

    suspend fun getTrackLyricData(trackId: Long): TrackLyricData {
        return dataStore.data.first().trackLyricData(trackId)
    }

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
            preferences.incrementLyricRevision(trackId)
        }
    }

    suspend fun getTrackLyricOffset(trackId: Long): Int {
        return dataStore.data.first()[lyricOffsetKey(trackId)] ?: DEFAULT_LYRIC_OFFSET_MS
    }

    suspend fun setTrackLyricOffset(trackId: Long, offsetMs: Int) {
        dataStore.edit { preferences ->
            preferences[lyricOffsetKey(trackId)] = offsetMs
            preferences.incrementLyricRevision(trackId)
        }
    }

    suspend fun clearTrackLyricData(trackId: Long) {
        dataStore.edit { preferences ->
            preferences.remove(lyricPathKey(trackId))
            preferences.remove(lyricOffsetKey(trackId))
            preferences.incrementLyricRevision(trackId)
        }
    }

    private fun Preferences.trackLyricData(trackId: Long): TrackLyricData =
        TrackLyricData(
            path = this[lyricPathKey(trackId)],
            offsetMs = this[lyricOffsetKey(trackId)] ?: DEFAULT_LYRIC_OFFSET_MS,
            revision = this[lyricRevisionKey(trackId)] ?: 0
        )

    private fun lyricPathKey(trackId: Long) =
        stringPreferencesKey("track_lyric_path_$trackId")

    private fun lyricOffsetKey(trackId: Long) =
        intPreferencesKey("track_lyric_offset_$trackId")

    private fun lyricRevisionKey(trackId: Long) =
        intPreferencesKey("track_lyric_revision_$trackId")

    private fun MutablePreferences.incrementLyricRevision(trackId: Long) {
        val key = lyricRevisionKey(trackId)
        this[key] = ((this[key] ?: 0) + 1).coerceAtLeast(0)
    }

    private companion object {
        const val DEFAULT_LYRIC_OFFSET_MS = 0
    }
}
