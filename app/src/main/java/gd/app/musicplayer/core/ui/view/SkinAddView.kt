package gd.app.musicplayer.core.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import gd.app.musicplayer.R
import kotlin.math.min

class SkinAddView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconBounds = Rect()

    private var circleRadius = 0f
    private var textBaseline = 0f

    private var iconDrawable: Drawable? = null
    private var labelText: String? = null

    init {
        if (!isInEditMode) {
            iconDrawable = AppCompatResources.getDrawable(context, R.drawable.skin_more)
            labelText = context.getString(R.string.gallery)
        }

        textPaint.apply {
            style = Paint.Style.FILL
            textSize = dpToPx(12f)
            textAlign = Paint.Align.CENTER
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background circle
        textPaint.color = 0x66FFFFFF
        canvas.drawCircle(width / 2f, height / 2f, circleRadius, textPaint)

        // Draw icon
        iconDrawable?.let {
            it.bounds = iconBounds
            it.draw(canvas)
        }

        // Draw label text
        labelText?.let {
            textPaint.color = Color.WHITE
            canvas.drawText(it, width / 2f, textBaseline, textPaint)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        circleRadius = min(w, h) / 2f

        val iconSize = dpToPx(30f).toInt()

        iconBounds.set(0, 0, iconSize, iconSize)

        val iconLeft = (w - iconSize) / 2
        val iconTop = (circleRadius - iconSize) + (iconSize * 0.2143f)

        iconBounds.offsetTo(iconLeft, iconTop.toInt())

        textBaseline = calculateTextBaseline(
            iconBounds.bottom + textPaint.textSize / 2f + dpToPx(4f)
        )
    }

    private fun calculateTextBaseline(centerY: Float): Float {
        val metrics = textPaint.fontMetrics
        return centerY - (metrics.ascent + metrics.descent) / 2
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}
