package gd.app.musicplayer.core.common.extension

import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.widget.ImageView
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
/** Matches fragment_music_list_item album thumb (~52dp); original list loads have no full-screen override. */
private const val LIST_ARTWORK_DP = 52

fun ImageView.loadCircularArtwork(
    artworkSource: Any?,
    placeholderResId: Int,
) {
    loadArtwork(
        artworkSource = artworkSource,
        placeholderResId = placeholderResId,
        decodeFullQuality = false,
        requestOptions = {
            transform(CircleArtworkTransformation.INSTANCE)
        }
    )
}

/**
 * List / grid thumbs — decode near view size with RGB_565 (original list [h6.b.e]
 * does not override to min-screen ARGB8888).
 */
fun ImageView.loadMusicArtwork(
    artworkSource: Any?,
) {
    loadArtwork(
        artworkSource = artworkSource,
        placeholderResId = R.drawable.default_album_identify,
        decodeFullQuality = false,
        requestOptions = {
            centerCrop()
        }
    )
}

/** Player / lock / drive covers — keep high-quality full decode. */
fun ImageView.loadMusicArtworkLarge(
    artworkSource: Any?,
) {
    loadArtwork(
        artworkSource = artworkSource,
        placeholderResId = R.drawable.default_album_identify,
        decodeFullQuality = true,
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
    // Keep the dark player plate when artwork is missing — transparent error lets the
    // light window wash through and white chrome becomes invisible.
    val fallback = R.drawable.th_music_large

    Glide.with(this)
        .load(artworkSource)
        .override(targetWidth, targetHeight)
        .placeholder(fallback)
        .error(fallback)
        .transform(BlurTransformation(BLUR_RADIUS, BLUR_SAMPLING))
        .into(this)
}

private fun ImageView.loadArtwork(
    artworkSource: Any?,
    placeholderResId: Int,
    decodeFullQuality: Boolean,
    requestOptions: RequestBuilder<Drawable>.() -> RequestBuilder<Drawable>,
) {
    if (!context.canLoadImage()) return

    val request = Glide.with(this)
        .load(artworkSource)
        .placeholder(placeholderResId)
        .error(placeholderResId)

    if (decodeFullQuality) {
        val imageSize = context.getMinScreenSize()
        request
            .format(DecodeFormat.PREFER_ARGB_8888)
            .override(imageSize, imageSize)
            .requestOptions()
            .into(this)
    } else {
        val thumbPx = listArtworkSizePx()
        request
            .format(DecodeFormat.PREFER_RGB_565)
            .override(thumbPx, thumbPx)
            .requestOptions()
            .into(this)
    }
}

private fun ImageView.listArtworkSizePx(): Int {
    val fromView = maxOf(width, height)
    if (fromView > 0) return fromView

    val fromParams = maxOf(layoutParams?.width ?: 0, layoutParams?.height ?: 0)
    if (fromParams > 0) return fromParams

    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        LIST_ARTWORK_DP.toFloat(),
        resources.displayMetrics
    ).toInt()
}
