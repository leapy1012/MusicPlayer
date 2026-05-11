package gd.app.musicplayer.core.designsystem.view

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.common.extension.spToPx

import gd.app.musicplayer.R

class WelcomeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val iconDrawable: Drawable? =
        ContextCompat.getDrawable(context, R.drawable.welcome_icon)

    private val iconBounds = Rect()
    private val arcRect = RectF()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val welcomeText = context.getString(R.string.welcome)

    private val iconTextSpacing = context.dpToPx(40f)
    private var textSize = context.spToPx(40f)

    init {
        textPaint.textSize = textSize
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(ContextCompat.getColor(context, R.color.welcome_background_color))

        val drawable = iconDrawable

        if (drawable == null) {
            drawCenteredText(canvas, height / 2f)
            return
        }

        drawArc(canvas)

        drawable.bounds = iconBounds
        drawable.draw(canvas)

        val textY = baseline((iconBounds.bottom + iconTextSpacing).toFloat())

        if (textY + textSize / 2 <= height) {
            textPaint.color = context.getColor(R.color.welcome_text_color)
            canvas.drawText(welcomeText, width / 2f, textY, textPaint)
        }
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)

        val drawable = iconDrawable ?: return
        if (w <= 0 || h <= 0) return

        val iconWidth = drawable.intrinsicWidth
        val iconHeight = drawable.intrinsicHeight

        val iconTop = ((h - iconHeight) * 0.382f).toInt()

        iconBounds.set(
            (w - iconWidth) / 2,
            iconTop,
            (w + iconWidth) / 2,
            iconTop + iconHeight
        )

        val arcTop = iconTop + iconHeight * 0.63f
        val arcSize = max(w, h) * 2f

        arcRect.set(
            (w - arcSize) / 2f,
            arcTop,
            (w + arcSize) / 2f,
            arcTop + arcSize
        )

        adjustTextSize(w)
    }

    private fun drawArc(canvas: Canvas) {
        textPaint.color = ContextCompat.getColor(context, R.color.welcome_arc_color)
        canvas.drawArc(arcRect, 180f, 360f, false, textPaint)
    }

    private fun drawCenteredText(canvas: Canvas, centerY: Float) {
        textPaint.color = ContextCompat.getColor(context, R.color.welcome_text_color)
        canvas.drawText(welcomeText, width / 2f, baseline(centerY), textPaint)
    }

    private fun baseline(centerY: Float): Float {
        val fm = textPaint.fontMetrics
        return centerY - (fm.ascent + fm.descent) / 2
    }

    private fun adjustTextSize(viewWidth: Int) {
        val maxWidth = viewWidth * 0.8f

        textPaint.textSize = textSize

        while (textPaint.measureText(welcomeText) > maxWidth) {
            textSize -= 1f
            textPaint.textSize = textSize
        }
    }

}
