package gd.app.musicplayer.ui.feature.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.domain.usecase.setting.SettingPreferencesUseCase
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SettingUiState(
    val forwardBackwardSeconds: Int = 15,
    val showForwardBackward: Boolean = false,
    val fadeDurationSeconds: Int = 6,
    val shakeEnabled: Boolean = false,
    val shakeLevel: Float = 0.5f,
    val bluetoothLyricEnabled: Boolean = true,
    val bluetoothAutoStartEnabled: Boolean = false,
    val oldNotificationEnabled: Boolean = false,
    val colorNotificationEnabled: Boolean = true,
    val queueForSearchingMode: Int = 0,
    val replayGainMode: Int = 0,
    val replayGainPreampWithTag: Float = 0f,
    val replayGainPreampWithoutTag: Float = 0f,
    val lockBackgroundMode: Int = 1,
    val playlistAddPosition: Int = 0
)

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val settingPreferencesUseCase: SettingPreferencesUseCase
) : ViewModel() {

    val uiState: StateFlow<SettingUiState> = settingPreferencesUseCase.observePreferenceChanges(
            SettingPreferencesUseCase.KEY_FORWARD_BACKWARD_SECONDS,
            SettingPreferencesUseCase.KEY_SHOW_FORWARD_BACKWARD,
            SettingPreferencesUseCase.KEY_FADE_DURATION_MS,
            SettingPreferencesUseCase.KEY_SHAKE_CHANGE_MUSIC,
            SettingPreferencesUseCase.KEY_BLUETOOTH_LYRIC,
            SettingPreferencesUseCase.KEY_BLUETOOTH_AUTO_START,
            SettingPreferencesUseCase.KEY_OLD_NOTIFICATION,
            SettingPreferencesUseCase.KEY_COLOR_NOTIFICATION,
            SettingPreferencesUseCase.KEY_QUEUE_FOR_SEARCHING,
            SettingPreferencesUseCase.KEY_REPLAY_GAIN_MODE,
            SettingPreferencesUseCase.KEY_REPLAY_GAIN_PREAMP_WITH_TAG,
            SettingPreferencesUseCase.KEY_REPLAY_GAIN_PREAMP_WITHOUT_TAG,
            SettingPreferencesUseCase.KEY_LOCK_BACKGROUND,
            SettingPreferencesUseCase.KEY_PLAYLIST_ADD_POSITION
        ).map { buildUiState() }
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildUiState()
    )

    fun setQueueForSearchingMode(mode: Int) {
        settingPreferencesUseCase.setQueueForSearchingMode(mode)
    }

    fun setShakeEnabled(enabled: Boolean) {
        settingPreferencesUseCase.setShakeEnabled(enabled)
    }

    fun setShakeLevel(level: Float) {
        settingPreferencesUseCase.setShakeLevel(level)
    }

    fun setBluetoothLyricEnabled(enabled: Boolean) {
        settingPreferencesUseCase.setBluetoothLyricEnabled(enabled)
    }

    fun setBluetoothAutoStartEnabled(enabled: Boolean) {
        settingPreferencesUseCase.setBluetoothAutoStartEnabled(enabled)
    }

    fun setOldNotificationEnabled(enabled: Boolean) {
        settingPreferencesUseCase.setOldNotificationEnabled(enabled)
    }

    fun setColorNotificationEnabled(enabled: Boolean) {
        settingPreferencesUseCase.setColorNotificationEnabled(enabled)
    }

    fun setForwardBackwardSeconds(seconds: Int) {
        settingPreferencesUseCase.setForwardBackwardSeconds(seconds)
    }

    fun setShowForwardBackward(enabled: Boolean) {
        settingPreferencesUseCase.setShowForwardBackward(enabled)
    }

    fun setFadeDurationSeconds(seconds: Int) {
        settingPreferencesUseCase.setFadeDurationSeconds(seconds)
    }

    fun setReplayGainMode(mode: Int) {
        settingPreferencesUseCase.setReplayGainMode(mode)
    }

    fun setReplayGainPreamp(withTag: Float, withoutTag: Float) {
        settingPreferencesUseCase.setReplayGainPreamp(withTag, withoutTag)
    }

    fun setLockBackgroundMode(mode: Int) {
        settingPreferencesUseCase.setLockBackgroundMode(mode)
    }

    fun setPlaylistAddPosition(position: Int) {
        settingPreferencesUseCase.setPlaylistAddPosition(position)
    }

    private fun buildUiState(): SettingUiState {
        return SettingUiState(
            forwardBackwardSeconds = settingPreferencesUseCase.forwardBackwardSeconds(),
            showForwardBackward = settingPreferencesUseCase.showForwardBackward(),
            fadeDurationSeconds = settingPreferencesUseCase.fadeDurationSeconds(),
            shakeEnabled = settingPreferencesUseCase.shakeEnabled(),
            shakeLevel = settingPreferencesUseCase.shakeLevel(),
            bluetoothLyricEnabled = settingPreferencesUseCase.bluetoothLyricEnabled(),
            bluetoothAutoStartEnabled = settingPreferencesUseCase.bluetoothAutoStartEnabled(),
            oldNotificationEnabled = settingPreferencesUseCase.oldNotificationEnabled(),
            colorNotificationEnabled = settingPreferencesUseCase.colorNotificationEnabled(),
            queueForSearchingMode = settingPreferencesUseCase.queueForSearchingMode(),
            replayGainMode = settingPreferencesUseCase.replayGainMode(),
            replayGainPreampWithTag = settingPreferencesUseCase.replayGainPreampWithTag(),
            replayGainPreampWithoutTag = settingPreferencesUseCase.replayGainPreampWithoutTag(),
            lockBackgroundMode = settingPreferencesUseCase.lockBackgroundMode(),
            playlistAddPosition = settingPreferencesUseCase.playlistAddPosition()
        )
    }

}
