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

    interface OnDragStateListener {
        fun onDragStarted()
        fun onDragProgress(progress: Float)
        fun onDragFinished(dismissed: Boolean)
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
    private var dragStateListener: OnDragStateListener? = null

    private var allowedDirections: Int = Direction.ALL
    private var disallowDragIntercept = false

    private var initialX = 0f
    private var initialY = 0f
    private var isInterceptingDrag = false
    private var dragInProgress = false
    private var dismissedDispatched = false
    private var childTouchCancelled = false

    val isDragging: Boolean
        get() = dragInProgress || dragCallback.isDragging

    override fun onFinishInflate() {
        super.onFinishInflate()
        contentView = getChildAt(0)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return runCatching {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = event.x
                    initialY = event.y
                    isInterceptingDrag = false
                    dismissedDispatched = false
                    childTouchCancelled = false
                    dragCallback.reset()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - initialX
                    val dy = event.y - initialY
                    val direction = resolveDirection(dx, dy)
                    if (
                        direction != 0 &&
                        isDirectionAllowed(direction) &&
                        isDragPastSlop(dx, dy)
                    ) {
                        isInterceptingDrag = true
                    }
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    isInterceptingDrag = false
                }
            }
            dragHelper.shouldInterceptTouchEvent(event)
            true
        }.getOrElse {
            dragHelper.cancel()
            true
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return runCatching {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                childTouchCancelled = false
            }

            if (!disallowDragIntercept) {
                dragHelper.processTouchEvent(event)
            }

            val child = contentView
            if (child != null) {
                if (disallowDragIntercept || childTouchCancelled || !dragCallback.hasActiveDragDirection()) {
                    child.dispatchTouchEvent(event)
                } else if (!childTouchCancelled) {
                    childTouchCancelled = true
                    MotionEvent.obtain(event).apply {
                        action = MotionEvent.ACTION_CANCEL
                        child.dispatchTouchEvent(this)
                        recycle()
                    }
                }
            }

            if (event.actionMasked == MotionEvent.ACTION_UP && !dragCallback.isDragging) {
                performClick()
            }

            true
        }.getOrElse {
            dragHelper.cancel()
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

        if (dragInProgress) {
            val dismissed = dragCallback.isDismissed

            dragInProgress = false
            dragCallback.isDragging = false

            dragStateListener?.onDragFinished(dismissed)

            if (dismissed && !dismissedDispatched) {
                dismissedDispatched = true
                notifyDismissed()
            } else if (!dismissed) {
                resetContentAfterCancelledDrag()
            }
        }
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Intentionally ignored. This layout only respects explicit
        // setDisallowInterceptTouchEvent(...) calls, matching the reference behavior.
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
            childTouchCancelled = false
            dragCallback.reset()
        }
    }

    fun setOnDismissListener(listener: OnDismissListener?) {
        dismissListener = listener
    }

    fun setOnDragStateListener(listener: OnDragStateListener?) {
        dragStateListener = listener
    }

    private fun notifyDismissed() {
        post {
            contentView?.let { view ->
                dismissListener?.onDismissed(view)
            }
        }
    }

    private fun resetDragState() {
        isInterceptingDrag = false
        dragInProgress = false
        dismissedDispatched = false
        dragCallback.reset()
        resetContentAfterCancelledDrag()
    }

    private fun resetContentAfterCancelledDrag() {
        // The dragged child stays fully opaque; ViewDragHelper restores position.
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

    private fun calculateDismissProgress(child: View): Float {
        val width = child.width.coerceAtLeast(1)
        val height = child.height.coerceAtLeast(1)

        val horizontalProgress = abs(child.left).toFloat() / width.toFloat()
        val verticalProgress = abs(child.top).toFloat() / height.toFloat()

        return kotlin.math.max(horizontalProgress, verticalProgress).coerceIn(0f, 1f)
    }

    private fun updateDragProgress(child: View) {
        val progress = calculateDismissProgress(child)

        dragStateListener?.onDragProgress(progress)
    }

    private fun dispatchDragStartedIfNeeded() {
        if (dragInProgress) return

        dragInProgress = true
        dragStateListener?.onDragStarted()
    }

    private inner class DragCallback : ViewDragHelper.Callback() {

        private var dragDirection = 0

        var isDismissed = false
        var isDragging = false

        fun reset() {
            dragDirection = 0
            isDismissed = false
            isDragging = false
        }

        fun hasActiveDragDirection(): Boolean {
            return dragDirection != 0
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
            dragDirection = 0
            isDismissed = false
            isDragging = true
            dismissedDispatched = false
            dispatchDragStartedIfNeeded()
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
            if (!canDragHorizontally()) return 0

            val width = child.width

            if (dragDirection == 0) {
                val detectedDirection = if (left < 0) {
                    Direction.LEFT
                } else {
                    Direction.RIGHT
                }

                if (!isDirectionAllowed(detectedDirection)) return 0

                dragDirection = detectedDirection
                dispatchDragStartedIfNeeded()
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
            if (!canDragVertically()) return 0

            val height = child.height

            if (dragDirection == 0) {
                val detectedDirection = if (top < 0) {
                    Direction.UP
                } else {
                    Direction.DOWN
                }

                if (!isDirectionAllowed(detectedDirection)) return 0

                dragDirection = detectedDirection
                dispatchDragStartedIfNeeded()
            }

            return when (dragDirection) {
                Direction.UP -> top.coerceIn(-height, 0)
                Direction.DOWN -> top.coerceIn(0, height)
                else -> 0
            }
        }

        override fun onViewPositionChanged(
            changedView: View,
            left: Int,
            top: Int,
            dx: Int,
            dy: Int
        ) {
            updateDragProgress(changedView)
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
                    if (shouldDismissLeft(releasedChild, width, xvel)) {
                        finalLeft = -width
                        isDismissed = true
                    }
                }

                Direction.RIGHT -> {
                    if (shouldDismissRight(releasedChild, width, xvel)) {
                        finalLeft = width
                        isDismissed = true
                    }
                }

                Direction.UP -> {
                    if (shouldDismissUp(releasedChild, height, yvel)) {
                        finalTop = -height
                        isDismissed = true
                    }
                }

                Direction.DOWN -> {
                    if (shouldDismissDown(releasedChild, height, yvel)) {
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

        private fun canDragHorizontally(): Boolean {
            return isDirectionAllowed(Direction.LEFT) ||
                    isDirectionAllowed(Direction.RIGHT)
        }

        private fun canDragVertically(): Boolean {
            return isDirectionAllowed(Direction.UP) ||
                    isDirectionAllowed(Direction.DOWN)
        }

        private fun shouldDismissLeft(
            child: View,
            width: Int,
            xvel: Float
        ): Boolean {
            return child.left < 0 &&
                    (
                            abs(child.left) > width * HORIZONTAL_DISMISS_THRESHOLD ||
                                    xvel < -MIN_FLING_VELOCITY
                            )
        }

        private fun shouldDismissRight(
            child: View,
            width: Int,
            xvel: Float
        ): Boolean {
            return child.left > 0 &&
                    (
                            child.left > width * HORIZONTAL_DISMISS_THRESHOLD ||
                                    xvel > MIN_FLING_VELOCITY
                            )
        }

        private fun shouldDismissUp(
            child: View,
            height: Int,
            yvel: Float
        ): Boolean {
            return child.top < 0 &&
                    (
                            abs(child.top) > height * VERTICAL_DISMISS_THRESHOLD ||
                                    yvel < -MIN_FLING_VELOCITY
                            )
        }

        private fun shouldDismissDown(
            child: View,
            height: Int,
            yvel: Float
        ): Boolean {
            return child.top > 0 &&
                    (
                            child.top > height * VERTICAL_DISMISS_THRESHOLD ||
                                    yvel > MIN_FLING_VELOCITY
                            )
        }
    }

    private companion object {
        private const val HORIZONTAL_DISMISS_THRESHOLD = 0.30f
        private const val VERTICAL_DISMISS_THRESHOLD = 0.25f
        private const val MIN_FLING_VELOCITY = 4000f
    }
}
