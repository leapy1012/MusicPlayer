package gd.app.musicplayer.util

import android.content.Context

class TrackLyricsStore private constructor(
    private val preferences: PreferenceUtil
) {

    fun getTrackLyricPath(trackId: Long): String? =
        preferences.getNullableStringPreference(lyricPathKey(trackId))

    fun setTrackLyricPath(trackId: Long, path: String?) {
        if (path.isNullOrBlank()) {
            preferences.removePreferences(lyricPathKey(trackId))
        } else {
            preferences.putStringPreference(lyricPathKey(trackId), path)
        }
    }

    fun getTrackLyricOffset(trackId: Long): Int =
        preferences.getIntPreference(lyricOffsetKey(trackId), 0)

    fun setTrackLyricOffset(trackId: Long, offsetMs: Int) {
        preferences.putIntPreference(lyricOffsetKey(trackId), offsetMs)
    }

    fun clearTrackLyricData(trackId: Long) {
        preferences.removePreferences(
            lyricPathKey(trackId),
            lyricOffsetKey(trackId)
        )
    }

    private fun lyricPathKey(trackId: Long): String = "track_lyric_path_$trackId"

    private fun lyricOffsetKey(trackId: Long): String = "track_lyric_offset_$trackId"

    companion object {
        fun from(context: Context): TrackLyricsStore =
            TrackLyricsStore(PreferenceUtil.getInstance(context.applicationContext))
    }
}
