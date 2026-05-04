package gd.app.musicplayer.core.extension

import java.util.Locale

fun Long.toDurationString(): String {
    if (this <= 0L) return "0:00"

    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

fun Long.isValidId(): Boolean {
    return this > 0L
}