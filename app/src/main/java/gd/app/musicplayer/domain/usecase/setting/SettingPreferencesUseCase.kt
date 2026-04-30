package gd.app.musicplayer.domain.usecase.setting

import gd.app.musicplayer.util.PreferenceUtil
import kotlinx.coroutines.flow.Flow

class SettingPreferencesUseCase(
    private val preferenceUtil: PreferenceUtil
) {
    fun observePreferenceChanges(vararg keys: String): Flow<Unit> =
        preferenceUtil.observePreferenceChanges(*keys)

    fun setQueueForSearchingMode(mode: Int) = preferenceUtil.putIntPreference(KEY_QUEUE_FOR_SEARCHING, mode)
    fun setShakeEnabled(enabled: Boolean) = preferenceUtil.putBooleanPreference(KEY_SHAKE_CHANGE_MUSIC, enabled)
    fun setShakeLevel(level: Float) = preferenceUtil.setShakeLevel(level)
    fun setBluetoothLyricEnabled(enabled: Boolean) = preferenceUtil.putBooleanPreference(KEY_BLUETOOTH_LYRIC, enabled)
    fun setBluetoothAutoStartEnabled(enabled: Boolean) = preferenceUtil.putBooleanPreference(KEY_BLUETOOTH_AUTO_START, enabled)
    fun setOldNotificationEnabled(enabled: Boolean) = preferenceUtil.putBooleanPreference(KEY_OLD_NOTIFICATION, enabled)
    fun setColorNotificationEnabled(enabled: Boolean) = preferenceUtil.putBooleanPreference(KEY_COLOR_NOTIFICATION, enabled)
    fun setForwardBackwardSeconds(seconds: Int) = preferenceUtil.putIntPreference(KEY_FORWARD_BACKWARD_SECONDS, seconds)
    fun setShowForwardBackward(enabled: Boolean) = preferenceUtil.putBooleanPreference(KEY_SHOW_FORWARD_BACKWARD, enabled)
    fun setFadeDurationSeconds(seconds: Int) =
        preferenceUtil.putIntPreference(KEY_FADE_DURATION_MS, seconds.coerceIn(1, 12) * 1000)
    fun setReplayGainMode(mode: Int) = preferenceUtil.putIntPreference(KEY_REPLAY_GAIN_MODE, mode.coerceIn(0, 2))
    fun setReplayGainPreamp(withTag: Float, withoutTag: Float) {
        preferenceUtil.putFloatPreference(KEY_REPLAY_GAIN_PREAMP_WITH_TAG, withTag)
        preferenceUtil.putFloatPreference(KEY_REPLAY_GAIN_PREAMP_WITHOUT_TAG, withoutTag)
    }
    fun setLockBackgroundMode(mode: Int) = preferenceUtil.putIntPreference(KEY_LOCK_BACKGROUND, mode.coerceIn(0, 1))
    fun setPlaylistAddPosition(position: Int) = preferenceUtil.setPlaylistAddPosition(position.coerceIn(0, 1))

    fun forwardBackwardSeconds(): Int = preferenceUtil.getIntPreference(KEY_FORWARD_BACKWARD_SECONDS, 15)
    fun showForwardBackward(): Boolean = preferenceUtil.getBooleanPreference(KEY_SHOW_FORWARD_BACKWARD, false)
    fun fadeDurationSeconds(): Int = (preferenceUtil.getIntPreference(KEY_FADE_DURATION_MS, 6000) / 1000).coerceIn(1, 12)
    fun shakeEnabled(): Boolean = preferenceUtil.isShakeToChangeTrackEnabled()
    fun shakeLevel(): Float = preferenceUtil.getShakeLevel()
    fun bluetoothLyricEnabled(): Boolean = preferenceUtil.getBooleanPreference(KEY_BLUETOOTH_LYRIC, true)
    fun bluetoothAutoStartEnabled(): Boolean = preferenceUtil.isBluetoothAutoStartEnabled()
    fun oldNotificationEnabled(): Boolean = preferenceUtil.getBooleanPreference(KEY_OLD_NOTIFICATION, false)
    fun colorNotificationEnabled(): Boolean = preferenceUtil.getBooleanPreference(KEY_COLOR_NOTIFICATION, true)
    fun queueForSearchingMode(): Int = preferenceUtil.getQueueForSearchingMode()
    fun replayGainMode(): Int = preferenceUtil.getIntPreference(KEY_REPLAY_GAIN_MODE, 0)
    fun replayGainPreampWithTag(): Float = preferenceUtil.getFloatPreference(KEY_REPLAY_GAIN_PREAMP_WITH_TAG, 0f)
    fun replayGainPreampWithoutTag(): Float = preferenceUtil.getFloatPreference(KEY_REPLAY_GAIN_PREAMP_WITHOUT_TAG, 0f)
    fun lockBackgroundMode(): Int = preferenceUtil.getIntPreference(KEY_LOCK_BACKGROUND, 1)
    fun playlistAddPosition(): Int = preferenceUtil.getPlaylistAddPosition()

    companion object {
        const val KEY_FORWARD_BACKWARD_SECONDS = "time_forward_backward"
        const val KEY_SHOW_FORWARD_BACKWARD = "show_forward_backward"
        const val KEY_FADE_DURATION_MS = "fade_duration"
        const val KEY_SHAKE_CHANGE_MUSIC = "preference_shake_change_music"
        const val KEY_BLUETOOTH_LYRIC = "bluetooth_lyric"
        const val KEY_BLUETOOTH_AUTO_START = "preference_bluetooth_auto_start"
        const val KEY_OLD_NOTIFICATION = "old_notification"
        const val KEY_COLOR_NOTIFICATION = "color_notification"
        const val KEY_QUEUE_FOR_SEARCHING = "queue_for_searching"
        const val KEY_REPLAY_GAIN_MODE = "replay_gain_mode"
        const val KEY_REPLAY_GAIN_PREAMP_WITH_TAG = "preamp_with_tag"
        const val KEY_REPLAY_GAIN_PREAMP_WITHOUT_TAG = "preamp_without_tag"
        const val KEY_LOCK_BACKGROUND = "lock_background"
        const val KEY_PLAYLIST_ADD_POSITION = "preference_playlist_add_position"
    }
}

