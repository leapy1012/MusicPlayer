package gd.app.musicplayer.core.datastore

import android.content.Context
import android.content.SharedPreferences

/**
 * Original [h9.e] / [h9.d]: PreferenceItemView reads/writes a named SharedPreferences file
 * synchronously so the Settings first frame matches persisted toggles.
 */
class PreferenceSharedStore(
    private val fileName: String,
    private val mode: Int = Context.MODE_PRIVATE
) {
    fun getBoolean(context: Context, key: String, defaultValue: Boolean): Boolean {
        return prefs(context)?.getBoolean(key, defaultValue) ?: defaultValue
    }

    fun putBoolean(context: Context, key: String, value: Boolean) {
        prefs(context)?.edit()?.putBoolean(key, value)?.apply()
    }

    fun prefs(context: Context): SharedPreferences? {
        return context.applicationContext.getSharedPreferences(fileName, mode)
    }

    companion object {
        const val MUSIC_PREFERENCE = "music_preference"
        const val MUSIC = "music"
    }
}
