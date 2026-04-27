package gd.app.lib.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.coordinatorlayout.widget.CoordinatorLayout

class InterceptTouchCoordinatorLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CoordinatorLayout(context, attrs, defStyleAttr) {

    private var touchHandlingDisabled: Boolean = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return !touchHandlingDisabled && super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return !touchHandlingDisabled && super.onTouchEvent(event)
    }

    fun setInterceptTouchEvent(disabled: Boolean) {
        touchHandlingDisabled = disabled
    }
}