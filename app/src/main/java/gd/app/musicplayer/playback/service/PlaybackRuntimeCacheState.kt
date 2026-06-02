package gd.app.musicplayer.playback.service

import gd.app.musicplayer.core.datastore.DesktopLyricPreference
import gd.app.musicplayer.core.datastore.SettingPreferences

internal class PlaybackRuntimeCacheState {
    @Volatile
    var pendingRestoreTrackId: Long? = null

    @Volatile
    var isNightMode: Boolean = false

    @Volatile
    var latestSettingPreferences: SettingPreferences = SettingPreferences()

    @Volatile
    var latestDesktopLyricPreference: DesktopLyricPreference = DesktopLyricPreference()

    fun reset() {
        pendingRestoreTrackId = null
        isNightMode = false
        latestSettingPreferences = SettingPreferences()
        latestDesktopLyricPreference = DesktopLyricPreference()
    }
}
