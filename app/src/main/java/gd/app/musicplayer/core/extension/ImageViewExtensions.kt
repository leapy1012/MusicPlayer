package gd.app.musicplayer.core.extension

import android.app.Activity
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import gd.app.musicplayer.core.ui.image.CircleArtworkTransformation
import gd.app.musicplayer.data.model.Music

fun ImageView.loadCircularArtwork(
    artworkSource: Any,
    placeholderResId: Int,
) {
    val activity = context as? Activity
    if (activity?.isDestroyed == true || activity?.isFinishing == true) return

    val imageSize = context.getMinScreenSize()

    Glide.with(this)
        .load(artworkSource)
        .placeholder(placeholderResId)
        .error(placeholderResId)
        .format(DecodeFormat.PREFER_ARGB_8888)
        .override(imageSize, imageSize)
        .transform(CircleArtworkTransformation.INSTANCE)
        .into(this)
}