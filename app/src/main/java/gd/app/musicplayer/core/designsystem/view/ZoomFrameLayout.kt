package gd.app.musicplayer.core.designsystem.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.min
import androidx.core.graphics.withScale
import androidx.core.view.isEmpty

class ZoomFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var zoomScale: Float = 1f

    override fun dispatchDraw(canvas: Canvas) {
        canvas.withScale(
            zoomScale,
            zoomScale,
            width / 2f,
            height / 2f
        ) {

            super.dispatchDraw(canvas)

        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Preview-only container. Consume all touches so child widget preview is not interactive.
        return true
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        zoomScale = calculateZoomScale(
            widthMeasureSpec = widthMeasureSpec,
            heightMeasureSpec = heightMeasureSpec
        )

        super.onMeasure(
            widthMeasureSpec,
            heightMeasureSpec
        )
    }

    private fun calculateZoomScale(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ): Float {
        if (isEmpty()) return DEFAULT_SCALE

        val child = getChildAt(0)
        val childLayoutParams = child.layoutParams as? LayoutParams
            ?: return DEFAULT_SCALE

        val availableWidth =
            View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight

        val availableHeight =
            View.MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom

        val childWidth = childLayoutParams.width
        val childHeight = childLayoutParams.height

        if (availableWidth <= 0 || availableHeight <= 0) return DEFAULT_SCALE
        if (childWidth <= 0 || childHeight <= 0) return DEFAULT_SCALE

        val widthScale = availableWidth.toFloat() / childWidth.toFloat()
        val heightScale = availableHeight.toFloat() / childHeight.toFloat()

        return min(
            DEFAULT_SCALE,
            min(widthScale, heightScale)
        )
    }

    private companion object {
        private const val DEFAULT_SCALE = 1f
    }
}
