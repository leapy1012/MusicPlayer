package gd.app.musicplayer.core.common.extension

import android.content.Context
import android.text.format.Formatter
import gd.app.musicplayer.domain.model.Music
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds


fun Music.isFavorite(): Boolean {
    return playlistId == 1L
}

fun Music.albumArtSource(): Any {
    val artworkSource =
        albumPicture?.takeIf { it.isNotBlank() } ?: albumId.takeIf { it.isNotBlank() }?.let {
            "content://media/external/audio/albumart/$it"
        } ?: data.orEmpty()
    return artworkSource
}

fun Music.formatAddedDate(): String {
    if (date == null || date <= 0L) return ""
    val epochMillis = if (date < 1_000_000_000_000L) date * 1000L else date
    return SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(epochMillis))
}

fun Music.formatFileSize(context: Context): String {
    if (size == null || size <= 0L) return ""
    return Formatter.formatShortFileSize(context, size)
}

fun Music.formatDuration(): String {

    if (duration <= 0) return ""

    val duration = duration.milliseconds
    val totalSeconds = duration.inWholeSeconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.getDefault(), hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.getDefault(), minutes, seconds)
    }
}
