package gd.app.musicplayer.core.ui.image

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.TransformationUtils
import java.security.MessageDigest

class CircleArtworkTransformation : BitmapTransformation() {

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(CACHE_KEY.toByteArray(Charsets.UTF_8))
    }

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val squared = TransformationUtils.centerCrop(pool, toTransform, outWidth, outHeight)

        val result = pool.get(
            squared.width,
            squared.height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            shader = BitmapShader(
                squared,
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP
            )
        }

        val radius = squared.width / 2f
        canvas.drawCircle(
            squared.width / 2f,
            squared.height / 2f,
            radius,
            paint
        )

        return result
    }

    override fun equals(other: Any?): Boolean {
        return other is CircleArtworkTransformation
    }

    override fun hashCode(): Int {
        return CACHE_KEY.hashCode()
    }

    companion object {
        private const val CACHE_KEY = "circle_artwork_transformation"
        val INSTANCE = CircleArtworkTransformation()
    }
}
