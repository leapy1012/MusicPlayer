package gd.app.musicplayer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.di.ThemeSettingsDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class ThemeSettings(
    val themeType: Int = ThemeSettingPreferenceStore.DEFAULT_THEME_TYPE,
    val overlayColor: Int = ThemeSettingPreferenceStore.DEFAULT_THEME_OVERLAY_COLOR,
    val blur: Int = ThemeSettingPreferenceStore.DEFAULT_THEME_BLUR,
    val themeColor: Int = ThemeSettingPreferenceStore.DEFAULT_THEME_COLOR,
    val themeDialogEnabled: Boolean = ThemeSettingPreferenceStore.DEFAULT_THEME_DIALOG_ENABLED,
    val imageName: String = ThemeSettingPreferenceStore.DEFAULT_THEME_IMAGE,
    val imageUris: List<String> = emptyList()
)

@Singleton
class ThemeSettingPreferenceStore @Inject constructor(
    @param:ThemeSettingsDataStore
    private val dataStore: DataStore<Preferences>
) {

    val settings: Flow<ThemeSettings> =
        dataStore.data.map { preferences ->
            preferences.toThemeSettings()
        }

    suspend fun getSettingsSnapshot(): ThemeSettings {
        return dataStore.data.first().toThemeSettings()
    }

    suspend fun getThemeType(): Int {
        return getInt(KEY_THEME_TYPE, DEFAULT_THEME_TYPE)
    }

    suspend fun setThemeType(themeType: Int) {
        set(KEY_THEME_TYPE, themeType)
    }

    suspend fun getThemeOverlayColor(): Int {
        return getInt(KEY_THEME_OVERLAY_COLOR, DEFAULT_THEME_OVERLAY_COLOR)
    }

    suspend fun setThemeOverlayColor(overlay: Int) {
        set(KEY_THEME_OVERLAY_COLOR, overlay)
    }

    suspend fun getThemeBlur(): Int {
        return getInt(KEY_THEME_BLUR, DEFAULT_THEME_BLUR)
    }

    suspend fun setThemeBlur(blur: Int) {
        set(KEY_THEME_BLUR, blur.coerceAtLeast(0))
    }

    suspend fun getThemeColor(): Int {
        return getInt(KEY_THEME_COLOR, DEFAULT_THEME_COLOR)
    }

    suspend fun setThemeColor(color: Int) {
        set(KEY_THEME_COLOR, color)
    }

    suspend fun isThemeDialogEnabled(): Boolean {
        return getBoolean(KEY_THEME_DIALOG, DEFAULT_THEME_DIALOG_ENABLED)
    }

    suspend fun setThemeDialogEnabled(enabled: Boolean) {
        set(KEY_THEME_DIALOG, enabled)
    }

    suspend fun getThemeImageName(): String {
        return getString(KEY_IMAGE_NAME, DEFAULT_THEME_IMAGE)
    }

    suspend fun setThemeImageName(fileName: String) {
        val normalized = fileName.trim()
        if (normalized.isEmpty()) return

        set(KEY_IMAGE_NAME, normalized)
    }

    suspend fun getThemeImageUris(): List<String> {
        val preferences = dataStore.data.first()
        return preferences.getThemeImageUris()
    }

    suspend fun setThemeImageUris(paths: List<String>) {
        val normalized = paths.normalizeThemeImageUris()
        set(KEY_THEME_SKIN_URIS, normalized.joinToString(SKIN_URI_SEPARATOR))
    }

    suspend fun addThemeImageUri(path: String) {
        val normalizedPath = path.trim()
        if (normalizedPath.isEmpty()) return

        val currentUris = getThemeImageUris()
        setThemeImageUris(currentUris + normalizedPath)
    }

    suspend fun removeThemeImageUri(path: String) {
        val normalizedPath = path.trim()
        if (normalizedPath.isEmpty()) return

        val currentUris = getThemeImageUris()
        setThemeImageUris(currentUris.filterNot { it == normalizedPath })
    }

    suspend fun clearThemeImageUris() {
        set(KEY_THEME_SKIN_URIS, "")
    }

    private fun Preferences.toThemeSettings(): ThemeSettings {
        return ThemeSettings(
            overlayColor = this[KEY_THEME_OVERLAY_COLOR] ?: DEFAULT_THEME_OVERLAY_COLOR,
            blur = this[KEY_THEME_BLUR] ?: DEFAULT_THEME_BLUR,
            themeColor = this[KEY_THEME_COLOR] ?: DEFAULT_THEME_COLOR,
            themeDialogEnabled = this[KEY_THEME_DIALOG] ?: DEFAULT_THEME_DIALOG_ENABLED,
            imageName = this[KEY_IMAGE_NAME] ?: DEFAULT_THEME_IMAGE,
            imageUris = getThemeImageUris()
        )
    }

    private fun Preferences.getThemeImageUris(): List<String> {
        val storedUris = this[KEY_THEME_SKIN_URIS]
            .orEmpty()
            .split(SKIN_URI_SEPARATOR)
            .map(String::trim)
            .filter(String::isNotEmpty)

        val selectedImage = this[KEY_IMAGE_NAME] ?: DEFAULT_THEME_IMAGE

        val migrated = if (selectedImage.startsWith("/")) {
            storedUris + selectedImage
        } else {
            storedUris
        }

        return migrated.distinct()
    }

    private fun List<String>.normalizeThemeImageUris(): List<String> {
        return map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
    }

    private suspend fun getInt(
        key: Preferences.Key<Int>,
        defaultValue: Int
    ): Int {
        return dataStore.data.first()[key] ?: defaultValue
    }

    private suspend fun getString(
        key: Preferences.Key<String>,
        defaultValue: String
    ): String {
        return dataStore.data.first()[key] ?: defaultValue
    }

    private suspend fun getBoolean(
        key: Preferences.Key<Boolean>,
        defaultValue: Boolean
    ): Boolean {
        return dataStore.data.first()[key] ?: defaultValue
    }

    private suspend fun <T> set(
        key: Preferences.Key<T>,
        value: T
    ) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    companion object {
        const val DEFAULT_THEME_TYPE = ThemeManager.THEME_TYPE_PICTURE
        const val DEFAULT_THEME_IMAGE = "nature_01.webp"
        const val DEFAULT_THEME_OVERLAY_COLOR = 855638016
        const val DEFAULT_THEME_BLUR = 0
        const val DEFAULT_THEME_COLOR = -12467
        const val DEFAULT_THEME_DIALOG_ENABLED = true

        private const val SKIN_URI_SEPARATOR = "&&"

        private val KEY_THEME_SKIN_URIS = stringPreferencesKey("skin_uris")
        private val KEY_THEME_OVERLAY_COLOR = intPreferencesKey("theme_overlay_color")
        private val KEY_THEME_TYPE = intPreferencesKey("theme_type")
        private val KEY_THEME_BLUR = intPreferencesKey("theme_blur")
        private val KEY_THEME_COLOR = intPreferencesKey("theme_color")
        private val KEY_THEME_DIALOG = booleanPreferencesKey("theme_dialog")
        private val KEY_IMAGE_NAME = stringPreferencesKey("image_name")
    }
}