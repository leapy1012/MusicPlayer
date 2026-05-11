package gd.app.musicplayer.core.designsystem.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class WidgetRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var touchSlop: Float = 0f
    private var initialTouchX: Float? = null
    private var initialTouchY: Float? = null

    init {
        initialize()
    }

    private fun initialize() {
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        (itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                initialTouchX = event.x
                initialTouchY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                val startX = initialTouchX
                val startY = initialTouchY
                if (startX != null && startY != null) {
                    val deltaX = abs(event.x - startX)
                    val deltaY = abs(event.y - startY)
                    if (deltaX >= touchSlop || deltaY >= touchSlop) {
                        if (deltaY > deltaX) {
                            parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        initialTouchX = null
                        initialTouchY = null
                    }
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                initialTouchX = null
                initialTouchY = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
