package gd.app.lib.model.lrc.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView

class LyricScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    interface OnDispatchTouchListener {
        fun onDispatchTouch(view: View, event: MotionEvent)
    }

    private var maxHeightPx: Int = 0
    private var dispatchTouchListener: OnDispatchTouchListener? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        dispatchTouchListener?.onDispatchTouch(this, ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var resolvedHeightSpec = heightMeasureSpec
        if (maxHeightPx > 0) {
            val size = MeasureSpec.getSize(heightMeasureSpec)
            if (size > maxHeightPx) {
                resolvedHeightSpec = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.getMode(heightMeasureSpec))
            }
        }
        super.onMeasure(widthMeasureSpec, resolvedHeightSpec)
    }

    fun setMaxHeight(heightPx: Int) {
        maxHeightPx = heightPx
        requestLayout()
    }

    fun setOnDispatchTouchListener(listener: OnDispatchTouchListener?) {
        dispatchTouchListener = listener
    }
}
