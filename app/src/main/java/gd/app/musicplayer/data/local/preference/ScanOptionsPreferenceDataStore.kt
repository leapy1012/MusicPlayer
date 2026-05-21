package gd.app.musicplayer.data.local.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import gd.app.musicplayer.ui.scan.ScanOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanOptionsPreferenceDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    val scanOptions: Flow<ScanOptions> =
        dataStore.data
            .map { preferences ->
                preferences.toScanOptions()
            }

    suspend fun getScanOptions(): ScanOptions {
        return dataStore.data.first().toScanOptions()
    }

    suspend fun updateScanOptions(scanOptions: ScanOptions) {
        dataStore.edit { preferences ->
            preferences[PREF_EXCLUDE_MUSIC_BY_SECONDS] = scanOptions.excludeBySeconds
            preferences[PREF_EXCLUDE_MUSIC_BY_SIZE] = scanOptions.excludeBySize
            preferences[PREF_EXCLUDE_RINGTONE] = scanOptions.excludeRingtone
            preferences[PREF_EXCLUDE_MUSIC_SECONDS] = scanOptions.excludeSeconds
            preferences[PREF_EXCLUDE_MUSIC_SIZE] = scanOptions.excludeSizeKb
            preferences[PREF_SELECTED_SCAN_PATHS] = scanOptions.selectedScanPaths.toSet()
        }
    }

    private fun Preferences.toScanOptions(): ScanOptions {
        return ScanOptions(
            excludeBySeconds = this[PREF_EXCLUDE_MUSIC_BY_SECONDS] ?: true,
            excludeBySize = this[PREF_EXCLUDE_MUSIC_BY_SIZE] ?: true,
            excludeRingtone = this[PREF_EXCLUDE_RINGTONE] ?: false,
            excludeSeconds = this[PREF_EXCLUDE_MUSIC_SECONDS] ?: 60L,
            excludeSizeKb = this[PREF_EXCLUDE_MUSIC_SIZE] ?: 50L,
            selectedScanPaths = this[PREF_SELECTED_SCAN_PATHS]?.toList().orEmpty()
        )
    }

    companion object {
        private val PREF_EXCLUDE_RINGTONE =
            booleanPreferencesKey("pref_ignore_ringtone")

        private val PREF_EXCLUDE_MUSIC_SECONDS =
            longPreferencesKey("pref_exclude_music_duration")

        private val PREF_EXCLUDE_MUSIC_BY_SECONDS =
            booleanPreferencesKey("pref_ignore_60seconds_music")

        private val PREF_EXCLUDE_MUSIC_SIZE =
            longPreferencesKey("pref_exclude_music_size")

        private val PREF_EXCLUDE_MUSIC_BY_SIZE =
            booleanPreferencesKey("pref_exclude_music_by_size")

        private val PREF_SELECTED_SCAN_PATHS =
            stringSetPreferencesKey("pref_selected_scan_paths")
    }
}
