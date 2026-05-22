package gd.app.musicplayer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class DrivePreferenceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    suspend fun isDriveWarningEnabled(): Boolean {
        return dataStore.data.first()[KEY_DRIVE_WARNING] ?: DEFAULT_DRIVE_WARNING_ENABLED
    }

    suspend fun setDriveWarningEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_DRIVE_WARNING] = enabled
        }
    }

    private companion object {
        const val DEFAULT_DRIVE_WARNING_ENABLED = true

        val KEY_DRIVE_WARNING = booleanPreferencesKey("preference_drive_warning")
    }
}
