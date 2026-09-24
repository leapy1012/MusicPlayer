package gd.app.musicplayer.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class MusicDataStore @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val dataStore: DataStore<Preferences>
) {
    val data: Flow<Preferences> = dataStore.data

    fun <T> observe(key: Preferences.Key<T>, defaultValue: T): Flow<T> {
        return dataStore.data.map { preferences -> preferences[key] ?: defaultValue }.distinctUntilChanged()
    }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
        // Mirror booleans into SharedPreferences so PreferenceItemView can paint correctly
        // on inflate (original App uses SP; Dream's source of truth is DataStore).
        if (value is Boolean) {
            PreferenceSharedStore(PreferenceSharedStore.MUSIC_PREFERENCE)
                .putBoolean(appContext, key.name, value)
        }
    }

    /**
     * One-shot DataStore → SharedPreferences mirror for Settings open first paint.
     */
    suspend fun syncBooleansToSharedPreferences() {
        val preferences = dataStore.data.first()
        val store = PreferenceSharedStore(PreferenceSharedStore.MUSIC_PREFERENCE)
        val editor = store.prefs(appContext)?.edit() ?: return
        preferences.asMap().forEach { (key, value) ->
            if (value is Boolean) {
                editor.putBoolean(key.name, value)
            }
        }
        editor.apply()
    }
}
