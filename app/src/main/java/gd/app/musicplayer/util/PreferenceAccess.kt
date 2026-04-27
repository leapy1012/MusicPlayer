package gd.app.musicplayer.util

interface PreferenceAccess {
    fun containsPreference(key: String): Boolean
    fun removePreferences(vararg keys: String)
    fun getBooleanPreference(key: String, defaultValue: Boolean): Boolean
    fun getFloatPreference(key: String, defaultValue: Float): Float
    fun getIntPreference(key: String, defaultValue: Int): Int
    fun getLongPreference(key: String, defaultValue: Long): Long
    fun getStringPreference(key: String, defaultValue: String): String
    fun getNullableStringPreference(key: String): String?
    fun putBooleanPreference(key: String, value: Boolean)
    fun putFloatPreference(key: String, value: Float)
    fun putIntPreference(key: String, value: Int)
    fun putLongPreference(key: String, value: Long)
    fun putStringPreference(key: String, value: String)
}
