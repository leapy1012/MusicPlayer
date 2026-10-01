package gd.app.musicplayer.core.datastore

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.di.ApplicationScope
import gd.app.musicplayer.domain.model.LibraryTabConfig
import gd.app.musicplayer.domain.model.LibraryTabConfigStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus

/**
 * Sync mirror of library tab prefs — same idea as original [m5.n.h] /
 * SharedPreferences [preference_tab] / [preference_tab_id].
 *
 * Seeded from SharedPreferences on construct (no coroutine), kept warm from
 * DataStore, and updated on writes so Library open never [runBlocking]s.
 */
@Singleton
class LibraryTabPreferencesCache @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope appScope: CoroutineScope,
    musicDataStore: MusicDataStore
) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cachedTabs: List<LibraryTabConfig> =
        LibraryTabConfigStore.parse(preferences.getString(KEY_TAB_CONFIG, null))

    @Volatile
    private var cachedLastTabId: Int =
        preferences.getInt(KEY_LAST_TAB, DEFAULT_LAST_TAB)

    init {
        musicDataStore.data
            .onEach { prefs ->
                val tabs = LibraryTabConfigStore.parse(prefs[SettingsKeys.LIBRARY_TAB_CONFIG])
                val lastTab = prefs[SettingsKeys.LIBRARY_LAST_TAB] ?: DEFAULT_LAST_TAB
                cachedTabs = tabs
                cachedLastTabId = lastTab
                mirrorToSharedPreferences(tabs, lastTab)
            }
            .launchIn(appScope + Dispatchers.IO)
    }

    /** Visible tabs for first Library paint — never suspends. */
    fun peekVisibleTabs(): List<LibraryTabConfig> {
        return LibraryTabConfigStore.visibleItems(cachedTabs)
    }

    fun peekLastTabId(): Int = cachedLastTabId

    fun onTabConfigWritten(items: List<LibraryTabConfig>) {
        cachedTabs = items
        preferences.edit()
            .putString(KEY_TAB_CONFIG, LibraryTabConfigStore.serialize(items))
            .apply()
    }

    fun onLastTabWritten(tabId: Int) {
        cachedLastTabId = tabId
        preferences.edit()
            .putInt(KEY_LAST_TAB, tabId)
            .apply()
    }

    private fun mirrorToSharedPreferences(tabs: List<LibraryTabConfig>, lastTabId: Int) {
        preferences.edit()
            .putString(KEY_TAB_CONFIG, LibraryTabConfigStore.serialize(tabs))
            .putInt(KEY_LAST_TAB, lastTabId)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "music_preference"
        const val KEY_TAB_CONFIG = "preference_tab"
        const val KEY_LAST_TAB = "preference_tab_id"
        const val DEFAULT_LAST_TAB = 0
    }
}
