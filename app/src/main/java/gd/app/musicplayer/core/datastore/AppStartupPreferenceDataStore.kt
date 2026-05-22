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
class AppStartupPreferenceDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    suspend fun isFirstStart(): Boolean {
        return dataStore.data.first()[KEY_FIRST_START] ?: true
    }

    suspend fun setFirstStart(firstStart: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_FIRST_START] = firstStart
        }
    }

    suspend fun getEffectPresetSchemaVersion(): Int {
        return dataStore.data.first()[KEY_EFFECT_PRESET_SCHEMA_VERSION] ?: 0
    }

    suspend fun setEffectPresetSchemaVersion(version: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_EFFECT_PRESET_SCHEMA_VERSION] = version
        }
    }

    private companion object {
        val KEY_FIRST_START = booleanPreferencesKey("first_start")
        val KEY_EFFECT_PRESET_SCHEMA_VERSION =
            intPreferencesKey("effect_preset_schema_version")
    }
}