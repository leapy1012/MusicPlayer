package gd.app.musicplayer.core.designsystem.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs

class DeskLrcDragLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var dragListener: DeskLyricsDragListener? = null

    private var lastRawY: Float = 0f
    private var isDragging: Boolean = false
    private var touchDownTimeMs: Long = 0L

    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop

    interface DeskLyricsDragListener {
        /**
         * Called when the user performs a quick tap without dragging.
         */
        fun onQuickClick(view: View)

        /**
         * Called while the layout is being dragged vertically.
         *
         * @param deltaY vertical movement since the previous drag event.
         */
        fun onDrag(view: View, deltaY: Float)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val listener = dragListener ?: return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownTimeMs = System.currentTimeMillis()
                isDragging = false
                lastRawY = event.rawY
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val currentRawY = event.rawY
                val deltaY = currentRawY - lastRawY

                if (!isDragging) {
                    if (abs(deltaY) > touchSlop) {
                        isDragging = true
                        lastRawY = currentRawY
                    }
                    return true
                }

                lastRawY = currentRawY
                listener.onDrag(this, deltaY)
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    val currentRawY = event.rawY
                    val deltaY = currentRawY - lastRawY
                    lastRawY = currentRawY

                    listener.onDrag(this, deltaY)
                } else {
                    val touchDurationMs = System.currentTimeMillis() - touchDownTimeMs

                    if (touchDurationMs < QUICK_CLICK_THRESHOLD_MS) {
                        listener.onQuickClick(this)
                    }
                }

                return true
            }
        }

        return true
    }

    fun setDeskLyricsDragListener(listener: DeskLyricsDragListener?) {
        dragListener = listener
    }

    companion object {
        private const val QUICK_CLICK_THRESHOLD_MS = 230L
    }
}