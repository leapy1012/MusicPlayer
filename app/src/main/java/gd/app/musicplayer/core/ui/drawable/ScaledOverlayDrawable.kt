package gd.app.musicplayer.core.ui.drawable

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.graphics.withClip

class ScaledOverlayDrawable(
    private val wrappedDrawable: Drawable?
) : Drawable() {

    private val scaleType: ImageView.ScaleType = ImageView.ScaleType.CENTER_CROP

    private var foregroundOverlayColor: Int = 0
    private var backgroundFillColor: Int = 0
    private var includeExtraPixelForBounds: Boolean = false

    fun setForegroundOverlayColor(color: Int) {
        if (foregroundOverlayColor == color) return
        foregroundOverlayColor = color
        invalidateSelf()
    }

    fun setBackgroundFillColor(color: Int) {
        if (backgroundFillColor == color) return
        backgroundFillColor = color
        invalidateSelf()
    }

    fun setIncludeExtraPixelForBounds(enabled: Boolean) {
        includeExtraPixelForBounds = enabled
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        canvas.withClip(bounds) {
            if (backgroundFillColor != 0) {
                drawColor(backgroundFillColor)
            }

            try {
                wrappedDrawable?.draw(this)
            } catch (error: Exception) {
                error.printStackTrace()
            }

            if (foregroundOverlayColor != 0) {
                drawColor(foregroundOverlayColor)
            }

        }
    }

    override fun onBoundsChange(bounds: Rect) {
        val drawable = wrappedDrawable ?: return

        if (scaleType != ImageView.ScaleType.CENTER_CROP &&
            scaleType != ImageView.ScaleType.FIT_CENTER
        ) {
            drawable.bounds = bounds
            return
        }

        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight

        val extra = if (includeExtraPixelForBounds) 1 else 0
        val targetWidth = bounds.width() + extra
        val targetHeight = bounds.height() + extra

        if (intrinsicWidth <= 0 || intrinsicHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            drawable.bounds = bounds
            return
        }

        val widthScale = intrinsicWidth.toFloat() / targetWidth.toFloat()
        val heightScale = intrinsicHeight.toFloat() / targetHeight.toFloat()

        val scale = if (scaleType == ImageView.ScaleType.CENTER_CROP) {
            minOf(widthScale, heightScale)
        } else {
            maxOf(widthScale, heightScale)
        }

        val scaledWidth = (intrinsicWidth / scale + 0.5f).toInt()
        val scaledHeight = (intrinsicHeight / scale + 0.5f).toInt()

        val scaledBounds = Rect(0, 0, scaledWidth, scaledHeight)
        scaledBounds.offsetTo(
            (bounds.centerX() - scaledBounds.width() / 2f).toInt(),
            (bounds.centerY() - scaledBounds.height() / 2f).toInt()
        )

        drawable.bounds = scaledBounds
    }

    override fun isStateful(): Boolean {
        return wrappedDrawable?.isStateful == true
    }

    override fun applyTheme(theme: Resources.Theme) {
        wrappedDrawable?.applyTheme(theme)
    }

    override fun clearColorFilter() {
        wrappedDrawable?.clearColorFilter()
    }

    override fun getColorFilter(): ColorFilter? {
        return wrappedDrawable?.colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        return wrappedDrawable?.opacity ?: PixelFormat.TRANSLUCENT
    }

    override fun getState(): IntArray {
        return wrappedDrawable?.state ?: super.getState()
    }

    override fun jumpToCurrentState() {
        wrappedDrawable?.jumpToCurrentState()
    }

    override fun setAlpha(alpha: Int) {
        wrappedDrawable?.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        wrappedDrawable?.colorFilter = colorFilter
    }

    override fun setState(stateSet: IntArray): Boolean {
        return wrappedDrawable?.setState(stateSet) ?: false
    }

    override fun setTint(tintColor: Int) {
        wrappedDrawable?.setTint(tintColor)
    }

    override fun setTintList(tint: ColorStateList?) {
        wrappedDrawable?.setTintList(tint)
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        wrappedDrawable?.setTintMode(tintMode)
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        return wrappedDrawable?.setVisible(visible, restart) ?: false
    }

    @Deprecated("Deprecated in Java")
    override fun setColorFilter(color: Int, mode: PorterDuff.Mode) {
        wrappedDrawable?.setColorFilter(color, mode)
    }
}
