package gd.app.musicplayer.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object PreferenceUtil :
    ThemePreferenceOps,
    SortPreferenceOps,
    PlayerPreferenceOps,
    SmartPlaylistPreferenceOps,
    AppPreferenceOps {

    @Volatile
    private var dataStoreHolder: DataStore<Preferences>? = null
    private val preferenceChanges = MutableSharedFlow<String>(extraBufferCapacity = 128)

    fun getInstance(context: Context): PreferenceUtil = apply {
        ensureInitialized(context)
    }

    private fun ensureInitialized(context: Context) {
        if (dataStoreHolder != null) return

        synchronized(this) {
            if (dataStoreHolder != null) return
            dataStoreHolder = PreferenceStoreDataStoreRegistry.getDataStore(
                context = context.applicationContext,
                fileName = PREFS_FILE_NAME
            )
        }

        migrateLegacyKeys()
        ensureInstallVersionInitialized()
    }

    private val dataStore: DataStore<Preferences>
        get() = checkNotNull(dataStoreHolder) {
            "PreferenceUtil has not been initialized. Call PreferenceUtil.getInstance(context) first."
        }

    override fun containsPreference(key: String): Boolean =
        runBlocking { dataStore.data.first().asMap().keys.any { it.name == key } }

    override fun removePreferences(vararg keys: String) {
        runBlocking {
            dataStore.edit { preferences ->
                keys.forEach { key ->
                    preferences.remove(stringPreferencesKey(key))
                    preferences.remove(booleanPreferencesKey(key))
                    preferences.remove(intPreferencesKey(key))
                    preferences.remove(longPreferencesKey(key))
                    preferences.remove(floatPreferencesKey(key))
                }
            }
        }
        keys.forEach(preferenceChanges::tryEmit)
    }

    override fun getBooleanPreference(key: String, defaultValue: Boolean): Boolean =
        runBlocking { dataStore.data.first()[booleanPreferencesKey(key)] ?: defaultValue }

    override fun getFloatPreference(key: String, defaultValue: Float): Float =
        runBlocking { dataStore.data.first()[floatPreferencesKey(key)] ?: defaultValue }

    override fun getIntPreference(key: String, defaultValue: Int): Int =
        runBlocking { dataStore.data.first()[intPreferencesKey(key)] ?: defaultValue }

    override fun getLongPreference(key: String, defaultValue: Long): Long =
        runBlocking { dataStore.data.first()[longPreferencesKey(key)] ?: defaultValue }

    override fun getStringPreference(key: String, defaultValue: String): String =
        runBlocking { dataStore.data.first()[stringPreferencesKey(key)] ?: defaultValue }

    override fun getNullableStringPreference(key: String): String? =
        runBlocking { dataStore.data.first()[stringPreferencesKey(key)] }

    override fun putBooleanPreference(key: String, value: Boolean) {
        runBlocking {
            dataStore.edit { it[booleanPreferencesKey(key)] = value }
        }
        preferenceChanges.tryEmit(key)
    }

    override fun putFloatPreference(key: String, value: Float) {
        runBlocking {
            dataStore.edit { it[floatPreferencesKey(key)] = value }
        }
        preferenceChanges.tryEmit(key)
    }

    override fun putIntPreference(key: String, value: Int) {
        runBlocking {
            dataStore.edit { it[intPreferencesKey(key)] = value }
        }
        preferenceChanges.tryEmit(key)
    }

    override fun putLongPreference(key: String, value: Long) {
        runBlocking {
            dataStore.edit { it[longPreferencesKey(key)] = value }
        }
        preferenceChanges.tryEmit(key)
    }

    override fun putStringPreference(key: String, value: String) {
        runBlocking {
            dataStore.edit { it[stringPreferencesKey(key)] = value }
        }
        preferenceChanges.tryEmit(key)
    }

    fun observePreferenceChanges(vararg keys: String): Flow<Unit> = callbackFlow {
        val keySet = keys.toSet()
        val collectorJob = launch {
            preferenceChanges.collect { changedKey ->
                if (keySet.isEmpty() || changedKey in keySet) {
                    trySend(Unit)
                }
            }
        }
        trySend(Unit)
        awaitClose {
            collectorJob.cancel()
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
