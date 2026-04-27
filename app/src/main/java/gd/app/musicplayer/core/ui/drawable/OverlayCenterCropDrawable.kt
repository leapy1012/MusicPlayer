package gd.app.musicplayer.core.ui.drawable

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.core.graphics.withClip

class OverlayCenterCropDrawable(
    resources: Resources,
    bitmap: Bitmap?,
    private var overlayColor: Int = 0,
) : BitmapDrawable(resources, bitmap) {

    private val drawMatrix = Matrix()
    private val overlayPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)

        val bitmap = bitmap ?: return
        val src = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())

        val drawableWidth = src.width()
        val drawableHeight = src.height()
        val viewWidth = bounds.width().toFloat()
        val viewHeight = bounds.height().toFloat()

        val scale: Float
        var dx = 0f
        var dy = 0f

        if (drawableWidth * viewHeight > viewWidth * drawableHeight) {
            scale = viewHeight / drawableHeight
            dx = (viewWidth - drawableWidth * scale) * 0.5f
        } else {
            scale = viewWidth / drawableWidth
            dy = (viewHeight - drawableHeight * scale) * 0.5f
        }

        drawMatrix.setScale(scale, scale)
        drawMatrix.postTranslate(dx, dy)
    }

    override fun draw(canvas: Canvas) {

        canvas.withClip(bounds) {
            drawBitmap(bitmap, drawMatrix, null)

            if (overlayColor != 0) {
                overlayPaint.color = overlayColor
                drawRect(bounds, overlayPaint)
            }

        }
    }
}