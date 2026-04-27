package gd.app.musicplayer.data.model

import androidx.room.ColumnInfo
import android.os.Parcelable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.extension.getMinScreenSize
import gd.app.musicplayer.core.ui.image.CircleArtworkTransformation
import gd.app.musicplayer.ui.common.base.BaseActivity
import kotlinx.parcelize.Parcelize

@Parcelize
data class Music(
    val _id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val album_id: String,
    val p_id: Long,
    @ColumnInfo(name = "album_pic")
    val albumPicture: String? = null,
    val data: String? = null,
    val size: Long? = null,
    val duration: Int = 0,
    val folder_path: String? = null,
    val date: Long? = null,
    @ColumnInfo(name = "play_time")
    val playTime: Long? = null,
    @ColumnInfo(name = "count")
    val playCount: Int = 0,
    val year: Int? = null
) : Parcelable

fun Music.isFavorite(): Boolean {
    return p_id == 1L
}

fun Music.albumArtSource(): Any {
    val artworkSource =
        albumPicture?.takeIf { it.isNotBlank() } ?: album_id.takeIf { it.isNotBlank() }?.let {
            "content://media/external/audio/albumart/$it"
        } ?: data.orEmpty()
    return artworkSource
}

fun Music.loadMusicArtwork(imageView: ImageView, placeholderResId: Int) {
    val context = imageView.context
    if (context is BaseActivity && context.isDestroyed) return

    val imageSize = context.getMinScreenSize()

    val artworkSource = albumArtSource()
    android.util.Log.e("Leapy", artworkSource.toString())

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
