package gd.app.musicplayer.util

import android.content.Context
import gd.app.musicplayer.R

interface SmartPlaylistPreferenceOps : PreferenceAccess {

    data class SmartPlaylistConfig(
        val windowDurationMs: Long,
        val windowStartMs: Long,
        val playlistLimit: Int
    )

    fun getSmartPlaylistSelectionIndex(): Int {
        return when (getSmartPlaylistWindowDurationMs()) {
            DAY_MS -> 0
            WEEK_MS -> 1
            MONTH_MS -> 2
            MONTH_3_MS -> 3
            MONTH_6_MS -> 4
            YEAR_MS -> 5
            FOREVER -> 6
            else -> 7
        }
    }

    fun getSmartPlaylistTrackLimitRaw(): Int =
        getIntPreference(KEY_PLAYLIST_TRACK_LIMIT, -1)

    fun getSmartPlaylistWindowDurationMs(): Long {
        return if (
            containsPreference(KEY_PLAYLIST_TRACK_LIMIT_TIME) ||
            !containsPreference(KEY_PLAYLIST_TRACK_LIMIT)
        ) {
            getLongPreference(KEY_PLAYLIST_TRACK_LIMIT_TIME, MONTH_6_MS)
        } else {
            0L
        }
    }

    fun getSmartPlaylistTrackLimit(): Int =
        if (getSmartPlaylistWindowDurationMs() == 0L) getSmartPlaylistTrackLimitRaw() else -1

    fun getSmartPlaylistConfig(nowMs: Long = System.currentTimeMillis()): SmartPlaylistConfig {
        val windowDurationMs = getSmartPlaylistWindowDurationMs()
        val windowStartMs = if (windowDurationMs > 0L) nowMs - windowDurationMs else 0L
        val playlistLimit = if (windowDurationMs == 0L) getSmartPlaylistTrackLimitRaw() else -1
        return SmartPlaylistConfig(
            windowDurationMs = windowDurationMs,
            windowStartMs = windowStartMs,
            playlistLimit = playlistLimit
        )
    }

    fun getSmartPlaylistSummary(context: Context): String {
        return when (val windowDurationMs = getSmartPlaylistWindowDurationMs()) {
            DAY_MS -> context.getString(R.string.playlist_limit_day)
            WEEK_MS -> context.getString(R.string.playlist_limit_week)
            MONTH_MS -> context.getString(R.string.playlist_limit_month)
            MONTH_3_MS -> context.getString(R.string.playlist_limit_month_3)
            MONTH_6_MS -> context.getString(R.string.playlist_limit_month_6)
            YEAR_MS -> context.getString(R.string.playlist_limit_year)
            FOREVER -> context.getString(R.string.playlist_limit_forever)
            else -> {
                val limit = if (windowDurationMs == 0L) getSmartPlaylistTrackLimitRaw() else -1
                if (limit > 0) limit.toString() else context.getString(R.string.playlist_track_limit_default)
            }
        }
    }

    fun setSmartPlaylistSelection(selectionIndex: Int, customLimit: Int = -1) {
        val windowDurationMs = when (selectionIndex) {
            0 -> DAY_MS
            1 -> WEEK_MS
            2 -> MONTH_MS
            3 -> MONTH_3_MS
            4 -> MONTH_6_MS
            5 -> YEAR_MS
            6 -> FOREVER
            else -> 0L
        }
        putLongPreference(KEY_PLAYLIST_TRACK_LIMIT_TIME, windowDurationMs)
        if (windowDurationMs == 0L) {
            putIntPreference(KEY_PLAYLIST_TRACK_LIMIT, customLimit)
        }
    }

    companion object {
        const val KEY_PLAYLIST_TRACK_LIMIT = "preference_playlist_track_limit"
        const val KEY_PLAYLIST_TRACK_LIMIT_TIME = "playlist_track_limit_time"

        const val DAY_MS = 86_400_000L
        const val WEEK_MS = 604_800_000L
        const val MONTH_MS = 2_592_000_000L
        const val MONTH_3_MS = 7_776_000_000L
        const val MONTH_6_MS = 15_552_000_000L
        const val YEAR_MS = 31_104_000_000L
        const val FOREVER = -1L
    }
}
