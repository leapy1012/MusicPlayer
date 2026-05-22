package gd.app.musicplayer.feature.setting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.R
import gd.app.musicplayer.core.datastore.DesktopLyricPreference
import gd.app.musicplayer.core.datastore.DesktopLyricPreferenceStore
import gd.app.musicplayer.core.datastore.PlaylistPreferenceDataStore
import gd.app.musicplayer.core.datastore.SettingPreferences
import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import gd.app.musicplayer.core.datastore.SoundEffectPreferences
import gd.app.musicplayer.core.datastore.StatusBarLyricPreference
import gd.app.musicplayer.core.datastore.StatusBarLyricPreferenceStore
import gd.app.musicplayer.domain.model.SmartPlaylistConfig
import gd.app.musicplayer.domain.repository.ThemeRepo
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.round

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val settingPreferences: SettingPreferencesDataStore,
    private val desktopLyricPreferenceStore: DesktopLyricPreferenceStore,
    private val statusBarLyricPreferenceStore: StatusBarLyricPreferenceStore,
    private val soundEffectPreferences: SoundEffectPreferences,
    private val playlistPreferenceDataStore: PlaylistPreferenceDataStore,
    private val themeRepo: ThemeRepo
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        combine(
            settingPreferences.observeSettingPreferences(),
            soundEffectPreferences.equalizerPreference,
            desktopLyricPreferenceStore.desktopLyricPreference,
            statusBarLyricPreferenceStore.preference,
            playlistPreferenceDataStore.observeSmartPlaylistConfig()
        ) { preferences, equalizer, desktopLyricPreference, statusBarLyricPreference, smartPlaylistConfig ->
            preferences.toUiState(
                useTenBand = equalizer.bandMode == SoundEffectPreferences.TEN_BAND_MODE,
                desktopLyricPreference = desktopLyricPreference,
                statusBarLyricPreference = statusBarLyricPreference,
                smartPlaylistConfig = smartPlaylistConfig
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

    fun setKeepAliveTipSeen() = update {
        settingPreferences.updateShowKeepAliveDot(false)
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
        playlistPreferenceDataStore.setSmartPlaylistSelection(selectionIndex, customLimit)
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

    private fun update(block: suspend () -> Unit): Job {
        return viewModelScope.launch {
            block()
        }
    }

    private fun supportsModernMediaStyleNotification(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N
    }

    private fun SettingPreferences.toUiState(
        useTenBand: Boolean = false,
        desktopLyricPreference: DesktopLyricPreference =
            DesktopLyricPreference(),
        statusBarLyricPreference: StatusBarLyricPreference = StatusBarLyricPreference(),
        smartPlaylistConfig: SmartPlaylistConfig = SmartPlaylistConfig(
            windowStartMs = 0L,
            windowDurationMs = PlaylistPreferenceDataStore.MONTH_MS_6,
            trackLimit = -1
        )
    ): SettingsUiState {
        return SettingsUiState(
            useTenBand = useTenBand,
            useTenBandAvailable = SoundEffectPreferences.supportsTenBandEqualizer(),
            showHiddenFolders = normal.showHiddenFolders,
            showKeepAliveDot = normal.showKeepAliveDot,
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
            smartPlaylistSelectionIndex = smartPlaylistSelectionIndex(smartPlaylistConfig),
            smartPlaylistCustomLimit = smartPlaylistConfig.trackLimit,
            playlistTrackLimitLabel = smartPlaylistLimitLabel(smartPlaylistConfig),
            notificationBarEnabled = notification.notificationBarEnabled,
            oldNotificationEnabled = notification.oldNotificationEnabled,
            colorNotificationEnabled = notification.colorNotificationEnabled,
            colorNotificationEnabledAvailable =
                !supportsModernMediaStyleNotification() || notification.oldNotificationEnabled,
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

    private fun smartPlaylistSelectionIndex(config: SmartPlaylistConfig): Int {
        return when (config.windowDurationMs) {
            PlaylistPreferenceDataStore.DAY_MS -> 0
            PlaylistPreferenceDataStore.WEEK_MS -> 1
            PlaylistPreferenceDataStore.MONTH_MS -> 2
            PlaylistPreferenceDataStore.MONTH_MS_3 -> 3
            PlaylistPreferenceDataStore.MONTH_MS_6 -> 4
            PlaylistPreferenceDataStore.YEAR_MS -> 5
            PlaylistPreferenceDataStore.FOREVER -> 6
            else -> 7
        }
    }

    private fun smartPlaylistLimitLabel(config: SmartPlaylistConfig): String {
        return when (smartPlaylistSelectionIndex(config)) {
            0 -> appContext.getString(R.string.playlist_limit_day)
            1 -> appContext.getString(R.string.playlist_limit_week)
            2 -> appContext.getString(R.string.playlist_limit_month)
            3 -> appContext.getString(R.string.playlist_limit_month_3)
            4 -> appContext.getString(R.string.playlist_limit_month_6)
            5 -> appContext.getString(R.string.playlist_limit_year)
            6 -> appContext.getString(R.string.playlist_limit_forever)
            else -> config.trackLimit
                .takeIf { it > 0 }
                ?.toString()
                ?: appContext.getString(R.string.playlist_track_limit_default)
        }
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
