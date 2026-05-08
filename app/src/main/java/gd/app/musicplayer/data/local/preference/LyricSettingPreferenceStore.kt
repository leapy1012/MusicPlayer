package gd.app.musicplayer.data.local.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import gd.app.musicplayer.core.datastore.SettingsKeys
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class LyricSettingPreferenceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    val lyricPreferences: Flow<LyricsSettingPreference> =
        dataStore.data
            .map { preferences ->
                preferences.toLyricsSettingPreference()
            }
            .distinctUntilChanged()

    suspend fun getLyricsPreference(): LyricsSettingPreference {
        return dataStore.data.first().toLyricsSettingPreference()
    }

    suspend fun setBluetoothLyricEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.KEY_BLUETOOTH_LYRIC_ENABLED] = enabled
        }
    }

    suspend fun setLyricColor(color: Int) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.KEY_LYRIC_COLOR] = color
        }
    }

    suspend fun setLyricTextSize(size: Float) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.KEY_LYRIC_TEXT_SIZE] = size
        }
    }

    suspend fun setLyricAlign(align: Int) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.KEY_LYRIC_ALIGN] = align
        }
    }

    suspend fun setLyricStyle(style: Int) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.KEY_LYRIC_STYLE] = style
        }
    }

    suspend fun setLyricAutoScrollEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.KEY_LYRIC_AUTO_SCROLL] = enabled
        }
    }

    suspend fun updateLyricsPreference(
        bluetoothLyricEnabled: Boolean? = null,
        lyricColor: Int? = null,
        lyricTextSize: Float? = null,
        lyricAlign: Int? = null,
        lyricStyle: Int? = null,
        lyricAutoScrollEnabled: Boolean? = null
    ) {
        dataStore.edit { preferences ->
            bluetoothLyricEnabled?.let {
                preferences[SettingsKeys.KEY_BLUETOOTH_LYRIC_ENABLED] = it
            }

            lyricColor?.let {
                preferences[SettingsKeys.KEY_LYRIC_COLOR] = it
            }

            lyricTextSize?.let {
                preferences[SettingsKeys.KEY_LYRIC_TEXT_SIZE] = it
            }

            lyricAlign?.let {
                preferences[SettingsKeys.KEY_LYRIC_ALIGN] = it
            }

            lyricStyle?.let {
                preferences[SettingsKeys.KEY_LYRIC_STYLE] = it
            }

            lyricAutoScrollEnabled?.let {
                preferences[SettingsKeys.KEY_LYRIC_AUTO_SCROLL] = it
            }
        }
    }

    private fun Preferences.toLyricsSettingPreference(): LyricsSettingPreference {
        return LyricsSettingPreference(
            bluetoothLyricEnabled =
                this[SettingsKeys.KEY_BLUETOOTH_LYRIC_ENABLED]
                    ?: DEFAULT_BLUETOOTH_LYRIC_ENABLED,
            lyricColor =
                this[SettingsKeys.KEY_LYRIC_COLOR]
                    ?: DEFAULT_LYRIC_COLOR,
            lyricTextSize =
                this[SettingsKeys.KEY_LYRIC_TEXT_SIZE]
                    ?: DEFAULT_LYRIC_TEXT_SIZE,
            lyricAlign =
                this[SettingsKeys.KEY_LYRIC_ALIGN]
                    ?: DEFAULT_LYRIC_ALIGN,
            lyricStyle =
                this[SettingsKeys.KEY_LYRIC_STYLE]
                    ?: DEFAULT_LYRIC_STYLE,
            lyricAutoScrollEnabled =
                this[SettingsKeys.KEY_LYRIC_AUTO_SCROLL]
                    ?: DEFAULT_LYRIC_AUTO_SCROLL_ENABLED
        )
    }

    private companion object {
        const val DEFAULT_BLUETOOTH_LYRIC_ENABLED = true
        const val DEFAULT_LYRIC_COLOR = -1
        const val DEFAULT_LYRIC_TEXT_SIZE = 18f
        const val DEFAULT_LYRIC_ALIGN = 1
        const val DEFAULT_LYRIC_STYLE = 0
        const val DEFAULT_LYRIC_AUTO_SCROLL_ENABLED = true
    }
}