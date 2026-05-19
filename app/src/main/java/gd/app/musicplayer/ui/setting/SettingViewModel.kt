package gd.app.musicplayer.ui.setting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.R
import gd.app.musicplayer.data.local.preference.DesktopLyricPreferenceStore
import gd.app.musicplayer.data.local.preference.SettingPreferences
import gd.app.musicplayer.data.local.preference.SettingPreferencesDataStore
import gd.app.musicplayer.data.local.preference.SoundEffectPreferences
import gd.app.musicplayer.data.local.preference.StatusBarLyricPreference
import gd.app.musicplayer.data.local.preference.StatusBarLyricPreferenceStore
import gd.app.musicplayer.domain.repository.ThemeRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.round

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val settingPreferences: SettingPreferencesDataStore,
    private val desktopLyricPreferenceStore: DesktopLyricPreferenceStore,
    private val statusBarLyricPreferenceStore: StatusBarLyricPreferenceStore,
    private val soundEffectPreferences: SoundEffectPreferences,
    private val themeRepo: ThemeRepo
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        combine(
            settingPreferences.observeSettingPreferences(),
            soundEffectPreferences.equalizerPreference,
            desktopLyricPreferenceStore.desktopLyricPreference,
            statusBarLyricPreferenceStore.preference
        ) { preferences, equalizer, desktopLyricPreference, statusBarLyricPreference ->
            preferences.toUiState(
                useTenBand = equalizer.bandMode == SoundEffectPreferences.TEN_BAND_MODE,
                desktopLyricPreference = desktopLyricPreference,
                statusBarLyricPreference = statusBarLyricPreference
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingPreferences().toUiState()
            )


    fun setUseTenBand(enabled: Boolean) = update {
        soundEffectPreferences.setEqualizerBandMode(
            if (enabled) SoundEffectPreferences.TEN_BAND_MODE else SoundEffectPreferences.FIVE_BAND_MODE
        )
    }

    fun setShowHiddenFolders(enabled: Boolean) = update {
        settingPreferences.updateShowHiddenFolders(enabled)
    }

    fun setDarkModeEnabled(enabled: Boolean) = update {
        themeRepo.toggleDarkMode(enabled)
    }

    fun setShowForwardBackward(enabled: Boolean) = update {
        settingPreferences.updateShowForwardBackward(enabled)
    }

    fun setForwardBackwardSeconds(seconds: Int) = update {
        settingPreferences.updateForwardBackwardSeconds(seconds)
    }

    fun setQueueForSearchingMode(mode: Int) = update {
        settingPreferences.updateQueueForSearchingMode(mode)
    }

    fun setBluetoothLyricEnabled(enabled: Boolean) = update {
        settingPreferences.updateBluetoothLyricEnabled(enabled)
    }

    fun setDesktopLyricsVisible(visible: Boolean) = update {
        desktopLyricPreferenceStore.setVisible(visible)
    }

    fun setDesktopLyricsLocked(locked: Boolean) = update {
        desktopLyricPreferenceStore.setLocked(locked)
    }

    fun setDesktopLyricsPendingEnableAfterPermission(pending: Boolean) = update {
        desktopLyricPreferenceStore.setPendingEnableAfterPermission(pending)
    }

    fun setShakeEnabled(enabled: Boolean) = update {
        settingPreferences.updateShakeEnabled(enabled)
    }

    fun setShakeLevel(level: Float) = update {
        settingPreferences.updateShakeLevel(level.coerceIn(0f, 1f))
    }

    fun setSwipeChangeSongsEnabled(enabled: Boolean) = update {
        settingPreferences.updateSwipeChangeSongsEnabled(enabled)
    }

    fun setSimultaneousPlayEnabled(enabled: Boolean) = update {
        settingPreferences.updateSimultaneousPlayEnabled(enabled)
    }

    fun setVolumeFadeEnabled(enabled: Boolean) = update {
        settingPreferences.updateVolumeFadeEnabled(enabled)
    }

    fun setGaplessPlaybackEnabled(enabled: Boolean) = update {
        settingPreferences.updateGaplessPlaybackEnabled(enabled)
        if (enabled) {
            settingPreferences.updateCrossFadeEnabled(false)
        }
    }

    fun setCrossFadeEnabled(enabled: Boolean) = update {
        settingPreferences.updateCrossFadeEnabled(enabled)
        if (enabled) {
            settingPreferences.updateGaplessPlaybackEnabled(false)
        }
    }

    fun setFadeDurationSeconds(seconds: Int) = update {
        settingPreferences.updateFadeDurationSeconds(seconds)
    }

    fun setTrackClickOperationEnabled(enabled: Boolean) = update {
        settingPreferences.updateTrackClickOperationEnabled(enabled)
    }

    fun setReplaySongEnabled(enabled: Boolean) = update {
        settingPreferences.updateReplaySongEnabled(enabled)
    }

    fun setReplayGainMode(mode: Int) = update {
        settingPreferences.updateReplayGainMode(mode)
    }

    fun setReplayGainPreamp(withTag: Float, withoutTag: Float) = update {
        settingPreferences.updateReplayGainPreamp(withTag, withoutTag)
    }

    fun setClickAddQueueEnabled(enabled: Boolean) = update {
        settingPreferences.updateClickAddQueueEnabled(enabled)
    }

    fun setPlaylistAddPosition(position: Int) = update {
        settingPreferences.updatePlaylistAddPosition(position)
    }

    fun setSmartPlaylistSelection(selectionIndex: Int, customLimit: Int = -1) = update {
//        musicPreferencesRepository.setSmartPlaylistSelection(selectionIndex, customLimit)
    }

    fun setNotificationBarEnabled(enabled: Boolean) = update {
        settingPreferences.updateNotificationBarEnabled(enabled)
    }

    fun setOldNotificationEnabled(enabled: Boolean) = update {
        settingPreferences.updateOldNotificationEnabled(enabled)
    }

    fun setColorNotificationEnabled(enabled: Boolean) = update {
        settingPreferences.updateColorNotificationEnabled(enabled)
    }

    fun setLockScreenEnabled(enabled: Boolean) = update {
        settingPreferences.updateLockScreenEnabled(enabled)
    }

    fun setLockBackgroundMode(mode: Int) = update {
        settingPreferences.updateLockBackgroundMode(mode)
    }

    fun setHeadsetInPlayEnabled(enabled: Boolean) = update {
        settingPreferences.updateHeadsetInPlayEnabled(enabled)
    }

    fun setHeadsetOutStopEnabled(enabled: Boolean) = update {
        settingPreferences.updateHeadsetOutStopEnabled(enabled)
    }

    fun setBluetoothAutoStartEnabled(enabled: Boolean) = update {
        settingPreferences.updateBluetoothAutoStartEnabled(enabled)
    }

    fun setBluetoothAutoStopEnabled(enabled: Boolean) = update {
        settingPreferences.updateBluetoothAutoStopEnabled(enabled)
    }

    fun setHeadsetControlAllowed(enabled: Boolean) = update {
        settingPreferences.updateHeadsetControlAllowed(enabled)
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
        }
    }

    private fun SettingPreferences.toUiState(
        useTenBand: Boolean = false,
        desktopLyricPreference: gd.app.musicplayer.data.local.preference.DesktopLyricPreference =
            gd.app.musicplayer.data.local.preference.DesktopLyricPreference(),
        statusBarLyricPreference: StatusBarLyricPreference = StatusBarLyricPreference()
    ): SettingsUiState {
        return SettingsUiState(
            useTenBand = useTenBand,
            useTenBandAvailable = SoundEffectPreferences.supportsTenBandEqualizer(),
            showHiddenFolders = normal.showHiddenFolders,
            darkModeEnabled = themeRepo.getCorePalette().isDarkMode(),
            showForwardBackward = normal.showForwardBackward,
            forwardBackwardSeconds = normal.forwardBackwardSeconds,
            queueForSearchingMode = normal.queueForSearchingMode,
            queueForSearchingLabel = queueForSearchingLabel(normal.queueForSearchingMode),
            bluetoothLyricEnabled = lyrics.bluetoothLyricEnabled,
            shakeEnabled = audio.shakeEnabled,
            shakeLevel = audio.shakeLevel,
            shakeLevelLabel = shakeLevelLabel(audio.shakeLevel),
            swipeChangeSongsEnabled = audio.swipeChangeSongsEnabled,
            simultaneousPlayEnabled = audio.simultaneousPlayEnabled,
            volumeFadeEnabled = audio.volumeFadeEnabled,
            gaplessPlaybackEnabled = audio.gaplessPlaybackEnabled,
            crossFadeEnabled = audio.crossFadeEnabled,
            fadeDurationSeconds = audio.fadeDurationSeconds,
            trackClickOperationEnabled = audio.trackClickOperationEnabled,
            replaySongEnabled = audio.replaySongEnabled,
            replayGainMode = replayGain.mode,
            replayGainModeLabel = replayGainModeLabel(replayGain.mode),
            replayGainPreampWithTag = replayGain.preampWithTag,
            replayGainPreampWithoutTag = replayGain.preampWithoutTag,
            replayGainPreampLabel =
                "${formatPreampDb(replayGain.preampWithTag)} / ${formatPreampDb(replayGain.preampWithoutTag)}",
            clickAddQueueEnabled = playlist.clickAddQueueEnabled,
            playlistAddPosition = playlist.addPosition,
            playlistAddPositionLabel = playlistAddPositionLabel(playlist.addPosition),
            smartPlaylistSelectionIndex = 0,
            smartPlaylistCustomLimit = -1,
            playlistTrackLimitLabel = "",
            notificationBarEnabled = notification.notificationBarEnabled,
            oldNotificationEnabled = notification.oldNotificationEnabled,
            colorNotificationEnabled =
                notification.colorNotificationEnabled && !notification.oldNotificationEnabled,
            colorNotificationEnabledAvailable = !notification.oldNotificationEnabled,
            desktopLyricPreference = desktopLyricPreference,
            statusBarLyricPreference = statusBarLyricPreference,
            lockScreenEnabled = lockscreen.lockScreenEnabled,
            lockBackgroundMode = lockscreen.backgroundMode,
            lockBackgroundLabel = lockBackgroundLabel(lockscreen.backgroundMode),
            headsetInPlayEnabled = headset.headsetInPlayEnabled,
            headsetOutStopEnabled = headset.headsetOutStopEnabled,
            bluetoothAutoStartEnabled = headset.bluetoothAutoStartEnabled,
            bluetoothAutoStopEnabled = headset.bluetoothAutoStopEnabled,
            headsetControlAllowed = headset.headsetControlAllowed
        )
    }

    private fun queueForSearchingLabel(mode: Int): String {
        return appContext.getString(
            if (mode == 1) R.string.queue_search_result else R.string.queue_all_songs
        )
    }

    private fun shakeLevelLabel(level: Float): String {
        return (((level.coerceIn(0f, 1f) * 15f) + 1f).toInt()).toString()
    }

    private fun replayGainModeLabel(mode: Int): String {
        return appContext.getString(
            when (mode.coerceIn(0, 2)) {
                1 -> R.string.replay_gain_track
                2 -> R.string.replay_gain_album
                else -> R.string.replay_gain_none
            }
        )
    }

    private fun playlistAddPositionLabel(position: Int): String {
        return appContext.getString(
            if (position == 0) R.string.add_music_position_top else R.string.add_music_position_end
        )
    }

    private fun lockBackgroundLabel(mode: Int): String {
        return appContext.getString(
            if (mode == 0) R.string.lock_screen_theme else R.string.lock_screen_artwork
        )
    }

    private fun formatPreampDb(value: Float): String {
        val rounded = round(value * 10f) / 10f
        val prefix = if (rounded > 0f) "+" else ""
        return "$prefix${rounded}dB"
    }
}
