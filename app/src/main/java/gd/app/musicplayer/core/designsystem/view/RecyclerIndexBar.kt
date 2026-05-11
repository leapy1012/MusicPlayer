package gd.app.musicplayer.core.designsystem.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.dpToPx
import kotlin.math.floor

class RecyclerIndexBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val labels = mutableListOf<String>()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
        textAlign = Paint.Align.CENTER
        textSize = context.dpToPx(12f).toFloat()
    }

    var onLabelSelected: ((String) -> Unit)? = null

    fun setTextColor(color: Int) {
        textPaint.color = color
        invalidate()
    }

    fun submitLabels(values: List<String>) {
        labels.clear()
        labels.addAll(values.distinct())
        visibility = if (labels.isEmpty()) GONE else VISIBLE
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(context.dpToPx(18f), widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (labels.isEmpty()) return

        val itemHeight = height.toFloat() / labels.size
        val centerX = width / 2f
        val centerOffset = (textPaint.descent() + textPaint.ascent()) / 2f
        labels.forEachIndexed { index, label ->
            val centerY = itemHeight * index + itemHeight / 2f
            canvas.drawText(label, centerX, centerY - centerOffset, textPaint)
        }
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (labels.isEmpty()) return false
        val index = floor((event.y / height.toFloat()) * labels.size)
            .toInt()
            .coerceIn(0, labels.lastIndex)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                onLabelSelected?.invoke(labels[index])
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }
}
