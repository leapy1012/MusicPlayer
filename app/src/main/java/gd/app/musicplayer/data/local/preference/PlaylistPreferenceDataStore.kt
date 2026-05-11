package gd.app.musicplayer.data.local.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import gd.app.musicplayer.domain.model.SmartPlaylistConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistPreferenceDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    fun observeSmartPlaylistConfig(): Flow<SmartPlaylistConfig> {
        return dataStore.data.map { preferences ->

            val timeLimit = preferences[KEY_PLAYLIST_TRACK_LIMIT_TIME] ?: MONTH_MS_6
            val trackLimit = preferences[KEY_PLAYLIST_TRACK_LIMIT] ?: DEFAULT_TRACK_LIMIT

            SmartPlaylistConfig(
                windowDurationMs = timeLimit,
                windowStartMs = if (timeLimit > 0) System.currentTimeMillis() - timeLimit else 0,
                trackLimit = if (timeLimit == 0L) trackLimit else -1
            )
        }
    }

    fun observePlaylistAddPosition(): Flow<Int> {
        return dataStore.data.map { preferences ->
            preferences[KEY_PLAYLIST_ADD_POSITION] ?: 0
        }
    }

    suspend fun getPlaylistAddPosition(): Int =
        observePlaylistAddPosition().first()

    suspend fun setPlaylistAddPosition(position: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_PLAYLIST_ADD_POSITION] = position
        }
    }


    suspend fun setSmartPlaylistSelection(
        selectionIndex: Int,
        customLimit: Int = DEFAULT_TRACK_LIMIT
    ) {
        val windowDurationMs = when (selectionIndex) {
            0 -> DAY_MS
            1 -> WEEK_MS
            2 -> MONTH_MS
            3 -> MONTH_MS_3
            4 -> MONTH_MS_6
            5 -> YEAR_MS
            6 -> FOREVER
            else -> 0L
        }

        dataStore.edit { preferences ->
            preferences[KEY_PLAYLIST_TRACK_LIMIT_TIME] = windowDurationMs

            if (windowDurationMs == 0L) {
                preferences[KEY_PLAYLIST_TRACK_LIMIT] = customLimit
            }
        }
    }

    companion object {
        private val KEY_PLAYLIST_TRACK_LIMIT = intPreferencesKey("preference_playlist_track_limit")
        private val KEY_PLAYLIST_TRACK_LIMIT_TIME = longPreferencesKey("playlist_track_limit_time")
        private val KEY_PLAYLIST_ADD_POSITION = intPreferencesKey("preference_playlist_add_position")

        private const val DEFAULT_TRACK_LIMIT = -1

        const val DAY_MS = 86_400_000L
        const val WEEK_MS = 604_800_000L
        const val MONTH_MS = 2_592_000_000L
        const val MONTH_MS_3 = 7_776_000_000L
        const val MONTH_MS_6 = 15_552_000_000L
        const val YEAR_MS = 31_104_000_000L
        const val FOREVER = -1L
    }
}