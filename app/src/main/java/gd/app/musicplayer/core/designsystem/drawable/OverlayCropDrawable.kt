package gd.app.musicplayer.core.designsystem.drawable

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.ImageView

class OverlayCropDrawable(
    private val base: Drawable,
    private var overlayColor: Int = 0,
    private var underlayColor: Int = 0,
    private var expandBoundsByOnePx: Boolean = false,
    private val scaleType: ImageView.ScaleType = ImageView.ScaleType.CENTER_CROP
) : Drawable() {

    fun setOverlayColor(color: Int) {
        overlayColor = color
    }

    override fun applyTheme(theme: Resources.Theme) {
        base.applyTheme(theme)
    }

    override fun clearColorFilter() {
        base.clearColorFilter()
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.clipRect(bounds)

        if (underlayColor != 0) {
            canvas.drawColor(underlayColor)
        }

        try {
            base.draw(canvas)
        } catch (e: Exception) {
            Log.e("PictureDrawable", "draw failed", e)
        }

        if (overlayColor != 0) {
            canvas.drawColor(overlayColor)
        }

        canvas.restore()
    }

    override fun getColorFilter(): ColorFilter? = base.colorFilter

    override fun getOpacity(): Int = base.opacity

    override fun getState(): IntArray = base.state

    override fun jumpToCurrentState() {
        base.jumpToCurrentState()
    }

    override fun onBoundsChange(bounds: Rect) {
        if (scaleType != ImageView.ScaleType.CENTER_CROP && scaleType != ImageView.ScaleType.FIT_CENTER) {
            base.bounds = bounds
            return
        }

        val sourceW = base.intrinsicWidth
        val sourceH = base.intrinsicHeight
        val targetW = bounds.width() + if (expandBoundsByOnePx) 1 else 0
        val targetH = bounds.height() + if (expandBoundsByOnePx) 1 else 0

        if (sourceW <= 0 || sourceH <= 0 || targetW <= 0 || targetH <= 0) {
            base.bounds = bounds
            return
        }

        val sx = sourceW.toFloat() / targetW.toFloat()
        val sy = sourceH.toFloat() / targetH.toFloat()
        val scale = if (scaleType == ImageView.ScaleType.CENTER_CROP) minOf(sx, sy) else maxOf(sx, sy)

        val drawW = (sourceW / scale + 0.5f).toInt()
        val drawH = (sourceH / scale + 0.5f).toInt()

        val out = Rect(0, 0, drawW, drawH)
        out.offsetTo(
            (bounds.centerX() - out.width() / 2f).toInt(),
            (bounds.centerY() - out.height() / 2f).toInt()
        )
        base.bounds = out
    }

    override fun setAlpha(alpha: Int) {
        base.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        base.colorFilter = colorFilter
    }

    override fun setState(stateSet: IntArray): Boolean = base.setState(stateSet)

    override fun setTint(tintColor: Int) {
        base.setTint(tintColor)
    }

    override fun setTintList(tint: ColorStateList?) {
        base.setTintList(tint)
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        if (tintMode != null) {
            base.setTintMode(tintMode)
        }
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        return base.setVisible(visible, restart)
    }

    override fun setColorFilter(color: Int, mode: PorterDuff.Mode) {
        base.setColorFilter(color, mode)
    }
}

//typealias OverlayCenterCropDrawable = OverlayCropDrawable
