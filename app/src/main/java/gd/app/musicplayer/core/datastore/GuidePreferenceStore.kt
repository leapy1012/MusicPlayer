package gd.app.musicplayer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class GuidePreferenceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    suspend fun shouldShowHomePlaylistDragGuide(): Boolean {
        return !(dataStore.data.first()[SettingsKeys.HOME_PLAYLIST_DRAG_GUIDE_SHOWN] ?: false)
    }

    suspend fun markHomePlaylistDragGuideShown() {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.HOME_PLAYLIST_DRAG_GUIDE_SHOWN] = true
        }
    }

    suspend fun shouldShowPlaylistDragGuide(): Boolean {
        return !(dataStore.data.first()[SettingsKeys.PLAYLIST_DRAG_GUIDE_SHOWN] ?: false)
    }

    suspend fun markPlaylistDragGuideShown() {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.PLAYLIST_DRAG_GUIDE_SHOWN] = true
        }
    }
}
