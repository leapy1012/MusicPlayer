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

    private var initialX = 0f
    private var initialY = 0f
    private var isInterceptingDrag = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        contentView = getChildAt(0)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (disallowDragIntercept) {
            dragHelper.cancel()
            return false
        }

        return try {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = event.x
                    initialY = event.y
                    isInterceptingDrag = false
                    dragCallback.reset()

                    dragHelper.processTouchEvent(event)

                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - initialX
                    val dy = event.y - initialY

                    val direction = resolveDirection(dx, dy)

                    if (direction != 0 &&
                        isDirectionAllowed(direction) &&
                        isDragPastSlop(dx, dy)
                    ) {
                        isInterceptingDrag = true
                    }

                    isInterceptingDrag && dragHelper.shouldInterceptTouchEvent(event)
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    isInterceptingDrag = false
                    dragHelper.cancel()
                    false
                }

                else -> {
                    dragHelper.shouldInterceptTouchEvent(event)
                }
            }
        } catch (_: Throwable) {
            dragHelper.cancel()
            false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (disallowDragIntercept) {
            return false
        }

        return try {
            dragHelper.processTouchEvent(event)

            if (event.actionMasked == MotionEvent.ACTION_UP &&
                !dragCallback.isDragging
            ) {
                performClick()
            }

            true
        } catch (_: Throwable) {
            false
        }
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun computeScroll() {
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this)
            return
        }

        if (dragCallback.isDismissed) {
            notifyDismissed()
        }
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        disallowDragIntercept = disallowIntercept

        if (disallowIntercept) {
            dragHelper.cancel()
        }

        super.requestDisallowInterceptTouchEvent(disallowIntercept)
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

    private fun notifyDismissed() {
        dragCallback.isDismissed = false

        post {
            contentView?.let { view ->
                dismissListener?.onDismissed(view)
            }
        }
    }

    private fun resolveDirection(
        dx: Float,
        dy: Float
    ): Int {
        if (abs(dx) < touchSlop && abs(dy) < touchSlop) {
            return 0
        }

        return if (abs(dx) > abs(dy)) {
            if (dx < 0f) Direction.LEFT else Direction.RIGHT
        } else {
            if (dy < 0f) Direction.UP else Direction.DOWN
        }
    }

    private fun isDragPastSlop(
        dx: Float,
        dy: Float
    ): Boolean {
        return abs(dx) > touchSlop || abs(dy) > touchSlop
    }

    private fun isDirectionAllowed(direction: Int): Boolean {
        return allowedDirections and direction == direction
    }

    private inner class DragCallback : ViewDragHelper.Callback() {

        private var dragDirection = 0

        var isDismissed = false

        var isDragging = false
            private set

        fun reset() {
            dragDirection = 0
            isDismissed = false
            isDragging = false
        }

        override fun tryCaptureView(
            child: View,
            pointerId: Int
        ): Boolean {
            return child == contentView
        }

        override fun onViewCaptured(
            capturedChild: View,
            activePointerId: Int
        ) {
            reset()
        }

        override fun getViewHorizontalDragRange(child: View): Int {
            return if (
                isDirectionAllowed(Direction.LEFT) ||
                isDirectionAllowed(Direction.RIGHT)
            ) {
                child.width
            } else {
                0
            }
        }

        override fun getViewVerticalDragRange(child: View): Int {
            return if (
                isDirectionAllowed(Direction.UP) ||
                isDirectionAllowed(Direction.DOWN)
            ) {
                child.height
            } else {
                0
            }
        }

        override fun clampViewPositionHorizontal(
            child: View,
            left: Int,
            dx: Int
        ): Int {
            val width = child.width

            if (dragDirection == 0) {
                val detectedDirection =
                    if (left < 0) Direction.LEFT else Direction.RIGHT

                if (!isDirectionAllowed(detectedDirection)) {
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
                val detectedDirection =
                    if (top < 0) Direction.UP else Direction.DOWN

                if (!isDirectionAllowed(detectedDirection)) {
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
                        (
                                abs(releasedChild.left) > width * HORIZONTAL_DISMISS_THRESHOLD ||
                                        xvel < -MIN_FLING_VELOCITY
                                )
                    ) {
                        finalLeft = -width
                        isDismissed = true
                    }
                }

                Direction.RIGHT -> {
                    if (
                        releasedChild.left > 0 &&
                        (
                                releasedChild.left > width * HORIZONTAL_DISMISS_THRESHOLD ||
                                        xvel > MIN_FLING_VELOCITY
                                )
                    ) {
                        finalLeft = width
                        isDismissed = true
                    }
                }

                Direction.UP -> {
                    if (
                        releasedChild.top < 0 &&
                        (
                                abs(releasedChild.top) > height * VERTICAL_DISMISS_THRESHOLD ||
                                        yvel < -MIN_FLING_VELOCITY
                                )
                    ) {
                        finalTop = -height
                        isDismissed = true
                    }
                }

                Direction.DOWN -> {
                    if (
                        releasedChild.top > 0 &&
                        (
                                releasedChild.top > height * VERTICAL_DISMISS_THRESHOLD ||
                                        yvel > MIN_FLING_VELOCITY
                                )
                    ) {
                        finalTop = height
                        isDismissed = true
                    }
                }
            }

            dragHelper.settleCapturedViewAt(
                finalLeft,
                finalTop
            )

            dragDirection = 0
            isDragging = false

            ViewCompat.postInvalidateOnAnimation(this@DragDismissLayout)
        }
    }

    private companion object {
        private const val HORIZONTAL_DISMISS_THRESHOLD = 0.30f
        private const val VERTICAL_DISMISS_THRESHOLD = 0.25f
        private const val MIN_FLING_VELOCITY = 4000f
    }
}