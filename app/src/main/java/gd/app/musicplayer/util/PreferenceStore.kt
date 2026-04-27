package gd.app.musicplayer.util

import android.content.SharedPreferences
import androidx.core.content.edit

class PreferenceStore(
    private val preferences: SharedPreferences
) {

    fun contains(key: String): Boolean = preferences.contains(key)

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        preferences.getBoolean(key, defaultValue)

    fun getInt(key: String, defaultValue: Int = 0): Int =
        preferences.getInt(key, defaultValue)

    fun getLong(key: String, defaultValue: Long = 0L): Long =
        preferences.getLong(key, defaultValue)

    fun getFloat(key: String, defaultValue: Float = 0f): Float =
        preferences.getFloat(key, defaultValue)

    fun getString(key: String, defaultValue: String? = null): String? =
        preferences.getString(key, defaultValue)

    fun putBoolean(key: String, value: Boolean) {
        preferences.edit { putBoolean(key, value) }
    }

    fun putInt(key: String, value: Int) {
        preferences.edit { putInt(key, value) }
    }

    fun putLong(key: String, value: Long) {
        preferences.edit { putLong(key, value) }
    }

    fun putFloat(key: String, value: Float) {
        preferences.edit { putFloat(key, value) }
    }

    fun putString(key: String, value: String?) {
        preferences.edit { putString(key, value) }
    }

    fun remove(vararg keys: String) {
        preferences.edit().apply {
            keys.forEach(::remove)
            apply()
        }
    }
}