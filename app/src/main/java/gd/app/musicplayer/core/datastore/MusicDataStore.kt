package gd.app.musicplayer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class MusicDataStore @Inject constructor(
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
    }
}
