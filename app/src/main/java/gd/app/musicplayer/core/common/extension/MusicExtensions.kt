package gd.app.musicplayer.core.common.extension

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.core.designsystem.image.AudioCover
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


const val URI_SCHEME_SEPARATOR = "://"
private const val QUEUE_MEDIA_ID_SEPARATOR = "#"

fun Music.isFavorite(): Boolean {
    return playlistId == 1L
}

fun Music.albumArtSource(): Any {
    // Prefer custom / cached artwork that is not a MediaStore albums/albumart URI.
    // Opening those URIs on some OEM MediaProviders throws
    // `SQLiteException: no such column: _data` from Glide's QMediaStoreUriLoader.
    albumPicture
        ?.takeIf { it.isNotBlank() && !it.isMediaStoreAlbumArtworkUri() }
        ?.let { return it }

    // Embedded cover from the audio file — reliable when MediaStore album art is broken.
    data?.takeIf { it.isNotBlank() }?.let { return AudioCover(it) }

    return ""
}

private fun String.isMediaStoreAlbumArtworkUri(): Boolean {
    return startsWith("content://media/external/audio/albums", ignoreCase = true) ||
        startsWith("content://media/external/audio/albumart", ignoreCase = true)
}


fun Music.resolveMediaUri(): Uri? {
    val source = data.orEmpty()

    if (source.isBlank()) return null

    return if (source.contains(URI_SCHEME_SEPARATOR)) {
        source.toUri()
    } else {
        Uri.fromFile(File(source))
    }
}

fun Music.toMediaItemOrNull(): MediaItem? {
    val mediaUri = resolveMediaUri() ?: return null

    return MediaItem.Builder()
        .setMediaId(toQueueMediaId())
        .setUri(mediaUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()
        )
        .build()
}

fun Music.toQueueMediaId(): String {
    return "$id$QUEUE_MEDIA_ID_SEPARATOR$queueToken"
}

fun String.parseTrackIdFromQueueMediaId(): Long? {
    return substringBefore(QUEUE_MEDIA_ID_SEPARATOR).toLongOrNull()
}

fun String.parseQueueTokenFromQueueMediaId(): Int? {
    return substringAfter(QUEUE_MEDIA_ID_SEPARATOR, "").toIntOrNull()
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
