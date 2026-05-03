package gd.app.musicplayer.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class PreferenceStore(
    context: Context,
    fileName: String
) {
    private val appContext = context.applicationContext
    private val dataStore: DataStore<Preferences> = PreferenceStoreDataStoreRegistry.getDataStore(
        appContext,
        fileName
    )

    fun contains(key: String): Boolean = runBlocking {
        dataStore.data.first().asMap().keys.any { it.name == key }
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        runBlocking { dataStore.data.first()[booleanPreferencesKey(key)] ?: defaultValue }

    fun getInt(key: String, defaultValue: Int = 0): Int =
        runBlocking { dataStore.data.first()[intPreferencesKey(key)] ?: defaultValue }

    fun getLong(key: String, defaultValue: Long = 0L): Long =
        runBlocking { dataStore.data.first()[longPreferencesKey(key)] ?: defaultValue }

    fun getFloat(key: String, defaultValue: Float = 0f): Float =
        runBlocking { dataStore.data.first()[floatPreferencesKey(key)] ?: defaultValue }

    fun getString(key: String, defaultValue: String? = null): String? =
        runBlocking { dataStore.data.first()[stringPreferencesKey(key)] ?: defaultValue }

    fun putBoolean(key: String, value: Boolean) {
        runBlocking {
            dataStore.edit { it[booleanPreferencesKey(key)] = value }
        }
    }

    fun putInt(key: String, value: Int) {
        runBlocking {
            dataStore.edit { it[intPreferencesKey(key)] = value }
        }
    }

    fun putLong(key: String, value: Long) {
        runBlocking {
            dataStore.edit { it[longPreferencesKey(key)] = value }
        }
    }

    fun putFloat(key: String, value: Float) {
        runBlocking {
            dataStore.edit { it[floatPreferencesKey(key)] = value }
        }
    }

    fun putString(key: String, value: String?) {
        runBlocking {
            dataStore.edit { preferences ->
                val stringKey = stringPreferencesKey(key)
                if (value == null) {
                    preferences.remove(stringKey)
                } else {
                    preferences[stringKey] = value
                }
            }
        }
    }

    fun remove(vararg keys: String) {
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
    }
}

internal object PreferenceStoreDataStoreRegistry {
    private val Context.musicPreferenceDataStore by preferencesDataStore(
        name = "music_preference",
        produceMigrations = { context ->
            listOf(SharedPreferencesMigration(context, "music_preference"))
        }
    )
    private val Context.musicWidgetDataStore by preferencesDataStore(
        name = "music_widget_preference",
        produceMigrations = { context ->
            listOf(SharedPreferencesMigration(context, "music_widget_preference"))
        }
    )
    private val Context.widgetConfigDataStore by preferencesDataStore(
        name = "widget_config_store",
        produceMigrations = { context ->
            listOf(SharedPreferencesMigration(context, "widget_config_store"))
        }
    )
    private val Context.musicEffectsDataStore by preferencesDataStore(
        name = "music",
        produceMigrations = { context ->
            listOf(SharedPreferencesMigration(context, "music"))
        }
    )

    fun getDataStore(context: Context, fileName: String): DataStore<Preferences> {
        return when (fileName) {
            "music_widget_preference" -> context.musicWidgetDataStore
            "widget_config_store" -> context.widgetConfigDataStore
            "music" -> context.musicEffectsDataStore
            else -> context.musicPreferenceDataStore
        }
    }
}
