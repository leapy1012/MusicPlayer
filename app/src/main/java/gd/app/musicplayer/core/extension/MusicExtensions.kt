package gd.app.musicplayer.core.extension

import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.image.CircleArtworkTransformation
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.ui.common.base.BaseActivity


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
