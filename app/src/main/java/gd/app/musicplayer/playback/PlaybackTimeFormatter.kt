package gd.app.musicplayer.playback

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackTimeFormatter @Inject constructor() {
    fun format(timeMs: Int): String {
        val totalSeconds = (timeMs.coerceAtLeast(0) / 1000)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
