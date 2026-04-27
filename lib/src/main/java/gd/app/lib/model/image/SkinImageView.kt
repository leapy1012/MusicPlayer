package gd.app.lib.model.image

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView

class SkinImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    @DrawableRes
    private var errorDrawableResId: Int = 0

    private var errorDrawable: Drawable? = null
    private var hasAppliedFallbackDrawable: Boolean = false

    override fun onDraw(canvas: Canvas) {
        if (hasAppliedFallbackDrawable) {
            drawSafely(canvas)
            return
        }

        try {
            super.onDraw(canvas)
        } catch (_: Exception) {
            applyFallbackDrawableIfNeeded()

            if (hasAppliedFallbackDrawable) {
                drawSafely(canvas)
            }
        }
    }

    fun setErrorDrawableResId(@DrawableRes drawableResId: Int) {
        errorDrawableResId = drawableResId
        errorDrawable = null
        hasAppliedFallbackDrawable = false
    }

    // Optional: keep old API name for compatibility with existing Java/Kotlin calls
    fun setErrorResId(@DrawableRes drawableResId: Int) {
        setErrorDrawableResId(drawableResId)
    }

    private fun applyFallbackDrawableIfNeeded() {
        if (errorDrawableResId == 0) return
        if (hasAppliedFallbackDrawable) return

        if (errorDrawable == null) {
            errorDrawable = loadDrawable(errorDrawableResId)
        }

        errorDrawable?.let { drawable ->
            hasAppliedFallbackDrawable = true
            setImageDrawable(drawable)
        }
    }

    private fun loadDrawable(@DrawableRes drawableResId: Int): Drawable? {
        return AppCompatResources.getDrawable(context, drawableResId)
    }

    private fun drawSafely(canvas: Canvas) {
        try {
            super.draw(canvas)
        } catch (_: Exception) {
            // Prevent crash even if fallback drawable also fails
        }
    }
}