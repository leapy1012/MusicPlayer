package gd.app.musicplayer.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import gd.app.musicplayer.core.extension.albumArtSource
import gd.app.musicplayer.data.model.Music

class ArtworkLoader(
    private val context: Context,
    private val defaultArtwork: Bitmap,
) {
    private var target: CustomTarget<Bitmap>? = null

    fun clear() {
        target?.let { Glide.with(context).clear(it) }
        target = null
    }

    fun load(
        music: Music,
        onLoaded: (Bitmap) -> Unit,
        onFailed: (Bitmap) -> Unit,
        onCleared: () -> Unit,
    ) {
        clear()

        val newTarget = object : CustomTarget<Bitmap>(ART_SIZE_PX, ART_SIZE_PX) {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                onLoaded(resource)
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                onFailed(defaultArtwork)
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                onCleared()
            }
        }

        target = newTarget

        Glide.with(context)
            .asBitmap()
            .load(music.albumArtSource())
            .centerCrop()
            .into(newTarget)
    }

    companion object {
        private const val ART_SIZE_PX = 512
    }
}
