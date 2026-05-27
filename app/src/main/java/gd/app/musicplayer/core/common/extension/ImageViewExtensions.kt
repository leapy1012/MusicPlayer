package gd.app.musicplayer.core.common.extension

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.graphics.drawable.toDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.DecodeFormat
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.image.CircleArtworkTransformation
import jp.wasabeef.glide.transformations.BlurTransformation

private const val BLUR_RADIUS = 40
private const val BLUR_SAMPLING = 4
private const val BLUR_BACKGROUND_WIDTH_DIVISOR = 7
private const val BLUR_BACKGROUND_HEIGHT_DIVISOR = 10

fun ImageView.loadCircularArtwork(
    artworkSource: Any?,
    placeholderResId: Int,
) {
    loadArtwork(
        artworkSource = artworkSource,
        placeholderResId = placeholderResId,
        requestOptions = {
            transform(CircleArtworkTransformation.INSTANCE)
        }
    )
}

fun ImageView.loadMusicArtwork(
    artworkSource: Any?,
) {
    loadArtwork(
        artworkSource = artworkSource,
        placeholderResId = R.drawable.default_album_identify,
        requestOptions = {
            centerCrop()
        }
    )
}

fun ImageView.loadBlurredArtworkBackground(
    artworkSource: Any?,
) {
    val targetWidth = resources.displayMetrics.widthPixels / BLUR_BACKGROUND_WIDTH_DIVISOR
    val targetHeight = resources.displayMetrics.heightPixels / BLUR_BACKGROUND_HEIGHT_DIVISOR

    Glide.with(this)
        .load(artworkSource)
        .override(targetWidth, targetHeight)
        .placeholder(drawable)
        .error(Color.TRANSPARENT.toDrawable())
        .transform(BlurTransformation(BLUR_RADIUS, BLUR_SAMPLING))
        .into(this)
}

private fun ImageView.loadArtwork(
    artworkSource: Any?,
    placeholderResId: Int,
    requestOptions: RequestBuilder<Drawable>.() -> RequestBuilder<Drawable>,
) {
    if (!context.canLoadImage()) return

    val imageSize = context.getMinScreenSize()

    Glide.with(this)
        .load(artworkSource)
        .placeholder(placeholderResId)
        .error(placeholderResId)
        .format(DecodeFormat.PREFER_ARGB_8888)
        .override(imageSize, imageSize)
        .requestOptions()
        .into(this)
}