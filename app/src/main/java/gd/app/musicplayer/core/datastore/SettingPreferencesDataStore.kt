package gd.app.musicplayer.core.datastore

import gd.app.musicplayer.feature.library.model.LibraryTabConfig
import kotlinx.coroutines.flow.Flow

interface SettingPreferencesDataStore {

    val playbackVolumeFadeEnabled: Flow<Boolean>

    val replayGainPreference: Flow<ReplayGainSettingPreference>
    val libraryTabConfig: Flow<List<LibraryTabConfig>>

    fun observeSettingPreferences(): Flow<SettingPreferences>
    fun observePlayMode(): Flow<Int>

    suspend fun getPlayMode(): Int
    suspend fun getForwardBackwardSeconds(): Int
    suspend fun getLibraryLastTab(): Int
    suspend fun getLockScreenEnabled(): Boolean
    suspend fun getSimultaneousPlayEnabled(): Boolean
    suspend fun setLibraryLastTab(tabId: Int)
    suspend fun getLibraryTabConfig(): List<LibraryTabConfig>
    suspend fun setLibraryTabConfig(items: List<LibraryTabConfig>)

    suspend fun cyclePlayMode(): Int
    suspend fun getTrackClickOperationEnabled(): Boolean
    suspend fun getReplaySongEnabled(): Boolean
    suspend fun getShakeLevel(): Float
    suspend fun getShakeEnabled(): Boolean
    suspend fun getShowHiddenFolders(): Boolean
    fun observeShowHiddenFolders(): Flow<Boolean>
    suspend fun updatePlayMode(mode: Int)
    suspend fun updateForwardBackwardSeconds(seconds: Int)
    suspend fun updateShowForwardBackward(enabled: Boolean)
    suspend fun updateShowHiddenFolders(enabled: Boolean)
    suspend fun updateShowKeepAliveDot(enabled: Boolean)
    suspend fun updateFadeDurationSeconds(seconds: Int)
    suspend fun updateSwipeChangeSongsEnabled(enabled: Boolean)
    suspend fun updateSimultaneousPlayEnabled(enabled: Boolean)
    suspend fun updateVolumeFadeEnabled(enabled: Boolean)
    suspend fun updateGaplessPlaybackEnabled(enabled: Boolean)
    suspend fun updateCrossFadeEnabled(enabled: Boolean)
    suspend fun updateTrackClickOperationEnabled(enabled: Boolean)
    suspend fun updateReplaySongEnabled(enabled: Boolean)
    suspend fun updateQueueForSearchingMode(mode: Int)
    suspend fun updateReplayGainMode(mode: Int)
    suspend fun updateReplayGainPreamp(withTag: Float, withoutTag: Float)
    suspend fun updateLockBackgroundMode(mode: Int)
    suspend fun updateLockScreenEnabled(enabled: Boolean)
    suspend fun updateBluetoothLyricEnabled(enabled: Boolean)
    suspend fun updateBluetoothAutoStartEnabled(enabled: Boolean)
    suspend fun updateBluetoothAutoStopEnabled(enabled: Boolean)
    suspend fun updateHeadsetInPlayEnabled(enabled: Boolean)
    suspend fun updateHeadsetOutStopEnabled(enabled: Boolean)
    suspend fun updateHeadsetControlAllowed(enabled: Boolean)
    suspend fun updateOldNotificationEnabled(enabled: Boolean)
    suspend fun updateColorNotificationEnabled(enabled: Boolean)
    suspend fun updateNotificationBarEnabled(enabled: Boolean)
    suspend fun updateShakeEnabled(enabled: Boolean)
    suspend fun updateShakeLevel(level: Float)
    suspend fun updatePlaylistAddPosition(position: Int)
    suspend fun updateClickAddQueueEnabled(enabled: Boolean)
}
