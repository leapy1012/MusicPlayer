package gd.app.musicplayer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class PlaybackUiPreferenceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    val slidingSwitchEnabled: Flow<Boolean> =
        dataStore.data
            .map { preferences ->
                preferences[KEY_SLIDING_SWITCH] ?: DEFAULT_SLIDING_SWITCH_ENABLED
            }
            .distinctUntilChanged()

    suspend fun setSlidingSwitchEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_SLIDING_SWITCH] = enabled
        }
    }

    private companion object {
        const val DEFAULT_SLIDING_SWITCH_ENABLED = true
        val KEY_SLIDING_SWITCH = booleanPreferencesKey("preference_sliding_switch")
    }
}
