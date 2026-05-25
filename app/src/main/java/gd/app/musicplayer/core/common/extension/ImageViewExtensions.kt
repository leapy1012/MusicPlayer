package gd.app.musicplayer.core.common.extension

import android.app.Activity
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.image.CircleArtworkTransformation
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.ui.common.base.BaseActivity

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

fun ImageView.loadMusicArtwork(
    artworkSource: Any?
) {
    val activity = context as? Activity
    if (activity?.isDestroyed == true || activity?.isFinishing == true) return

    val imageSize = context.getMinScreenSize()

    Glide.with(context)
        .load(artworkSource)
        .placeholder(R.drawable.default_album_identify)
        .format(DecodeFormat.PREFER_ARGB_8888)
        .error(R.drawable.default_album_identify)
        .centerCrop()
        .override(imageSize, imageSize)
        .into(this)
}