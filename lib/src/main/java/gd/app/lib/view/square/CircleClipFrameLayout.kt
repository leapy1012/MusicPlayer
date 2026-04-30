package gd.app.lib.view.square

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

class CircleClipFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val clipPath = Path()

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        val squareSpec = if (width > height) heightMeasureSpec else widthMeasureSpec

        super.onMeasure(squareSpec, squareSpec)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        val radius = minOf(width, height) / 2f

        clipPath.reset()
        clipPath.addCircle(
            width / 2f,
            height / 2f,
            radius,
            Path.Direction.CW
        )
    }

    override fun dispatchDraw(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.clipPath(clipPath)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saveCount)
    }
}
