package gd.app.musicplayer.playback.service


import android.app.Service

internal const val START_STICKY: Int = Service.START_STICKY
internal const val START_NOT_STICKY: Int = Service.START_NOT_STICKY

internal const val NO_INDEX = -1
internal const val NO_TRACK_ID = Long.MIN_VALUE
internal const val NO_PLAYER_COMMAND = -1

internal const val PROGRESS_TICK_MS = 500L
internal const val SESSION_AUTO_SAVE_INTERVAL_MS = 5_000L
internal const val MEDIA3_SESSION_ACTIVITY_REQUEST_CODE = 2
internal const val MEDIA3_COMMAND_TOGGLE_FAVORITE =
    "gd.app.musicplayer.media3.TOGGLE_FAVORITE"
internal const val MEDIA3_COMMAND_CYCLE_PLAYBACK_MODE =
    "gd.app.musicplayer.media3.CYCLE_PLAYBACK_MODE"
internal const val MEDIA3_COMMAND_STOP_AFTER_CURRENT =
    "gd.app.musicplayer.media3.STOP_AFTER_CURRENT"
internal const val MEDIA3_COMMAND_CLOSE_NOTIFICATION =
    "gd.app.musicplayer.media3.CLOSE_NOTIFICATION"
internal const val PREVIOUS_RESTART_WINDOW_MS = 5_000L
internal const val PLAY_PAUSE_FADE_DURATION_MS = 1_000L

