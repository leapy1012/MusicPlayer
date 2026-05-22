package gd.app.musicplayer.core.datastore

import gd.app.musicplayer.feature.library.model.LibraryTabConfig
import gd.app.musicplayer.feature.library.model.LibraryTabConfigStore

data class SettingPreferences(
    val playMode: Int = 1,
    val normal: NormalSettingPreference = NormalSettingPreference(),
    val lyrics: LyricsSettingPreference = LyricsSettingPreference(),
    val audio: AudioSettingPreference = AudioSettingPreference(),
    val replayGain: ReplayGainSettingPreference = ReplayGainSettingPreference(),
    val playlist: PlaylistSettingPreference = PlaylistSettingPreference(),
    val notification: NotificationSettingPreference = NotificationSettingPreference(),
    val lockscreen: LockscreenSettingPreference = LockscreenSettingPreference(),
    val headset: HeadsetSettingPreference = HeadsetSettingPreference()
)

data class NormalSettingPreference(
    val forwardBackwardSeconds: Int = 15,
    val showForwardBackward: Boolean = false,
    val showKeepAliveDot: Boolean = true,
    val queueForSearchingMode: Int = 0,
    val showHiddenFolders: Boolean = true,
    val libraryTabConfig: List<LibraryTabConfig> = LibraryTabConfigStore.defaultItems
)

data class LyricsSettingPreference(
    val bluetoothLyricEnabled: Boolean = true,
    val lyricColor: Int = -9371,
    val lyricTextSize: Float = 16f,
    val lyricAlign: Int = 1,
    val lyricStyle: Int = 0,
    val lyricAutoScrollEnabled: Boolean = false
)

data class AudioSettingPreference(
    val fadeDurationSeconds: Int = 6,
    val shakeEnabled: Boolean = false,
    val shakeLevel: Float = 0.5f,
    val swipeChangeSongsEnabled: Boolean = true,
    val simultaneousPlayEnabled: Boolean = false,
    val volumeFadeEnabled: Boolean = false,
    val gaplessPlaybackEnabled: Boolean = false,
    val crossFadeEnabled: Boolean = false,
    val trackClickOperationEnabled: Boolean = false,
    val replaySongEnabled: Boolean = false
)

data class ReplayGainSettingPreference(
    val mode: Int = 0,
    val preampWithTag: Float = 0f,
    val preampWithoutTag: Float = 0f
)

data class PlaylistSettingPreference(
    val addPosition: Int = 0,
    val clickAddQueueEnabled: Boolean = false
)

data class NotificationSettingPreference(
    val notificationBarEnabled: Boolean = false,
    val oldNotificationEnabled: Boolean = false,
    val colorNotificationEnabled: Boolean = true
)

data class LockscreenSettingPreference(
    val backgroundMode: Int = 1,
    val lockScreenEnabled: Boolean = true
)

data class HeadsetSettingPreference(
    val bluetoothAutoStartEnabled: Boolean = false,
    val headsetInPlayEnabled: Boolean = false,
    val headsetOutStopEnabled: Boolean = true,
    val bluetoothAutoStopEnabled: Boolean = true,
    val headsetControlAllowed: Boolean = true
)
