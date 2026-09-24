package gd.app.musicplayer.core.common.extension

/**
 * Formats a duration in milliseconds for list/player UI.
 * Matches Music Player 8.1.5 [y6.m0.B] (zero-padded minutes/seconds).
 */
fun Long.toDurationString(): String {
    if (this <= 1L) return "00:00"
    if (this < 1_000L) return "00:01"

    val totalSeconds = this / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds / 60L) % 60L
    val seconds = totalSeconds % 60L

    val builder = StringBuilder()
    if (hours > 0L) {
        builder.append(hours).append(':')
    }
    if (minutes < 10L) {
        builder.append('0')
    }
    builder.append(minutes).append(':')
    if (seconds < 10L) {
        builder.append('0')
    }
    builder.append(seconds)
    return builder.toString()
}

fun Long.isValidId(): Boolean {
    return this > 0L
}
