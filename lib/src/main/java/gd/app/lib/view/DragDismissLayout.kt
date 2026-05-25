package gd.app.lib.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.math.MathUtils
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import kotlin.math.abs

class DragDismissLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    fun interface OnDismissListener {
        fun onDismissed(view: View?)
    }

    interface OnDragStateListener {
        fun onDragStarted()
        fun onDragProgress(progress: Float)
        fun onDragFinished(dismissed: Boolean)
    }

    private val touchSlop: Int =
        ViewConfiguration.get(context).scaledTouchSlop

    private val dragCallback = DragCallback()
    private val dragHelper: ViewDragHelper =
        ViewDragHelper.create(this, dragCallback)

    private var dismissListener: OnDismissListener? = null
    private var dragStateListener: OnDragStateListener? = null

    private var dismissView: View? = null

    private var allowedDragDirections: Int = DIRECTION_HORIZONTAL
    private var disallowDragIntercept: Boolean = false
    private var sentCancelToChild: Boolean = false
    private var dragInProgress: Boolean = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        dismissView = getChildAt(0)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return runCatching {
            dragHelper.shouldInterceptTouchEvent(event)
            true
        }.getOrElse {
            dragHelper.cancel()
            true
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        runCatching {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                sentCancelToChild = false
            }

            if (!disallowDragIntercept) {
                dragHelper.processTouchEvent(event)
            }

            dispatchTouchToChildIfNeeded(event)
        }.onFailure { throwable ->
            throwable.printStackTrace()
        }

        return true
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Intentionally ignored. This layout owns the drag gesture.
    }

    override fun computeScroll() {
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this)
            return
        }

        if (dragInProgress) {
            dragInProgress = false
            dragStateListener?.onDragFinished(dragCallback.dismissPending)
        }

        if (dragCallback.dismissPending) {
            completeDismiss()
        }
    }

    fun setAllowedDragDirections(directions: Int) {
        allowedDragDirections = directions
    }

    fun setDisallowDragIntercept(disallow: Boolean) {
        disallowDragIntercept = disallow

        if (disallow) {
            dragHelper.cancel()
        }
    }

    fun setOnDismissListener(listener: OnDismissListener?) {
        dismissListener = listener
    }

    fun setOnDragStateListener(listener: OnDragStateListener?) {
        dragStateListener = listener
    }

    private fun dispatchTouchToChildIfNeeded(event: MotionEvent) {
        val child = dismissView ?: return

        val shouldPassThroughNormally =
            disallowDragIntercept ||
                    sentCancelToChild ||
                    !dragCallback.isDragging

        if (shouldPassThroughNormally) {
            child.dispatchTouchEvent(event)
            return
        }

        sentCancelToChild = true

        val cancelEvent = MotionEvent.obtain(event).apply {
            action = MotionEvent.ACTION_CANCEL
        }

        child.dispatchTouchEvent(cancelEvent)
        cancelEvent.recycle()
    }

    private fun calculateDragProgress(child: View): Float {
        val width = child.width.coerceAtLeast(1)
        val height = child.height.coerceAtLeast(1)

        val horizontal = abs(child.left).toFloat() / width.toFloat()
        val vertical = abs(child.top).toFloat() / height.toFloat()
        return horizontal.coerceAtLeast(vertical).coerceIn(0f, 1f)
    }

    private fun completeDismiss() {
        dragCallback.dismissPending = false

        post {
            dismissListener?.onDismissed(dismissView)
        }
    }

    private inner class DragCallback : ViewDragHelper.Callback() {

        private var activeDirection: Int = DIRECTION_NONE
        private var accumulatedDx: Int = 0
        private var accumulatedDy: Int = 0

        var dismissPending: Boolean = false

        val isDragging: Boolean
            get() = activeDirection != DIRECTION_NONE

        override fun tryCaptureView(
            child: View,
            pointerId: Int
        ): Boolean {
            return child == dismissView
        }

        override fun onViewCaptured(
            capturedChild: View,
            activePointerId: Int
        ) {
            dismissPending = false
            activeDirection = DIRECTION_NONE
            accumulatedDx = 0
            accumulatedDy = 0
            if (!dragInProgress) {
                dragInProgress = true
                dragStateListener?.onDragStarted()
            }
        }

        override fun clampViewPositionHorizontal(
            child: View,
            left: Int,
            dx: Int
        ): Int {
            val width = child.width

            if (activeDirection == DIRECTION_NONE) {
                accumulatedDx += dx

                if (abs(accumulatedDx) < touchSlop) {
                    return 0
                }

                val direction = if (left < 0) {
                    DIRECTION_LEFT
                } else {
                    DIRECTION_RIGHT
                }

                if (!allowedDragDirections.hasDirection(direction)) {
                    return 0
                }

                activeDirection = direction
            }

            return when (activeDirection) {
                DIRECTION_LEFT -> left.coerceIn(-width, 0)
                DIRECTION_RIGHT -> left.coerceIn(0, width)
                else -> 0
            }
        }

        override fun clampViewPositionVertical(
            child: View,
            top: Int,
            dy: Int
        ): Int {
            val height = child.height

            if (activeDirection == DIRECTION_NONE) {
                accumulatedDy += dy

                if (abs(accumulatedDy) < touchSlop) {
                    return 0
                }

                val direction = if (top < 0) {
                    DIRECTION_UP
                } else {
                    DIRECTION_DOWN
                }

                if (!allowedDragDirections.hasDirection(direction)) {
                    return 0
                }

                activeDirection = direction
            }

            return when (activeDirection) {
                DIRECTION_UP -> top.coerceIn(-height, 0)
                DIRECTION_DOWN -> top.coerceIn(0, height)
                else -> 0
            }
        }

        override fun onViewReleased(
            releasedChild: View,
            xVelocity: Float,
            yVelocity: Float
        ) {
            val target = resolveReleaseTarget(
                child = releasedChild,
                xVelocity = xVelocity,
                yVelocity = yVelocity
            )

            dismissPending = target.shouldDismiss

            dragHelper.settleCapturedViewAt(
                target.left,
                target.top
            )

            activeDirection = DIRECTION_NONE
            ViewCompat.postInvalidateOnAnimation(this@DragDismissLayout)
        }

        override fun onViewPositionChanged(
            changedView: View,
            left: Int,
            top: Int,
            dx: Int,
            dy: Int
        ) {
            dragStateListener?.onDragProgress(calculateDragProgress(changedView))
        }

        private fun resolveReleaseTarget(
            child: View,
            xVelocity: Float,
            yVelocity: Float
        ): ReleaseTarget {
            val left = child.left
            val top = child.top
            val width = child.width
            val height = child.height

            return when (activeDirection) {
                DIRECTION_LEFT -> {
                    val shouldDismiss =
                        left < 0 &&
                                xVelocity < 0f &&
                                (-left > width * HORIZONTAL_DISMISS_THRESHOLD ||
                                        xVelocity < -DISMISS_VELOCITY_THRESHOLD)

                    if (shouldDismiss) {
                        ReleaseTarget(
                            left = -width,
                            top = 0,
                            shouldDismiss = true
                        )
                    } else {
                        ReleaseTarget.Idle
                    }
                }

                DIRECTION_RIGHT -> {
                    val shouldDismiss =
                        left > 0 &&
                                xVelocity > 0f &&
                                (left > width * HORIZONTAL_DISMISS_THRESHOLD ||
                                        xVelocity > DISMISS_VELOCITY_THRESHOLD)

                    if (shouldDismiss) {
                        ReleaseTarget(
                            left = width,
                            top = 0,
                            shouldDismiss = true
                        )
                    } else {
                        ReleaseTarget.Idle
                    }
                }

                DIRECTION_UP -> {
                    val shouldDismiss =
                        top < 0 &&
                                yVelocity < 0f &&
                                (-top > height * VERTICAL_DISMISS_THRESHOLD ||
                                        yVelocity < -DISMISS_VELOCITY_THRESHOLD)

                    if (shouldDismiss) {
                        ReleaseTarget(
                            left = 0,
                            top = -height,
                            shouldDismiss = true
                        )
                    } else {
                        ReleaseTarget.Idle
                    }
                }

                DIRECTION_DOWN -> {
                    val shouldDismiss =
                        top > 0 &&
                                yVelocity > 0f &&
                                (top > height * VERTICAL_DISMISS_THRESHOLD ||
                                        yVelocity > DISMISS_VELOCITY_THRESHOLD)

                    if (shouldDismiss) {
                        ReleaseTarget(
                            left = 0,
                            top = height,
                            shouldDismiss = true
                        )
                    } else {
                        ReleaseTarget.Idle
                    }
                }

                else -> ReleaseTarget.Idle
            }
        }
    }

    private data class ReleaseTarget(
        val left: Int,
        val top: Int,
        val shouldDismiss: Boolean
    ) {
        companion object {
            val Idle = ReleaseTarget(
                left = 0,
                top = 0,
                shouldDismiss = false
            )
        }
    }

    companion object {
        private const val TAG = "DragDismissLayout"

        const val DIRECTION_NONE = 0
        const val DIRECTION_LEFT = 1
        const val DIRECTION_RIGHT = 1 shl 1
        const val DIRECTION_UP = 1 shl 2
        const val DIRECTION_DOWN = 1 shl 3

        const val DIRECTION_HORIZONTAL = DIRECTION_LEFT or DIRECTION_RIGHT
        const val DIRECTION_VERTICAL = DIRECTION_UP or DIRECTION_DOWN
        const val DIRECTION_ALL = DIRECTION_HORIZONTAL or DIRECTION_VERTICAL

        private const val HORIZONTAL_DISMISS_THRESHOLD = 0.30f
        private const val VERTICAL_DISMISS_THRESHOLD = 0.25f
        private const val DISMISS_VELOCITY_THRESHOLD = 4_000f

        private fun Int.hasDirection(direction: Int): Boolean {
            return this and direction == direction
        }
    }
}
