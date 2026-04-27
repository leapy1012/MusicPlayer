package gd.app.musicplayer.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object PreferenceUtil :
    ThemePreferenceOps,
    SortPreferenceOps,
    PlayerPreferenceOps,
    SmartPlaylistPreferenceOps,
    AppPreferenceOps {

    @Volatile
    private var preferencesHolder: SharedPreferences? = null

    fun getInstance(context: Context): PreferenceUtil = apply {
        ensureInitialized(context)
    }

    private fun ensureInitialized(context: Context) {
        if (preferencesHolder != null) return

        synchronized(this) {
            if (preferencesHolder != null) return

            preferencesHolder = context.applicationContext.getSharedPreferences(
                PREFS_FILE_NAME,
                Context.MODE_PRIVATE
            )
        }

        migrateLegacyKeys()
        ensureInstallVersionInitialized()
    }

    private val preferences: SharedPreferences
        get() = checkNotNull(preferencesHolder) {
            "PreferenceUtil has not been initialized. Call PreferenceUtil.getInstance(context) first."
        }

    override fun containsPreference(key: String): Boolean =
        preferences.contains(key)

    override fun removePreferences(vararg keys: String) {
        preferences.edit {
            keys.forEach(::remove)
        }
    }

    override fun getBooleanPreference(key: String, defaultValue: Boolean): Boolean =
        preferences.getBoolean(key, defaultValue)

    override fun getFloatPreference(key: String, defaultValue: Float): Float =
        preferences.getFloat(key, defaultValue)

    override fun getIntPreference(key: String, defaultValue: Int): Int =
        preferences.getInt(key, defaultValue)

    override fun getLongPreference(key: String, defaultValue: Long): Long =
        preferences.getLong(key, defaultValue)

    override fun getStringPreference(key: String, defaultValue: String): String =
        preferences.getString(key, defaultValue) ?: defaultValue

    override fun getNullableStringPreference(key: String): String? =
        preferences.getString(key, null)

    override fun putBooleanPreference(key: String, value: Boolean) {
        preferences.edit { putBoolean(key, value) }
    }

    override fun putFloatPreference(key: String, value: Float) {
        preferences.edit { putFloat(key, value) }
    }

    override fun putIntPreference(key: String, value: Int) {
        preferences.edit { putInt(key, value) }
    }

    override fun putLongPreference(key: String, value: Long) {
        preferences.edit { putLong(key, value) }
    }

    override fun putStringPreference(key: String, value: String) {
        preferences.edit { putString(key, value) }
    }

    fun observePreferenceChanges(vararg keys: String): Flow<Unit> = callbackFlow {
        val keySet = keys.toSet()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (keySet.isEmpty() || changedKey in keySet) {
                trySend(Unit)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    private fun migrateLegacyKeys() {
        removePreferences(*LEGACY_KEYS_TO_DELETE)
    }

    private fun ensureInstallVersionInitialized() {
        if (!containsPreference(KEY_INSTALL_VERSION)) {
            putIntPreference(KEY_INSTALL_VERSION, 803)
        }
    }

    private const val PREFS_FILE_NAME = "music_preference"
    private const val KEY_INSTALL_VERSION = "install_version"

    private val LEGACY_KEYS_TO_DELETE = arrayOf(
        "preference_clear_preference",
        "preference_music_set",
        "preference_music_id",
        "preference_last_version",
        "preference_sleep_time",
        "preference_sleep_end_time",
        "pref_show_lyric",
        "preference_widget2x2",
        "preference_widget4x1",
        "preference_widget4x1White",
        "preference_auto_skin",
        "preference_open_count",
        "preference_desk_lrc_is_preset_type",
        "preference_last_tab"
    )
}
