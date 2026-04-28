package gd.app.musicplayer.core.ui.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.core.graphics.withScale
import androidx.core.view.isNotEmpty

class ZoomFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var contentScale = 1f

    override fun dispatchDraw(canvas: Canvas) {
        canvas.withScale(contentScale, contentScale, width / 2f, height / 2f) {
            super.dispatchDraw(this)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (isNotEmpty()) {
            val availableWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            val availableHeight = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
            val childLayoutParams = getChildAt(0).layoutParams as LayoutParams

            val widthScale = if (childLayoutParams.width > 0) {
                availableWidth.toFloat() / childLayoutParams.width
            } else {
                1f
            }
            val heightScale = if (childLayoutParams.height > 0) {
                availableHeight.toFloat() / childLayoutParams.height
            } else {
                1f
            }

            contentScale = minOf(1f, widthScale, heightScale)
        } else {
            contentScale = 1f
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
