package gd.app.musicplayer.core.extension

import android.content.Context
import android.text.format.Formatter
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.image.CircleArtworkTransformation
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.ui.common.base.BaseActivity
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

fun Music.loadMusicArtwork(imageView: ImageView, placeholderResId: Int) {
    val context = imageView.context
    if (context is BaseActivity && context.isDestroyed) return

    val imageSize = context.getMinScreenSize()

    val artworkSource = albumArtSource()

    Glide.with(context)
        .load(artworkSource)
        .placeholder(placeholderResId)
        .format(DecodeFormat.PREFER_ARGB_8888)
        .error(placeholderResId)
        .override(imageSize, imageSize)
        .transform(CircleArtworkTransformation.INSTANCE)
        .into(imageView)
}

fun Music.loadMusicArtwork(imageView: ImageView) {
    val context = imageView.context
    if (context is BaseActivity && context.isDestroyed) return

    val imageSize = context.getMinScreenSize()

    val artworkSource = albumArtSource()

    Glide.with(context)
        .load(artworkSource)
        .placeholder(R.drawable.default_album_identify)
        .format(DecodeFormat.PREFER_ARGB_8888)
        .error(R.drawable.default_album_identify)
        .centerCrop()
        .override(imageSize, imageSize)
        .into(imageView)
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
