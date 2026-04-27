package gd.app.lib.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import gd.app.lib.R
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.withClip

class UniformRadiusImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var cornerRadiusPx: Int = 0
    private var usesOutlineClipping: Boolean = false
    private val roundedClipPath: Path?

    init {
        context.withStyledAttributes(attrs, R.styleable.UniformRadiusImageView) {
            cornerRadiusPx =
                getDimensionPixelSize(R.styleable.UniformRadiusImageView_urivCornerRadius, 0)
            usesOutlineClipping = getInt(R.styleable.UniformRadiusImageView_urivRoundMode, 0) == 0
        }

        roundedClipPath = when {
            usesOutlineClipping -> {
                if (cornerRadiusPx > 0) {
                    applyRoundedOutline(this, cornerRadiusPx.toFloat())
                }
                null
            }

            cornerRadiusPx > 0 -> Path()
            else -> null
        }
    }

    override fun onDraw(canvas: Canvas) {
        val path = roundedClipPath
        if (path == null) {
            super.onDraw(canvas)
            return
        }

        canvas.withClip(path) {
            super.onDraw(canvas)
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        val path = roundedClipPath ?: return
        if (width <= 0 || height <= 0) return

        path.reset()
        path.addRoundRect(
            RectF(
                paddingLeft.toFloat(),
                paddingTop.toFloat(),
                (width - paddingRight).toFloat(),
                (height - paddingBottom).toFloat()
            ),
            uniformCornerRadii(cornerRadiusPx.toFloat()),
            Path.Direction.CW
        )
    }

    fun uniformCornerRadii(radius: Float): FloatArray {
        return FloatArray(8) { radius }
    }

    fun applyRoundedOutline(view: View, cornerRadius: Float) {
        view.clipToOutline = true
        view.outlineProvider = RoundedOutlineProvider(cornerRadius)
    }
}