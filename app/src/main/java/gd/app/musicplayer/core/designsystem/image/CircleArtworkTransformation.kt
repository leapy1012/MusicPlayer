package gd.app.musicplayer.core.designsystem.image

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.TransformationUtils
import java.security.MessageDigest

class CircleArtworkTransformation private constructor() : BitmapTransformation() {

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int,
    ): Bitmap {
        val squaredBitmap = TransformationUtils.centerCrop(
            pool,
            toTransform,
            outWidth,
            outHeight,
        )

        val outputBitmap = pool.get(
            squaredBitmap.width,
            squaredBitmap.height,
            Bitmap.Config.ARGB_8888,
        )

        val radius = minOf(squaredBitmap.width, squaredBitmap.height) / 2f

        Canvas(outputBitmap).drawCircle(
            squaredBitmap.width / 2f,
            squaredBitmap.height / 2f,
            radius,
            createBitmapPaint(squaredBitmap),
        )

        return outputBitmap
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(CACHE_KEY_BYTES)
    }

    override fun equals(other: Any?): Boolean {
        return other is CircleArtworkTransformation
    }

    override fun hashCode(): Int {
        return HASH_CODE
    }

    private fun createBitmapPaint(bitmap: Bitmap): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            shader = BitmapShader(
                bitmap,
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP,
            )
        }
    }

    companion object {
        private const val VERSION = 1
        private const val ID = "gd.app.musicplayer.core.designsystem.image.CircleArtworkTransformation"
        private const val CACHE_KEY = "$ID.$VERSION"
        private val CACHE_KEY_BYTES = CACHE_KEY.toByteArray(Charsets.UTF_8)
        private val HASH_CODE = CACHE_KEY.hashCode()

        val INSTANCE = CircleArtworkTransformation()
    }
}