package gd.app.musicplayer.playback.desktop

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class DesktopLyricPresetDrawable(
    private val currentColor: Int,
    private val normalColor: Int
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val radius = minOf(bounds.width(), bounds.height()) / 2f

        canvas.save()
        paint.color = currentColor
        canvas.clipRect(bounds.left, bounds.top, bounds.right, bounds.centerY())
        canvas.drawCircle(bounds.centerX().toFloat(), bounds.centerY().toFloat(), radius, paint)
        canvas.restore()

        canvas.save()
        paint.color = normalColor
        canvas.clipRect(bounds.left, bounds.centerY(), bounds.right, bounds.bottom)
        canvas.drawCircle(bounds.centerX().toFloat(), bounds.centerY().toFloat(), radius, paint)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
