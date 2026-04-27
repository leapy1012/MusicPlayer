package gd.app.lib.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import kotlin.math.abs

class DragDismissLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    interface OnDismissListener {
        fun onDismissed(view: View)
    }

    object Direction {
        const val LEFT = 1
        const val RIGHT = 1 shl 1
        const val UP = 1 shl 2
        const val DOWN = 1 shl 3
        const val HORIZONTAL = LEFT or RIGHT
        const val VERTICAL = UP or DOWN
        const val ALL = HORIZONTAL or VERTICAL
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val dragCallback = DismissDragCallback()
    private val dragHelper = ViewDragHelper.create(this, dragCallback)

    private var dismissListener: OnDismissListener? = null
    private var allowedDirections: Int = Direction.ALL
    private var childHandlesOwnTouch = false
    private var childTouchCanceled = false
    private var dismissTarget: View? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        dismissTarget = getChildAt(0)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        dragHelper.shouldInterceptTouchEvent(event)
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        try {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                childTouchCanceled = false
            }

            if (!childHandlesOwnTouch) {
                dragHelper.processTouchEvent(event)
            }

            val target = dismissTarget
            if (target != null) {
                if (childHandlesOwnTouch || childTouchCanceled || !dragCallback.isDragging()) {
                    target.dispatchTouchEvent(event)
                } else {
                    childTouchCanceled = true
                    val cancelEvent = MotionEvent.obtain(event)
                    cancelEvent.action = MotionEvent.ACTION_CANCEL
                    target.dispatchTouchEvent(cancelEvent)
                    cancelEvent.recycle()
                }
            }
        } catch (_: Exception) {
        }
        return true
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Parent interception is intentionally controlled by the drag-dismiss logic.
    }

    override fun computeScroll() {
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this)
        } else if (dragCallback.shouldNotifyDismiss()) {
            dragCallback.setShouldNotifyDismiss(false)
            dismissTarget?.let { target ->
                post { dismissListener?.onDismissed(target) }
            }
        }
    }

    fun setAllowedDirections(directions: Int) {
        allowedDirections = directions
    }

    fun setDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        childHandlesOwnTouch = disallowIntercept
        if (disallowIntercept) {
            dragHelper.cancel()
        }
    }

    fun setOnDismissListener(listener: OnDismissListener?) {
        dismissListener = listener
    }

    private inner class DismissDragCallback : ViewDragHelper.Callback() {
        private var activeDirection: Int = 0
        private var notifyDismiss = false
        private var accumulatedDx = 0
        private var accumulatedDy = 0

        fun isDragging(): Boolean = activeDirection != 0

        fun shouldNotifyDismiss(): Boolean = notifyDismiss

        fun setShouldNotifyDismiss(shouldNotifyDismiss: Boolean) {
            notifyDismiss = shouldNotifyDismiss
        }

        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            return child == dismissTarget
        }

        override fun onViewCaptured(capturedChild: View, activePointerId: Int) {
            notifyDismiss = false
            activeDirection = 0
            accumulatedDx = 0
            accumulatedDy = 0
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            val width = child.width
            when (activeDirection) {
                0 -> {
                    accumulatedDx += dx
                    if (abs(accumulatedDx) < touchSlop) return 0

                    val direction = if (left < 0) Direction.LEFT else Direction.RIGHT
                    if ((allowedDirections and direction) == 0) {
                        dragHelper.cancel()
                        return 0
                    }
                    activeDirection = direction
                }

                Direction.LEFT -> return left.coerceIn(-width, 0)
                Direction.RIGHT -> return left.coerceIn(0, width)
                else -> return 0
            }
            return left.coerceIn(-width, width)
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            val height = child.height
            when (activeDirection) {
                0 -> {
                    accumulatedDy += dy
                    if (abs(accumulatedDy) < touchSlop) return 0

                    val direction = if (top < 0) Direction.UP else Direction.DOWN
                    if ((allowedDirections and direction) == 0) {
                        dragHelper.cancel()
                        return 0
                    }
                    activeDirection = direction
                }

                Direction.UP -> return top.coerceIn(-height, 0)
                Direction.DOWN -> return top.coerceIn(0, height)
                else -> return 0
            }
            return top.coerceIn(-height, height)
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val width = releasedChild.width
            val height = releasedChild.height
            val left = releasedChild.left
            val top = releasedChild.top

            var finalLeft = 0
            var finalTop = 0

            when (activeDirection) {
                Direction.LEFT -> {
                    if (left < 0 && ((-left) > width * 0.3f || xvel < -4000f)) {
                        notifyDismiss = true
                        finalLeft = -width
                    }
                }

                Direction.RIGHT -> {
                    if (left > 0 && (left > width * 0.3f || xvel > 4000f)) {
                        notifyDismiss = true
                        finalLeft = width
                    }
                }

                Direction.UP -> {
                    if (top < 0 && ((-top) > height * 0.25f || yvel < -4000f)) {
                        notifyDismiss = true
                        finalTop = -height
                    }
                }

                Direction.DOWN -> {
                    if (top > 0 && (top > height * 0.25f || yvel > 4000f)) {
                        notifyDismiss = true
                        finalTop = height
                    }
                }
            }

            dragHelper.settleCapturedViewAt(finalLeft, finalTop)
            activeDirection = 0
            ViewCompat.postInvalidateOnAnimation(this@DragDismissLayout)
        }
    }
}
