package gd.app.musicplayer.data.local.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

    suspend fun setLastCustomMinutes(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_LAST_CUSTOM_MINUTES] = minutes.coerceAtLeast(1)
        }
    }

    private companion object {
        const val DEFAULT_LAST_CUSTOM_MINUTES = 15

        val KEY_LAST_CUSTOM_MINUTES =
            intPreferencesKey("sleep_time")
    }
}