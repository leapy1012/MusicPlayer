package gd.app.musicplayer.core.designsystem.theme

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.ImageView
import kotlin.math.max
import kotlin.math.min

class OverlayDrawable(
    private val baseDrawable: Drawable?
) : Drawable() {
    private val scaleType = ImageView.ScaleType.CENTER_CROP
    private var foregroundOverlayColor: Int = 0
    private var backgroundOverlayColor: Int = 0
    private var expandBoundsByOnePixel: Boolean = false

    fun setForegroundOverlayColor(color: Int) {
        foregroundOverlayColor = color
    }

    override fun applyTheme(theme: Resources.Theme) {
        baseDrawable?.applyTheme(theme)
    }

    override fun clearColorFilter() {
        baseDrawable?.clearColorFilter()
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.clipRect(bounds)
        if (backgroundOverlayColor != 0) {
            canvas.drawColor(backgroundOverlayColor)
        }
        try {
            baseDrawable?.draw(canvas)
        } catch (e: Exception) {
            Log.e("PictureDrawable", "Failed to draw themed drawable", e)
        }
        if (foregroundOverlayColor != 0) {
            canvas.drawColor(foregroundOverlayColor)
        }
        canvas.restore()
    }

    override fun getColorFilter(): ColorFilter? = baseDrawable?.colorFilter

    override fun getOpacity(): Int = baseDrawable?.opacity ?: PixelFormat.TRANSLUCENT

    override fun getState(): IntArray = baseDrawable?.state ?: super.getState()

    override fun jumpToCurrentState() {
        baseDrawable?.jumpToCurrentState()
    }

    override fun onBoundsChange(bounds: Rect) {
        val drawable = baseDrawable ?: return
        if (scaleType != ImageView.ScaleType.CENTER_CROP && scaleType != ImageView.ScaleType.FIT_CENTER) {
            drawable.bounds = bounds
            return
        }

        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight
        val width = bounds.width() + if (expandBoundsByOnePixel) 1 else 0
        val height = bounds.height() + if (expandBoundsByOnePixel) 1 else 0
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0 || width <= 0 || height <= 0) {
            drawable.bounds = bounds
            return
        }

        val widthScale = intrinsicWidth.toFloat() / width
        val heightScale = intrinsicHeight.toFloat() / height
        val scale = if (scaleType == ImageView.ScaleType.CENTER_CROP) {
            min(widthScale, heightScale)
        } else {
            max(widthScale, heightScale)
        }
        val scaled = Rect(
            0,
            0,
            (intrinsicWidth / scale + 0.5f).toInt(),
            (intrinsicHeight / scale + 0.5f).toInt()
        )
        scaled.offsetTo(
            (bounds.centerX() - scaled.width() / 2f).toInt(),
            (bounds.centerY() - scaled.height() / 2f).toInt()
        )
        drawable.bounds = scaled
    }

    override fun setAlpha(alpha: Int) {
        baseDrawable?.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        baseDrawable?.colorFilter = colorFilter
    }

    override fun setState(stateSet: IntArray): Boolean = baseDrawable?.setState(stateSet) == true

    override fun setTint(tintColor: Int) {
        baseDrawable?.setTint(tintColor)
    }

    override fun setTintList(tint: ColorStateList?) {
        baseDrawable?.setTintList(tint)
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        baseDrawable?.setTintMode(tintMode)
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean =
        baseDrawable?.setVisible(visible, restart) == true

    @Deprecated("Deprecated in Drawable")
    override fun setColorFilter(color: Int, mode: PorterDuff.Mode) {
        baseDrawable?.setColorFilter(color, mode)
    }
}
