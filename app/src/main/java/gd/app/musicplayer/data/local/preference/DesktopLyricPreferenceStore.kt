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
        pendingEnableAfterPermission: Boolean? = null
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
                    ?: DEFAULT_PENDING_ENABLE_AFTER_PERMISSION
        )
    }

    private companion object {
        const val DEFAULT_VISIBLE = false
        const val DEFAULT_LOCKED = false
        const val DEFAULT_PENDING_ENABLE_AFTER_PERMISSION = false
    }
}