package gd.app.musicplayer.core.ui.drawable

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.core.graphics.withSave

class RoundedMaskDrawable(
    private val base: Drawable,
    private val cornerRadiusPx: Float
) : Drawable() {

    override fun onBoundsChange(bounds: Rect) {
        base.bounds = bounds
    }

    override fun draw(canvas: Canvas) {
        canvas.withSave {
            val path = android.graphics.Path().apply {
                addRoundRect(
                    bounds.left.toFloat(),
                    bounds.top.toFloat(),
                    bounds.right.toFloat(),
                    bounds.bottom.toFloat(),
                    cornerRadiusPx,
                    cornerRadiusPx,
                    android.graphics.Path.Direction.CW
                )
            }
            canvas.clipPath(path)
            base.draw(canvas)
        }
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, cornerRadiusPx)
    }

    override fun setAlpha(alpha: Int) {
        base.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        base.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}