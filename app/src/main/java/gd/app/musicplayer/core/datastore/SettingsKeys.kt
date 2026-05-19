package gd.app.musicplayer.core.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object SettingsKeys {
    val PLAY_MODE = intPreferencesKey("preference_play_mode")
    val FORWARD_BACKWARD_SECONDS = intPreferencesKey("time_forward_backward")
    val SHOW_FORWARD_BACKWARD = booleanPreferencesKey("show_forward_backward")
    val SHOW_HIDDEN_FOLDERS = booleanPreferencesKey("show_hidden_folders")
    val FADE_DURATION_MS = intPreferencesKey("fade_duration")
    val SWIPE_CHANGE_SONGS = booleanPreferencesKey("swipe_change_songs")
    val SIMULTANEOUS_PLAY = booleanPreferencesKey("simultaneous_play")
    val VOLUME_FADE = booleanPreferencesKey("preference_volume_fade")
    val GAPLESS_PLAYBACK = booleanPreferencesKey("gapless_play")
    val CROSS_FADE = booleanPreferencesKey("fade_enable")
    val TRACK_CLICK_OPERATION = booleanPreferencesKey("preference_track_click_operation")
    val REPLAY_SONG = booleanPreferencesKey("preference_replay_song")
    val QUEUE_FOR_SEARCHING = intPreferencesKey("queue_for_searching")
    val SHAKE_CHANGE_MUSIC = booleanPreferencesKey("preference_shake_change_music")
    val SHAKE_LEVEL = floatPreferencesKey("shake_level")
    val BLUETOOTH_LYRIC = booleanPreferencesKey("bluetooth_lyric")
    val BLUETOOTH_AUTO_START = booleanPreferencesKey("preference_bluetooth_auto_start")
    val OLD_NOTIFICATION = booleanPreferencesKey("old_notification")
    val COLOR_NOTIFICATION = booleanPreferencesKey("color_notification")
    val NOTIFICATION_BAR_ENABLED = booleanPreferencesKey("use_notification_bar")
    val REPLAY_GAIN_MODE = intPreferencesKey("replay_gain_mode")
    val REPLAY_GAIN_PREAMP_WITH_TAG = floatPreferencesKey("preamp_with_tag")
    val REPLAY_GAIN_PREAMP_WITHOUT_TAG = floatPreferencesKey("preamp_without_tag")
    val LOCK_BACKGROUND = intPreferencesKey("lock_background")
    val LOCK_SCREEN = booleanPreferencesKey("preference_lock_screen")
    val PLAYLIST_ADD_POSITION = intPreferencesKey("preference_playlist_add_position")
    val CLICK_ADD_QUEUE = booleanPreferencesKey("preference_click_add_queue")
    val HEADSET_IN_PLAY = booleanPreferencesKey("preference_headset_in_play")
    val HEADSET_OUT_STOP = booleanPreferencesKey("preference_headset_out_stop")
    val BLUETOOTH_AUTO_STOP = booleanPreferencesKey("preference_bluetooth_auto_stop")
    val HEADSET_CONTROL_ALLOWED = booleanPreferencesKey("preference_headset_control_allow")
    val LIBRARY_TAB_CONFIG = stringPreferencesKey("preference_tab")
    val LIBRARY_LAST_TAB = intPreferencesKey("preference_tab_id")
    val HOME_PLAYLIST_DRAG_GUIDE_SHOWN =
        booleanPreferencesKey("home_playlist_drag_guide_shown")
    val PLAYLIST_DRAG_GUIDE_SHOWN =
        booleanPreferencesKey("playlist_drag_guide_shown")

    val KEY_BLUETOOTH_LYRIC_ENABLED =
        booleanPreferencesKey("bluetooth_lyric_enabled")
    val KEY_LYRIC_COLOR =
        intPreferencesKey("preference_lyric_color")
    val KEY_LYRIC_TEXT_SIZE =
        floatPreferencesKey("preference_lyric_text_size")
    val KEY_LYRIC_ALIGN =
        intPreferencesKey("lyric_align")
    val KEY_LYRIC_STYLE =
        intPreferencesKey("lyric_style")
    val KEY_LYRIC_AUTO_SCROLL =
        booleanPreferencesKey("lyric_auto_scroll")

    val KEY_DESKTOP_LYRIC_VISIBLE =
        booleanPreferencesKey("show_desktop_lyrics")

    val KEY_DESKTOP_LYRIC_LOCKED =
        booleanPreferencesKey("preference_desk_lrc_lock")

    val KEY_DESKTOP_LYRIC_PENDING_ENABLE_AFTER_PERMISSION =
        booleanPreferencesKey("desktop_lyric_pending_enable_after_permission")

    val KEY_DESKTOP_LYRIC_PRESET_COLOR_INDEX =
        intPreferencesKey("desk_lrc_preset_color_index")

    val KEY_DESKTOP_LYRIC_CURRENT_COLOR_PROGRESS =
        intPreferencesKey("desk_lrc_current_color_progress")

    val KEY_DESKTOP_LYRIC_NORMAL_COLOR_PROGRESS =
        intPreferencesKey("desk_lrc_normal_color_progress")

    val KEY_DESKTOP_LYRIC_ALPHA =
        floatPreferencesKey("desk_lrc_alpha")

    val KEY_DESKTOP_LYRIC_TEXT_SIZE =
        intPreferencesKey("desk_lrc_text_size")

    val KEY_DESKTOP_LYRIC_Y =
        intPreferencesKey("desk_lrc_y")
}
