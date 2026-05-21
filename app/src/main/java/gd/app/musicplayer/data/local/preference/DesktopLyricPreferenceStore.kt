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
class DesktopLyricPreferenceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    val desktopLyricPreference: Flow<DesktopLyricPreference> =
        dataStore.data
            .map { preferences ->
                preferences.toDesktopLyricPreference()
            }
            .distinctUntilChanged()

    suspend fun getPreferenceSnapshot(): DesktopLyricPreference {
        return dataStore.data.first().toDesktopLyricPreference()
    }

    suspend fun isVisible(): Boolean {
        return getPreferenceSnapshot().visible
    }

    suspend fun setVisible(visible: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.KEY_DESKTOP_LYRIC_VISIBLE] = visible
        }
    }

    suspend fun isLocked(): Boolean {
        return getPreferenceSnapshot().locked
    }

    suspend fun setLocked(locked: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.KEY_DESKTOP_LYRIC_LOCKED] = locked
        }
    }

    suspend fun isPendingEnableAfterPermission(): Boolean {
        return getPreferenceSnapshot().pendingEnableAfterPermission
    }

    suspend fun setPendingEnableAfterPermission(pending: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.KEY_DESKTOP_LYRIC_PENDING_ENABLE_AFTER_PERMISSION] = pending
        }
    }

    suspend fun updatePreference(
        visible: Boolean? = null,
        locked: Boolean? = null,
        pendingEnableAfterPermission: Boolean? = null,
        presetColorIndex: Int? = null,
        currentColorProgress: Int? = null,
        normalColorProgress: Int? = null,
        alpha: Float? = null,
        textSize: Int? = null,
        y: Int? = null
    ) {
        dataStore.edit { preferences ->
            visible?.let {
                preferences[SettingsKeys.KEY_DESKTOP_LYRIC_VISIBLE] = it
            }

            locked?.let {
                preferences[SettingsKeys.KEY_DESKTOP_LYRIC_LOCKED] = it
            }

            pendingEnableAfterPermission?.let {
                preferences[SettingsKeys.KEY_DESKTOP_LYRIC_PENDING_ENABLE_AFTER_PERMISSION] = it
            }

            presetColorIndex?.let {
                preferences[SettingsKeys.KEY_DESKTOP_LYRIC_PRESET_COLOR_INDEX] = it
            }

            currentColorProgress?.let {
                preferences[SettingsKeys.KEY_DESKTOP_LYRIC_CURRENT_COLOR_PROGRESS] = it
            }

            normalColorProgress?.let {
                preferences[SettingsKeys.KEY_DESKTOP_LYRIC_NORMAL_COLOR_PROGRESS] = it
            }

            alpha?.let {
                preferences[SettingsKeys.KEY_DESKTOP_LYRIC_ALPHA] = it
            }

            textSize?.let {
                preferences[SettingsKeys.KEY_DESKTOP_LYRIC_TEXT_SIZE] = it
            }

            y?.let {
                preferences[SettingsKeys.KEY_DESKTOP_LYRIC_Y] = it
            }
        }
    }

    suspend fun disable() {
        updatePreference(
            visible = false,
            pendingEnableAfterPermission = false
        )
    }

    suspend fun enableAfterPermissionGranted() {
        updatePreference(
            visible = true,
            pendingEnableAfterPermission = false
        )
    }

    suspend fun markPendingEnableAfterPermission() {
        setPendingEnableAfterPermission(true)
    }

    suspend fun clearPendingEnableAfterPermission() {
        setPendingEnableAfterPermission(false)
    }

    private fun Preferences.toDesktopLyricPreference(): DesktopLyricPreference {
        return DesktopLyricPreference(
            visible = this[SettingsKeys.KEY_DESKTOP_LYRIC_VISIBLE] ?: DEFAULT_VISIBLE,
            locked = this[SettingsKeys.KEY_DESKTOP_LYRIC_LOCKED] ?: DEFAULT_LOCKED,
            pendingEnableAfterPermission =
                this[SettingsKeys.KEY_DESKTOP_LYRIC_PENDING_ENABLE_AFTER_PERMISSION]
                    ?: DEFAULT_PENDING_ENABLE_AFTER_PERMISSION,
            presetColorIndex =
                this[SettingsKeys.KEY_DESKTOP_LYRIC_PRESET_COLOR_INDEX]
                    ?: DEFAULT_PRESET_COLOR_INDEX,
            currentColorProgress =
                this[SettingsKeys.KEY_DESKTOP_LYRIC_CURRENT_COLOR_PROGRESS]
                    ?: DEFAULT_CURRENT_COLOR_PROGRESS,
            normalColorProgress =
                this[SettingsKeys.KEY_DESKTOP_LYRIC_NORMAL_COLOR_PROGRESS]
                    ?: DEFAULT_NORMAL_COLOR_PROGRESS,
            alpha =
                this[SettingsKeys.KEY_DESKTOP_LYRIC_ALPHA]
                    ?: DEFAULT_ALPHA,
            textSize =
                this[SettingsKeys.KEY_DESKTOP_LYRIC_TEXT_SIZE]
                    ?: DEFAULT_TEXT_SIZE,
            y =
                this[SettingsKeys.KEY_DESKTOP_LYRIC_Y]
                    ?: DEFAULT_Y
        )
    }

    private companion object {
        const val DEFAULT_VISIBLE = false
        const val DEFAULT_LOCKED = false
        const val DEFAULT_PENDING_ENABLE_AFTER_PERMISSION = false
        const val DEFAULT_PRESET_COLOR_INDEX = 0
        const val DEFAULT_CURRENT_COLOR_PROGRESS = 0
        const val DEFAULT_NORMAL_COLOR_PROGRESS = 0
        const val DEFAULT_ALPHA = 1f
        const val DEFAULT_TEXT_SIZE = 16
        const val DEFAULT_Y = -1
    }
}
