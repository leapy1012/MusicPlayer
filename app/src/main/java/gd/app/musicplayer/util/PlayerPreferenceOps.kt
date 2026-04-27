package gd.app.musicplayer.util

interface PlayerPreferenceOps : PreferenceAccess {

    fun getLibraryTabConfigs(): List<LibraryTabConfig> =
        LibraryTabConfigStore.parse(getNullableStringPreference(KEY_LIBRARY_TAB_CONFIGS))

    fun setLibraryTabConfigs(items: List<LibraryTabConfig>) {
        putStringPreference(
            KEY_LIBRARY_TAB_CONFIGS,
            LibraryTabConfigStore.serialize(items)
        )
    }

    fun getLockScreenTimeFormat(): Int =
        getIntPreference(KEY_LOCK_TIME_FORMAT, 2)

    fun setLockScreenTimeFormat(format: Int) {
        putIntPreference(KEY_LOCK_TIME_FORMAT, format)
    }

    fun getLyricColor(): Int =
        getIntPreference(KEY_LYRIC_COLOR, -9371)

    fun setLyricColor(color: Int) {
        putIntPreference(KEY_LYRIC_COLOR, color)
    }

    fun getLyricTextSize(): Int =
        getIntPreference(KEY_LYRIC_TEXT_SIZE, 16)

    fun setLyricTextSize(sizeSp: Int) {
        putIntPreference(KEY_LYRIC_TEXT_SIZE, sizeSp)
    }

    fun isLyricAutoScrollEnabled(): Boolean =
        getBooleanPreference(KEY_LYRIC_AUTO_SCROLL, false)

    fun setLyricAutoScrollEnabled(enabled: Boolean) {
        putBooleanPreference(KEY_LYRIC_AUTO_SCROLL, enabled)
    }

    fun getLyricAlign(): Int =
        getIntPreference(KEY_LYRIC_ALIGN, 1)

    fun setLyricAlign(align: Int) {
        putIntPreference(KEY_LYRIC_ALIGN, align.coerceIn(0, 2))
    }

    fun getLyricStyle(): Int =
        getIntPreference(KEY_LYRIC_STYLE, 0)

    fun setLyricStyle(style: Int) {
        putIntPreference(KEY_LYRIC_STYLE, style.coerceIn(0, 3))
    }

    fun isVolumeFadeEnabled(): Boolean =
        getBooleanPreference(KEY_VOLUME_FADE, false)

    fun setVolumeFadeEnabled(enabled: Boolean) {
        putBooleanPreference(KEY_VOLUME_FADE, enabled)
    }

    fun getDesktopLyricsAlpha(): Float =
        getFloatPreference(KEY_DESKTOP_LYRICS_ALPHA, 1.0f)

    fun setDesktopLyricsAlpha(alpha: Float) {
        putFloatPreference(KEY_DESKTOP_LYRICS_ALPHA, alpha)
    }

    fun isDesktopLyricsLocked(): Boolean =
        getBooleanPreference(KEY_DESKTOP_LYRICS_LOCKED, false)

    fun setDesktopLyricsLocked(locked: Boolean) {
        putBooleanPreference(KEY_DESKTOP_LYRICS_LOCKED, locked)
    }

    fun getDesktopLyricsCurrentColorPosition(): Int =
        getIntPreference(KEY_DESKTOP_LYRICS_CURRENT_COLOR_POSITION, 0)

    fun setDesktopLyricsCurrentColorPosition(position: Int) {
        putIntPreference(KEY_DESKTOP_LYRICS_CURRENT_COLOR_POSITION, position)
    }

    fun getDesktopLyricsNormalColorPosition(): Int =
        getIntPreference(KEY_DESKTOP_LYRICS_NORMAL_COLOR_POSITION, 0)

    fun setDesktopLyricsNormalColorPosition(position: Int) {
        putIntPreference(KEY_DESKTOP_LYRICS_NORMAL_COLOR_POSITION, position)
    }

    fun getDesktopLyricsPosition(defaultPosition: Int): Int =
        getIntPreference(KEY_DESKTOP_LYRICS_POSITION, defaultPosition)

    fun setDesktopLyricsPosition(position: Int) {
        putIntPreference(KEY_DESKTOP_LYRICS_POSITION, position)
    }

    fun getDesktopLyricsPresetColorPosition(): Int =
        getIntPreference(KEY_DESKTOP_LYRICS_PRESET_COLOR_POSITION, 0)

    fun setDesktopLyricsPresetColorPosition(position: Int) {
        putIntPreference(KEY_DESKTOP_LYRICS_PRESET_COLOR_POSITION, position)
    }

    fun getDesktopLyricsSize(): Int =
        getIntPreference(KEY_DESKTOP_LYRICS_SIZE, 16)

    fun setDesktopLyricsSize(size: Int) {
        putIntPreference(KEY_DESKTOP_LYRICS_SIZE, size)
    }

    fun isDesktopLyricsVisible(): Boolean =
        getBooleanPreference(KEY_SHOW_DESKTOP_LYRICS, false)

    fun setDesktopLyricsVisible(visible: Boolean) {
        putBooleanPreference(KEY_SHOW_DESKTOP_LYRICS, visible)
    }

    fun getAfterTimerOperation(): Int =
        getIntPreference(KEY_AFTER_TIMER_OPERATION, 1)

    fun setAfterTimerOperation(operation: Int) {
        putIntPreference(KEY_AFTER_TIMER_OPERATION, operation)
    }

    fun getEqualizerLastTab(): Int =
        getIntPreference(KEY_EQUALIZER_LAST_TAB, 0)

    fun setEqualizerLastTab(tab: Int) {
        putIntPreference(KEY_EQUALIZER_LAST_TAB, tab)
    }

    fun getLibraryLastTab(): Int =
        getIntPreference(KEY_LIBRARY_LAST_TAB, 0)

    fun setLibraryLastTab(tab: Int) {
        putIntPreference(KEY_LIBRARY_LAST_TAB, tab)
    }

    fun isReplaySongEnabled(): Boolean =
        getBooleanPreference(KEY_REPLAY_SONG, false)

    fun setReplaySongEnabled(enabled: Boolean) {
        putBooleanPreference(KEY_REPLAY_SONG, enabled)
    }

    fun ignoreShortTracksUnder60Seconds(): Boolean =
        getBooleanPreference(KEY_IGNORE_60_SECONDS_MUSIC, false)

    fun setIgnoreShortTracksUnder60Seconds(enabled: Boolean) {
        putBooleanPreference(KEY_IGNORE_60_SECONDS_MUSIC, enabled)
    }

    fun shouldExcludeMusicBySize(): Boolean =
        getBooleanPreference(KEY_EXCLUDE_MUSIC_BY_SIZE_ENABLED, true)

    fun setExcludeMusicBySize(enabled: Boolean) {
        putBooleanPreference(KEY_EXCLUDE_MUSIC_BY_SIZE_ENABLED, enabled)
    }

    fun getExcludedMusicDurationMs(): Int =
        getIntPreference(KEY_EXCLUDE_MUSIC_DURATION, 60_000)

    fun setExcludedMusicDurationMs(durationMs: Int) {
        putIntPreference(KEY_EXCLUDE_MUSIC_DURATION, durationMs)
    }

    fun getExcludedMusicSizeBytes(): Int =
        getIntPreference(KEY_EXCLUDE_MUSIC_SIZE, 51_200)

    fun setExcludedMusicSizeBytes(sizeBytes: Int) {
        putIntPreference(KEY_EXCLUDE_MUSIC_SIZE, sizeBytes)
    }

    fun shouldIgnoreRingtones(): Boolean =
        getBooleanPreference(KEY_IGNORE_RINGTONE, false)

    fun setIgnoreRingtones(enabled: Boolean) {
        putBooleanPreference(KEY_IGNORE_RINGTONE, enabled)
    }

    fun getMaxPlaylistTime(): Long =
        getLongPreference(KEY_MAX_PLAYLIST_TIME, 0L)

    fun setMaxPlaylistTime(timeMs: Long) {
        putLongPreference(KEY_MAX_PLAYLIST_TIME, timeMs)
    }

    fun getMusicProgress(): IntArray {
        val raw = getNullableStringPreference(KEY_MUSIC_PROGRESS) ?: return intArrayOf(-1, 0)
        val parts = raw.split("&")
        if (parts.size != 2) return intArrayOf(-1, 0)

        return try {
            intArrayOf(parts[0].toInt(), parts[1].toInt())
        } catch (_: NumberFormatException) {
            intArrayOf(-1, 0)
        }
    }

    fun setMusicProgress(trackId: Int, progressMs: Int) {
        putStringPreference(KEY_MUSIC_PROGRESS, "$trackId&$progressMs")
    }

    fun getPlayMode(): Int =
        getIntPreference(KEY_PLAY_MODE, 1)

    fun setPlayMode(mode: Int) {
        putIntPreference(KEY_PLAY_MODE, mode)
    }

    fun getPlayPitch(): Float =
        getFloatPreference(KEY_PLAY_PITCH, 1.0f)

    fun setPlayPitch(pitch: Float) {
        putFloatPreference(KEY_PLAY_PITCH, pitch)
    }

    fun getPlaySpeed(): Float =
        getFloatPreference(KEY_PLAY_SPEED, 1.0f)

    fun setPlaySpeed(speed: Float) {
        putFloatPreference(KEY_PLAY_SPEED, speed)
    }

    fun getPlaylistAddPosition(): Int =
        getIntPreference(KEY_PLAYLIST_ADD_POSITION, 0)

    fun setPlaylistAddPosition(position: Int) {
        putIntPreference(KEY_PLAYLIST_ADD_POSITION, position)
    }

    fun getQueueForSearchingMode(): Int =
        getIntPreference(
            KEY_QUEUE_FOR_SEARCHING,
            if (getIntPreference(KEY_INSTALL_VERSION, 100) > 700) 1 else 0
        )

    fun getUnusedZeroValue(): Int = 0

    fun getShakeLevel(): Float =
        getFloatPreference(KEY_SHAKE_LEVEL, 0.5f)

    fun setShakeLevel(level: Float) {
        putFloatPreference(KEY_SHAKE_LEVEL, level)
    }

    fun isShakeToChangeTrackEnabled(): Boolean =
        getBooleanPreference(KEY_SHAKE_CHANGE_MUSIC, false)

    fun isShowShuffleButtonEnabled(): Boolean =
        getBooleanPreference(KEY_SHOW_SHUFFLE_BUTTON, false)

    fun setShowShuffleButtonEnabled(enabled: Boolean) {
        putBooleanPreference(KEY_SHOW_SHUFFLE_BUTTON, enabled)
    }

    fun isLockScreenEnabled(deviceSupportsNativeLockScreen: Boolean): Boolean =
        getBooleanPreference(KEY_LOCK_SCREEN, !deviceSupportsNativeLockScreen)

    fun setLockScreenEnabled(enabled: Boolean) {
        putBooleanPreference(KEY_LOCK_SCREEN, enabled)
    }

    fun isTimerAfterPlaybackEnabled(): Boolean =
        getBooleanPreference(KEY_TIMER_AFTER_PLAY, false)

    fun setTimerAfterPlaybackEnabled(enabled: Boolean) {
        putBooleanPreference(KEY_TIMER_AFTER_PLAY, enabled)
    }

    fun isClickAddQueueEnabled(): Boolean =
        getBooleanPreference(KEY_CLICK_ADD_QUEUE, false)

    fun getTrackClickOperationKey(): String =
        KEY_TRACK_CLICK_OPERATION

    fun isTrackClickOperationEnabled(): Boolean =
        getBooleanPreference(KEY_TRACK_CLICK_OPERATION, false)

    fun setTrackClickOperationEnabled(enabled: Boolean) {
        putBooleanPreference(KEY_TRACK_CLICK_OPERATION, enabled)
    }

    private companion object {
        const val KEY_LOCK_TIME_FORMAT = "preference_lock_time_format"
        const val KEY_LYRIC_COLOR = "preference_lyric_color"
        const val KEY_LYRIC_TEXT_SIZE = "preference_lyric_text_size"
        const val KEY_LYRIC_AUTO_SCROLL = "lyric_auto_scroll"
        const val KEY_LYRIC_ALIGN = "lyric_align"
        const val KEY_LYRIC_STYLE = "lyric_style"
        const val KEY_VOLUME_FADE = "preference_volume_fade"
        const val KEY_DESKTOP_LYRICS_ALPHA = "preference_desk_lrc_alpha"
        const val KEY_DESKTOP_LYRICS_CURRENT_COLOR_POSITION =
            "preference_desk_lrc_custom_current_color_position"
        const val KEY_DESKTOP_LYRICS_NORMAL_COLOR_POSITION =
            "preference_desk_lrc_custom_normal_color_position"
        const val KEY_DESKTOP_LYRICS_LOCKED = "preference_desk_lrc_lock"
        const val KEY_DESKTOP_LYRICS_POSITION = "desk_lrc_position"
        const val KEY_DESKTOP_LYRICS_PRESET_COLOR_POSITION =
            "preference_desk_lrc_preset_color_position"
        const val KEY_DESKTOP_LYRICS_SIZE = "preference_desk_lrc_size"
        const val KEY_SHOW_DESKTOP_LYRICS = "show_desktop_lyrics"
        const val KEY_AFTER_TIMER_OPERATION = "preference_after_timer_operation"
        const val KEY_EQUALIZER_LAST_TAB = "preference_eq_last_tab"
        const val KEY_LIBRARY_LAST_TAB = "preference_library_last_tab"
        const val KEY_LIBRARY_TAB_CONFIGS = "preference_library_tab_configs"
        const val KEY_REPLAY_SONG = "preference_replay_song"
        const val KEY_IGNORE_60_SECONDS_MUSIC = "pref_ignore_60seconds_music"
        const val KEY_EXCLUDE_MUSIC_BY_SIZE_ENABLED = "pref_exclude_music_by_size"
        const val KEY_EXCLUDE_MUSIC_DURATION = "pref_exclude_music_duration"
        const val KEY_EXCLUDE_MUSIC_SIZE = "pref_exclude_music_size"
        const val KEY_IGNORE_RINGTONE = "pref_ignore_rington"
        const val KEY_MAX_PLAYLIST_TIME = "preference_max_playlist_time"
        const val KEY_MUSIC_PROGRESS = "preference_music_progress"
        const val KEY_PLAY_MODE = "preference_play_mode"
        const val KEY_PLAY_PITCH = "preference_play_pitch"
        const val KEY_PLAY_SPEED = "preference_play_speed"
        const val KEY_PLAYLIST_ADD_POSITION = "preference_playlist_add_position"
        const val KEY_QUEUE_FOR_SEARCHING = "queue_for_searching"
        const val KEY_INSTALL_VERSION = "install_version"
        const val KEY_SHAKE_LEVEL = "preference_shake_level"
        const val KEY_SHAKE_CHANGE_MUSIC = "preference_shake_change_music"
        const val KEY_SHOW_SHUFFLE_BUTTON = "preference_show_shuffle_button"
        const val KEY_LOCK_SCREEN = "preference_lock_screen"
        const val KEY_TIMER_AFTER_PLAY = "preference_timer_after_play"
        const val KEY_CLICK_ADD_QUEUE = "preference_click_add_queue"
        const val KEY_TRACK_CLICK_OPERATION = "preference_track_click_operation"
    }
}
