package gd.app.musicplayer.core.designsystem.drawable

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.widget.ImageView
import kotlin.math.max
import kotlin.math.min

class ScaledOverlayDrawable(
    private val sourceDrawable: Drawable?,
    private val scaleType: ImageView.ScaleType = ImageView.ScaleType.CENTER_CROP,
) : Drawable() {

    private var foregroundOverlayColor: Int = 0
    private var backgroundOverlayColor: Int = 0
    private var expandBoundsByOnePixel: Boolean = false

    fun setForegroundOverlayColor(color: Int) {
        foregroundOverlayColor = color
        invalidateSelf()
    }

    fun setBackgroundOverlayColor(color: Int) {
        backgroundOverlayColor = color
        invalidateSelf()
    }

    fun setExpandBoundsByOnePixel(enabled: Boolean) {
        expandBoundsByOnePixel = enabled
        onBoundsChange(bounds)
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val saveCount = canvas.save()

        canvas.clipRect(bounds)

        if (backgroundOverlayColor != 0) {
            canvas.drawColor(backgroundOverlayColor)
        }

        try {
            sourceDrawable?.draw(canvas)
        } catch (exception: Exception) {

        }

        if (foregroundOverlayColor != 0) {
            canvas.drawColor(foregroundOverlayColor)
        }

        canvas.restoreToCount(saveCount)
    }

    override fun onBoundsChange(bounds: Rect) {
        val drawable = sourceDrawable ?: return

        if (scaleType != ImageView.ScaleType.CENTER_CROP &&
            scaleType != ImageView.ScaleType.FIT_CENTER
        ) {
            drawable.bounds = bounds
            return
        }

        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight

        val targetWidth = bounds.width() + if (expandBoundsByOnePixel) 1 else 0
        val targetHeight = bounds.height() + if (expandBoundsByOnePixel) 1 else 0

        if (
            intrinsicWidth <= 0 ||
            intrinsicHeight <= 0 ||
            targetWidth <= 0 ||
            targetHeight <= 0
        ) {
            drawable.bounds = bounds
            return
        }

        val widthScale = intrinsicWidth.toFloat() / targetWidth
        val heightScale = intrinsicHeight.toFloat() / targetHeight

        val scale = if (scaleType == ImageView.ScaleType.CENTER_CROP) {
            min(widthScale, heightScale)
        } else {
            max(widthScale, heightScale)
        }

        val scaledWidth = (intrinsicWidth / scale + 0.5f).toInt()
        val scaledHeight = (intrinsicHeight / scale + 0.5f).toInt()

        val scaledBounds = Rect(0, 0, scaledWidth, scaledHeight).apply {
            offsetTo(
                (bounds.centerX() - width() / 2f).toInt(),
                (bounds.centerY() - height() / 2f).toInt()
            )
        }

        drawable.bounds = scaledBounds
    }

    override fun applyTheme(theme: Resources.Theme) {
        sourceDrawable?.applyTheme(theme)
    }

    override fun clearColorFilter() {
        sourceDrawable?.clearColorFilter()
    }

    override fun getColorFilter(): ColorFilter? {
        return sourceDrawable?.colorFilter
    }

    override fun getOpacity(): Int {
        return sourceDrawable?.opacity ?: PixelFormat.TRANSLUCENT
    }

    override fun getState(): IntArray {
        return sourceDrawable?.state ?: super.getState()
    }

    override fun jumpToCurrentState() {
        sourceDrawable?.jumpToCurrentState()
    }

    override fun setAlpha(alpha: Int) {
        sourceDrawable?.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        sourceDrawable?.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun setState(stateSet: IntArray): Boolean {
        return sourceDrawable?.setState(stateSet) ?: false
    }

    override fun setTint(tintColor: Int) {
        sourceDrawable?.setTint(tintColor)
        invalidateSelf()
    }

    override fun setTintList(tint: ColorStateList?) {
        sourceDrawable?.setTintList(tint)
        invalidateSelf()
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        sourceDrawable?.setTintMode(tintMode)
        invalidateSelf()
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        return sourceDrawable?.setVisible(visible, restart) ?: false
    }

    @Deprecated(
        "Deprecated in Android framework",
        ReplaceWith("setColorFilter(color, tintMode)")
    )
    override fun setColorFilter(color: Int, tintMode: PorterDuff.Mode) {
        sourceDrawable?.setColorFilter(color, tintMode)
        invalidateSelf()
    }
}
