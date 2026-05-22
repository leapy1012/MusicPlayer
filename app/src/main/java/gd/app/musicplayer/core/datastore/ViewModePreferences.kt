package gd.app.musicplayer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import gd.app.musicplayer.domain.model.MusicSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ViewModePreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    fun observeListViewMode(musicSet: MusicSet): Flow<Int> {
        if (musicSet is MusicSet.Folders) {
            return flowOf(0)
        }

        val key = intPreferencesKey("list_view_mode_${musicSet.id}")

        return dataStore.data.map { preferences ->
            preferences[key] ?: 0
        }
    }

    suspend fun setListViewMode(musicSet: MusicSet, mode: Int) {
        if (musicSet is MusicSet.Folders) return

        val key = intPreferencesKey("list_view_mode_${musicSet.id}")

        dataStore.edit { preferences ->
            preferences[key] = mode
        }
    }
}