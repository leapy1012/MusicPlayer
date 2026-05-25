package gd.app.musicplayer.core.designsystem.drawable

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView

class OverlayCenterCropDrawable(
    resources: Resources,
    bitmap: Bitmap?,
    private var overlayColor: Int = 0,
) : Drawable() {

    private val contentDrawable: Drawable? = bitmap?.let { BitmapDrawable(resources, it) }
    private val scaleType = ImageView.ScaleType.CENTER_CROP

    override fun draw(canvas: Canvas) {
        val clip = bounds
        canvas.save()
        canvas.clipRect(clip)
        try {
            contentDrawable?.draw(canvas)
        } catch (_: Exception) {
        }
        if (overlayColor != 0) {
            canvas.drawColor(overlayColor)
        }
        canvas.restore()
    }

    override fun onBoundsChange(bounds: Rect) {
        val drawable = contentDrawable ?: return
        if (scaleType != ImageView.ScaleType.CENTER_CROP && scaleType != ImageView.ScaleType.FIT_CENTER) {
            drawable.bounds = bounds
            return
        }

        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight
        val viewWidth = bounds.width()
        val viewHeight = bounds.height()
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            drawable.bounds = bounds
            return
        }

        val widthScale = intrinsicWidth.toFloat() / viewWidth.toFloat()
        val heightScale = intrinsicHeight.toFloat() / viewHeight.toFloat()
        val divider = if (scaleType == ImageView.ScaleType.CENTER_CROP) {
            minOf(widthScale, heightScale)
        } else {
            maxOf(widthScale, heightScale)
        }

        val outWidth = (intrinsicWidth.toFloat() / divider + 0.5f).toInt()
        val outHeight = (intrinsicHeight.toFloat() / divider + 0.5f).toInt()
        val left = (bounds.centerX() - (outWidth / 2f)).toInt()
        val top = (bounds.centerY() - (outHeight / 2f)).toInt()
        drawable.setBounds(left, top, left + outWidth, top + outHeight)
    }

    override fun setAlpha(alpha: Int) {
        contentDrawable?.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        contentDrawable?.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = contentDrawable?.opacity ?: PixelFormat.TRANSLUCENT

    override fun setTint(tintColor: Int) {
        contentDrawable?.setTint(tintColor)
    }

    override fun setTintList(tint: ColorStateList?) {
        contentDrawable?.setTintList(tint)
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        if (tintMode != null) {
            contentDrawable?.setTintMode(tintMode)
        }
    }
}
