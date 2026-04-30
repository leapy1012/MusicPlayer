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
        const val RIGHT = 2
        const val UP = 4
        const val DOWN = 8
        const val ALL = LEFT or RIGHT or UP or DOWN
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val dragCallback = DragCallback()
    private val dragHelper = ViewDragHelper.create(this, dragCallback)

    private var contentView: View? = null
    private var dismissListener: OnDismissListener? = null

    private var allowedDirections: Int = Direction.ALL
    private var disallowDragIntercept = false
    private var hasSentCancelToChild = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        contentView = getChildAt(0)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        dragHelper.processTouchEvent(event)
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        try {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                hasSentCancelToChild = false
            }

            if (!disallowDragIntercept) {
                dragHelper.processTouchEvent(event)
            }

            dispatchEventToChild(event)

            if (event.actionMasked == MotionEvent.ACTION_UP && !dragCallback.isDragging) {
                performClick()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun computeScroll() {
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this)
        } else if (dragCallback.isDismissed) {
            notifyDismissed()
        }
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Intentionally ignored.
        // This layout manages drag interception manually.
    }

    fun setAllowedDirections(directions: Int) {
        allowedDirections = directions
    }

    fun setAllowedDirection(direction: Int) {
        allowedDirections = direction
    }

    fun setDisallowInterceptTouchEvent(disallow: Boolean) {
        disallowDragIntercept = disallow

        if (disallow) {
            dragHelper.cancel()
        }
    }

    fun setOnDismissListener(listener: OnDismissListener?) {
        dismissListener = listener
    }

    private fun dispatchEventToChild(event: MotionEvent) {
        val child = contentView ?: return

        if (disallowDragIntercept || hasSentCancelToChild || !dragCallback.hasDragStarted) {
            child.dispatchTouchEvent(event)
            return
        }

        hasSentCancelToChild = true

        val cancelEvent = MotionEvent.obtain(event).apply {
            action = MotionEvent.ACTION_CANCEL
        }

        child.dispatchTouchEvent(cancelEvent)
        cancelEvent.recycle()
    }

    private fun notifyDismissed() {
        dragCallback.isDismissed = false

        post {
            contentView?.let { dismissListener?.onDismissed(it) }
        }
    }

    private fun isDirectionAllowed(direction: Int): Boolean {
        return allowedDirections and direction == direction
    }

    private inner class DragCallback : ViewDragHelper.Callback() {

        private var dragDirection = 0
        private var accumulatedDx = 0
        private var accumulatedDy = 0

        var isDismissed = false
        var isDragging = false
            private set

        val hasDragStarted: Boolean
            get() = dragDirection != 0

        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            return child == contentView
        }

        override fun onViewCaptured(capturedChild: View, activePointerId: Int) {
            dragDirection = 0
            accumulatedDx = 0
            accumulatedDy = 0
            isDismissed = false
            isDragging = false
        }

        override fun clampViewPositionHorizontal(
            child: View,
            left: Int,
            dx: Int
        ): Int {
            val width = child.width

            if (dragDirection == 0) {
                accumulatedDx += dx

                if (abs(accumulatedDx) < touchSlop) {
                    return 0
                }

                val detectedDirection =
                    if (left < 0) Direction.LEFT else Direction.RIGHT

                if (!isDirectionAllowed(detectedDirection)) {
                    dragHelper.cancel()
                    return 0
                }

                dragDirection = detectedDirection
                isDragging = true
            }

            return when (dragDirection) {
                Direction.LEFT -> left.coerceIn(-width, 0)
                Direction.RIGHT -> left.coerceIn(0, width)
                else -> 0
            }
        }

        override fun clampViewPositionVertical(
            child: View,
            top: Int,
            dy: Int
        ): Int {
            val height = child.height

            if (dragDirection == 0) {
                accumulatedDy += dy

                if (abs(accumulatedDy) < touchSlop) {
                    return 0
                }

                val detectedDirection =
                    if (top < 0) Direction.UP else Direction.DOWN

                if (!isDirectionAllowed(detectedDirection)) {
                    dragHelper.cancel()
                    return 0
                }

                dragDirection = detectedDirection
                isDragging = true
            }

            return when (dragDirection) {
                Direction.UP -> top.coerceIn(-height, 0)
                Direction.DOWN -> top.coerceIn(0, height)
                else -> 0
            }
        }

        override fun onViewReleased(
            releasedChild: View,
            xvel: Float,
            yvel: Float
        ) {
            val width = releasedChild.width
            val height = releasedChild.height

            var finalLeft = 0
            var finalTop = 0

            when (dragDirection) {
                Direction.LEFT -> {
                    if (
                        releasedChild.left < 0 &&
                        xvel < 0f &&
                        (abs(releasedChild.left) > width * HORIZONTAL_DISMISS_THRESHOLD ||
                                xvel < -MIN_FLING_VELOCITY)
                    ) {
                        finalLeft = -width
                        isDismissed = true
                    }
                }

                Direction.RIGHT -> {
                    if (
                        releasedChild.left > 0 &&
                        xvel > 0f &&
                        (releasedChild.left > width * HORIZONTAL_DISMISS_THRESHOLD ||
                                xvel > MIN_FLING_VELOCITY)
                    ) {
                        finalLeft = width
                        isDismissed = true
                    }
                }

                Direction.UP -> {
                    if (
                        releasedChild.top < 0 &&
                        yvel < 0f &&
                        (abs(releasedChild.top) > height * VERTICAL_DISMISS_THRESHOLD ||
                                yvel < -MIN_FLING_VELOCITY)
                    ) {
                        finalTop = -height
                        isDismissed = true
                    }
                }

                Direction.DOWN -> {
                    if (
                        releasedChild.top > 0 &&
                        yvel > 0f &&
                        (releasedChild.top > height * VERTICAL_DISMISS_THRESHOLD ||
                                yvel > MIN_FLING_VELOCITY)
                    ) {
                        finalTop = height
                        isDismissed = true
                    }
                }
            }

            dragHelper.settleCapturedViewAt(finalLeft, finalTop)
            dragDirection = 0
            isDragging = false

            ViewCompat.postInvalidateOnAnimation(this@DragDismissLayout)
        }
    }

    private companion object {
        const val HORIZONTAL_DISMISS_THRESHOLD = 0.30f
        const val VERTICAL_DISMISS_THRESHOLD = 0.25f
        const val MIN_FLING_VELOCITY = 4000f
    }
}