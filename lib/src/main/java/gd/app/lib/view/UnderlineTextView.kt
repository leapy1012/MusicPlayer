package gd.app.lib.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import gd.app.lib.dpToPx

class UnderlineTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val underlineStrokeWidth: Float = context.dpToPx(1.0f).toFloat()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val underlineY = height - (underlineStrokeWidth / 2f) - 0.5f
        val textPaint = paint
        val originalAlpha = textPaint.alpha

        textPaint.alpha = originalAlpha / 2
        canvas.drawLine(
            paddingLeft.toFloat(),
            underlineY,
            (width - paddingRight).toFloat(),
            underlineY,
            textPaint
        )
        textPaint.alpha = originalAlpha
    }
}