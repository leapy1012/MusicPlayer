package gd.app.musicplayer.data.local.preference

import androidx.datastore.preferences.core.Preferences
import gd.app.musicplayer.core.datastore.MusicDataStore
import gd.app.musicplayer.core.datastore.SettingsKeys
import gd.app.musicplayer.playback.PlaybackMode
import gd.app.musicplayer.ui.library.model.LibraryTabConfig
import gd.app.musicplayer.ui.library.model.LibraryTabConfigStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

@Singleton
class SettingPreferencesDataStoreImpl @Inject constructor(
    private val dataStore: MusicDataStore
) : SettingPreferencesDataStore {


    override val playbackVolumeFadeEnabled: Flow<Boolean> =
        dataStore.data
            .map { preferences ->
                preferences[SettingsKeys.VOLUME_FADE] ?: false
            }
            .distinctUntilChanged()

    override val replayGainPreference: Flow<ReplayGainSettingPreference> =
        dataStore.data
            .map { preferences ->
                ReplayGainSettingPreference(
                    mode = normalizeReplayGainMode(
                        preferences[SettingsKeys.REPLAY_GAIN_MODE]
                    ),
                    preampWithTag = normalizeReplayGainPreampDb(
                        preferences[SettingsKeys.REPLAY_GAIN_PREAMP_WITH_TAG]
                    ),
                    preampWithoutTag = normalizeReplayGainPreampDb(
                        preferences[SettingsKeys.REPLAY_GAIN_PREAMP_WITHOUT_TAG]
                    )
                )
            }
            .distinctUntilChanged()

    override val libraryTabConfig: Flow<List<LibraryTabConfig>> =
        dataStore.data
            .map { preferences ->
                LibraryTabConfigStore.parse(
                    preferences[SettingsKeys.LIBRARY_TAB_CONFIG]
                )
            }
            .distinctUntilChanged()

    override suspend fun getForwardBackwardSeconds(): Int {
        return dataStore.data.first()[SettingsKeys.FORWARD_BACKWARD_SECONDS] ?: 15
    }

    override suspend fun getLibraryTabConfig(): List<LibraryTabConfig> {
        return LibraryTabConfigStore.parse(
            dataStore.data.first()[SettingsKeys.LIBRARY_TAB_CONFIG]
        )
    }

    override suspend fun getLibraryLastTab(): Int {
        return dataStore.data.first()[SettingsKeys.LIBRARY_LAST_TAB] ?: 0
    }

    override suspend fun getLockScreenEnabled(): Boolean {
        return dataStore.data.first()[SettingsKeys.LOCK_SCREEN] ?: true
    }

    override suspend fun getSimultaneousPlayEnabled(): Boolean {
        return dataStore.data.first()[SettingsKeys.SIMULTANEOUS_PLAY] ?: false
    }

    override suspend fun setLibraryLastTab(tabId: Int) {
        dataStore.set(SettingsKeys.LIBRARY_LAST_TAB, tabId)
    }

    override suspend fun setLibraryTabConfig(items: List<LibraryTabConfig>) {
        dataStore.set(
            SettingsKeys.LIBRARY_TAB_CONFIG,
            LibraryTabConfigStore.serialize(items)
        )
    }


    override fun observeSettingPreferences(): Flow<SettingPreferences> {
        return dataStore.data.map(::toSettingPreferences)
    }

    override fun observePlayMode(): Flow<Int> {
        return dataStore.observe(SettingsKeys.PLAY_MODE, PlaybackMode.ORDER)
    }

    override suspend fun getPlayMode(): Int {
        return dataStore.data.first()[SettingsKeys.PLAY_MODE] ?: PlaybackMode.ORDER
    }

    override suspend fun cyclePlayMode(): Int {
        val currentMode = observePlayMode().first()

        val nextMode = when (currentMode) {
            PlaybackMode.SINGLE -> PlaybackMode.ORDER
            PlaybackMode.ORDER -> PlaybackMode.LOOP_ALL
            PlaybackMode.LOOP_ALL -> PlaybackMode.SHUFFLE_ALL
            else -> PlaybackMode.SINGLE
        }

        updatePlayMode(nextMode)

        return nextMode
    }

    override suspend fun getShowHiddenFolders(): Boolean {
        return dataStore.observe(SettingsKeys.SHOW_HIDDEN_FOLDERS, true).first()
    }

    override fun observeShowHiddenFolders(): Flow<Boolean> {
        return dataStore.observe(SettingsKeys.SHOW_HIDDEN_FOLDERS, true)
    }

    override suspend fun getShakeLevel(): Float {
        return dataStore.observe(SettingsKeys.SHAKE_LEVEL, 0.5f).first()
    }

    override suspend fun getShakeEnabled(): Boolean {
        return dataStore.observe(SettingsKeys.SHAKE_CHANGE_MUSIC, false).first()
    }

    override suspend fun getTrackClickOperationEnabled(): Boolean {
        return dataStore.observe(SettingsKeys.TRACK_CLICK_OPERATION, false).first()
    }

    override suspend fun getReplaySongEnabled(): Boolean {
        return dataStore.observe(SettingsKeys.REPLAY_SONG, false).first()
    }

    override suspend fun updatePlayMode(mode: Int) {
        dataStore.set(SettingsKeys.PLAY_MODE, mode)
    }

    override suspend fun updateForwardBackwardSeconds(seconds: Int) {
        dataStore.set(SettingsKeys.FORWARD_BACKWARD_SECONDS, seconds)
    }

    override suspend fun updateShowForwardBackward(enabled: Boolean) {
        dataStore.set(SettingsKeys.SHOW_FORWARD_BACKWARD, enabled)
    }

    override suspend fun updateShowHiddenFolders(enabled: Boolean) {
        dataStore.set(SettingsKeys.SHOW_HIDDEN_FOLDERS, enabled)
    }

    override suspend fun updateFadeDurationSeconds(seconds: Int) {
        val durationMs = seconds.coerceIn(1, 12) * 1000
        dataStore.set(SettingsKeys.FADE_DURATION_MS, durationMs)
    }

    override suspend fun updateSwipeChangeSongsEnabled(enabled: Boolean) {
        dataStore.set(SettingsKeys.SWIPE_CHANGE_SONGS, enabled)
    }

    override suspend fun updateSimultaneousPlayEnabled(enabled: Boolean) {
        dataStore.set(SettingsKeys.SIMULTANEOUS_PLAY, enabled)
    }

    override suspend fun updateVolumeFadeEnabled(enabled: Boolean) {
        dataStore.set(SettingsKeys.VOLUME_FADE, enabled)
    }

    override suspend fun updateGaplessPlaybackEnabled(enabled: Boolean) {
        dataStore.set(SettingsKeys.GAPLESS_PLAYBACK, enabled)

        if (enabled) {
            dataStore.set(SettingsKeys.CROSS_FADE, false)
        }
    }

    override suspend fun updateCrossFadeEnabled(enabled: Boolean) {
        dataStore.set(SettingsKeys.CROSS_FADE, enabled)

        if (enabled) {
            dataStore.set(SettingsKeys.GAPLESS_PLAYBACK, false)
        }
    }

    override suspend fun updateTrackClickOperationEnabled(enabled: Boolean) {
        dataStore.set(SettingsKeys.TRACK_CLICK_OPERATION, enabled)
    }

    override suspend fun updateReplaySongEnabled(enabled: Boolean) {
        dataStore.set(SettingsKeys.REPLAY_SONG, enabled)
    }

    override suspend fun updateQueueForSearchingMode(mode: Int) {
        dataStore.set(SettingsKeys.QUEUE_FOR_SEARCHING, mode)
    }

    override suspend fun updateReplayGainMode(mode: Int) {
        val value = mode.coerceIn(0, 2)
        dataStore.set(SettingsKeys.REPLAY_GAIN_MODE, value)
    }

    override suspend fun updateReplayGainPreamp(withTag: Float, withoutTag: Float) {
        dataStore.set(
            SettingsKeys.REPLAY_GAIN_PREAMP_WITH_TAG,
            normalizeReplayGainPreampDb(withTag)
        )
        dataStore.set(
            SettingsKeys.REPLAY_GAIN_PREAMP_WITHOUT_TAG,
            normalizeReplayGainPreampDb(withoutTag)
        )
    }

    override suspend fun updateLockBackgroundMode(mode: Int) {
        val value = mode.coerceIn(0, 1)
        dataStore.set(SettingsKeys.LOCK_BACKGROUND, value)
    }

    override suspend fun updateLockScreenEnabled(enabled: Boolean) {
        dataStore.set(SettingsKeys.LOCK_SCREEN, enabled)
    }

    override suspend fun updateBluetoothLyricEnabled(enabled: Boolean) {
        dataStore.set(SettingsKeys.BLUETOOTH_LYRIC, enabled)
    }

    override suspend fun updateBluetoothAutoStartEnabled(enabled: Boolean) {
        dataStore.set(SettingsKeys.BLUETOOTH_AUTO_START, enabled)
    }

    override suspend fun updateBluetoothAutoStopEnabled(enabled: Boolean) {
        dataStore.set(SettingsKeys.BLUETOOTH_AUTO_STOP, enabled)
    }

    override suspend fun updateHeadsetInPlayEnabled(enabled: Boolean) {
        dataStore.set(SettingsKeys.HEADSET_IN_PLAY, enabled)
    }

    override suspend fun updateHeadsetOutStopEnabled(enabled: Boolean) {
        dataStore.set(SettingsKeys.HEADSET_OUT_STOP, enabled)
    }

    override suspend fun updateHeadsetControlAllowed(enabled: Boolean) {
        dataStore.set(SettingsKeys.HEADSET_CONTROL_ALLOWED, enabled)
    }

    override suspend fun updateOldNotificationEnabled(enabled: Boolean) {
        dataStore.set(SettingsKeys.OLD_NOTIFICATION, enabled)
    }

    override suspend fun updateColorNotificationEnabled(enabled: Boolean) {
        dataStore.set(SettingsKeys.COLOR_NOTIFICATION, enabled)
    }

    override suspend fun updateNotificationBarEnabled(enabled: Boolean) {
        dataStore.set(SettingsKeys.NOTIFICATION_BAR_ENABLED, enabled)
    }

    override suspend fun updateShakeEnabled(enabled: Boolean) {
        dataStore.set(SettingsKeys.SHAKE_CHANGE_MUSIC, enabled)
    }

    override suspend fun updateShakeLevel(level: Float) {
        dataStore.set(SettingsKeys.SHAKE_LEVEL, level)
    }

    override suspend fun updatePlaylistAddPosition(position: Int) {
        val value = position.coerceIn(0, 1)
        dataStore.set(SettingsKeys.PLAYLIST_ADD_POSITION, value)
    }

    override suspend fun updateClickAddQueueEnabled(enabled: Boolean) {
        dataStore.set(SettingsKeys.CLICK_ADD_QUEUE, enabled)
    }

    private fun toSettingPreferences(preferences: Preferences): SettingPreferences {
        return SettingPreferences(
            playMode = preferences[SettingsKeys.PLAY_MODE] ?: 1,
            normal = NormalSettingPreference(
                forwardBackwardSeconds = preferences[SettingsKeys.FORWARD_BACKWARD_SECONDS] ?: 15,
                showForwardBackward = preferences[SettingsKeys.SHOW_FORWARD_BACKWARD] ?: false,
                queueForSearchingMode = preferences[SettingsKeys.QUEUE_FOR_SEARCHING] ?: 0,
                showHiddenFolders = preferences[SettingsKeys.SHOW_HIDDEN_FOLDERS] ?: true,
                libraryTabConfig = LibraryTabConfigStore.parse(preferences[SettingsKeys.LIBRARY_TAB_CONFIG])
            ),
            lyrics = LyricsSettingPreference(
                bluetoothLyricEnabled = preferences[SettingsKeys.BLUETOOTH_LYRIC] ?: true
            ),
            audio = AudioSettingPreference(
                fadeDurationSeconds = normalizeFadeDurationSeconds(
                    preferences[SettingsKeys.FADE_DURATION_MS]
                ),
                shakeEnabled = preferences[SettingsKeys.SHAKE_CHANGE_MUSIC] ?: false,
                shakeLevel = preferences[SettingsKeys.SHAKE_LEVEL] ?: 0.5f,
                swipeChangeSongsEnabled = preferences[SettingsKeys.SWIPE_CHANGE_SONGS] ?: true,
                simultaneousPlayEnabled = preferences[SettingsKeys.SIMULTANEOUS_PLAY] ?: false,
                volumeFadeEnabled = preferences[SettingsKeys.VOLUME_FADE] ?: false,
                gaplessPlaybackEnabled = preferences[SettingsKeys.GAPLESS_PLAYBACK] ?: false,
                crossFadeEnabled = preferences[SettingsKeys.CROSS_FADE] ?: false,
                trackClickOperationEnabled = preferences[SettingsKeys.TRACK_CLICK_OPERATION]
                    ?: false,
                replaySongEnabled = preferences[SettingsKeys.REPLAY_SONG] ?: false
            ),
            replayGain = ReplayGainSettingPreference(
                mode = normalizeReplayGainMode(preferences[SettingsKeys.REPLAY_GAIN_MODE]),
                preampWithTag = normalizeReplayGainPreampDb(
                    preferences[SettingsKeys.REPLAY_GAIN_PREAMP_WITH_TAG]
                ),
                preampWithoutTag = normalizeReplayGainPreampDb(
                    preferences[SettingsKeys.REPLAY_GAIN_PREAMP_WITHOUT_TAG]
                )
            ),
            playlist = PlaylistSettingPreference(
                addPosition = preferences[SettingsKeys.PLAYLIST_ADD_POSITION] ?: 0,
                clickAddQueueEnabled = preferences[SettingsKeys.CLICK_ADD_QUEUE] ?: false
            ),
            notification = NotificationSettingPreference(
                notificationBarEnabled = preferences[SettingsKeys.NOTIFICATION_BAR_ENABLED]
                    ?: false,
                oldNotificationEnabled = preferences[SettingsKeys.OLD_NOTIFICATION] ?: false,
                colorNotificationEnabled = preferences[SettingsKeys.COLOR_NOTIFICATION] ?: true
            ),
            lockscreen = LockscreenSettingPreference(
                backgroundMode = preferences[SettingsKeys.LOCK_BACKGROUND] ?: 1,
                lockScreenEnabled = preferences[SettingsKeys.LOCK_SCREEN] ?: true
            ),
            headset = HeadsetSettingPreference(
                bluetoothAutoStartEnabled = preferences[SettingsKeys.BLUETOOTH_AUTO_START]
                    ?: false,
                headsetInPlayEnabled = preferences[SettingsKeys.HEADSET_IN_PLAY] ?: false,
                headsetOutStopEnabled = preferences[SettingsKeys.HEADSET_OUT_STOP] ?: true,
                bluetoothAutoStopEnabled = preferences[SettingsKeys.BLUETOOTH_AUTO_STOP] ?: true,
                headsetControlAllowed = preferences[SettingsKeys.HEADSET_CONTROL_ALLOWED] ?: true
            )
        )
    }

    private fun normalizeFadeDurationSeconds(durationMs: Int?): Int {
        return ((durationMs ?: DEFAULT_FADE_DURATION_MS) / 1000)
            .coerceIn(MIN_FADE_DURATION_SECONDS, MAX_FADE_DURATION_SECONDS)
    }

    private fun normalizeReplayGainMode(mode: Int?): Int {
        return (mode ?: DEFAULT_REPLAY_GAIN_MODE).coerceIn(
            MIN_REPLAY_GAIN_MODE,
            MAX_REPLAY_GAIN_MODE
        )
    }

    private fun normalizeReplayGainPreampDb(value: Float?): Float {
        val clamped = (value ?: DEFAULT_REPLAY_GAIN_PREAMP_DB).coerceIn(
            MIN_REPLAY_GAIN_PREAMP_DB,
            MAX_REPLAY_GAIN_PREAMP_DB
        )

        return (clamped * PREAMP_ROUNDING_SCALE).roundToInt() / PREAMP_ROUNDING_SCALE
    }

    private companion object {
        private const val DEFAULT_FADE_DURATION_MS = 6_000
        private const val MIN_FADE_DURATION_SECONDS = 1
        private const val MAX_FADE_DURATION_SECONDS = 12

        private const val DEFAULT_REPLAY_GAIN_MODE = 0
        private const val MIN_REPLAY_GAIN_MODE = 0
        private const val MAX_REPLAY_GAIN_MODE = 2

        private const val DEFAULT_REPLAY_GAIN_PREAMP_DB = 0f
        private const val MIN_REPLAY_GAIN_PREAMP_DB = -15f
        private const val MAX_REPLAY_GAIN_PREAMP_DB = 15f
        private const val PREAMP_ROUNDING_SCALE = 10f
    }
}

