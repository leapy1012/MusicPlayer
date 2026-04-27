package gd.app.lib.view.square

import android.content.Context
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.FrameLayout

class SquareCornerFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var squareResolver: c.a? = null
    private var cornerRadiusPx = context.resources.displayMetrics.density * 12f
    private val clipPath = Path()
    private val clipRect = RectF()

    fun setSquare(resolver: c.a?) {
        squareResolver = resolver
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val adjusted = squareResolver?.a(widthMeasureSpec, heightMeasureSpec)
        if (adjusted != null && adjusted.size == 2) {
            super.onMeasure(adjusted[0], adjusted[1])
            return
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        clipRect.set(0f, 0f, width.toFloat(), height.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(clipRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        super.dispatchDraw(canvas)
        canvas.restore()
    }
}
