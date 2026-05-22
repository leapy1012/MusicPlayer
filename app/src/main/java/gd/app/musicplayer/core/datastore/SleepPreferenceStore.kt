package gd.app.musicplayer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class SleepPreferenceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    suspend fun getLastCustomMinutes(): Int {
        return dataStore.data.first()[KEY_LAST_CUSTOM_MINUTES]
            ?.coerceAtLeast(1)
            ?: DEFAULT_LAST_CUSTOM_MINUTES
    }

    suspend fun getEndAction(): Int {
        return dataStore.data.first()[KEY_END_ACTION]
            ?.coerceIn(MIN_END_ACTION, MAX_END_ACTION)
            ?: DEFAULT_END_ACTION
    }

    suspend fun getStopAfterCurrentTrackEnabled(): Boolean {
        return dataStore.data.first()[KEY_STOP_AFTER_CURRENT_TRACK] ?: false
    }

    suspend fun setLastCustomMinutes(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_LAST_CUSTOM_MINUTES] = minutes.coerceAtLeast(1)
        }
    }

    suspend fun setEndAction(action: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_END_ACTION] = action.coerceIn(MIN_END_ACTION, MAX_END_ACTION)
        }
    }

    suspend fun setStopAfterCurrentTrackEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_STOP_AFTER_CURRENT_TRACK] = enabled
        }
    }

    private companion object {
        const val DEFAULT_LAST_CUSTOM_MINUTES = 15
        const val DEFAULT_END_ACTION = 0
        const val MIN_END_ACTION = 0
        const val MAX_END_ACTION = 1

        val KEY_LAST_CUSTOM_MINUTES =
            intPreferencesKey("sleep_time")
        val KEY_END_ACTION =
            intPreferencesKey("sleep_end_time")
        val KEY_STOP_AFTER_CURRENT_TRACK =
            booleanPreferencesKey("sleep_end_current")
    }
}
