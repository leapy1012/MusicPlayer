package gd.app.lib.view.panel

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import androidx.customview.widget.ViewDragHelper

class PanelDragHelper private constructor(
    private val dragHelper: ViewDragHelper
) {

    abstract class Callback {
        open fun clampViewPositionHorizontal(view: View, left: Int, dx: Int): Int = 0
        abstract fun clampViewPositionVertical(view: View, top: Int, dy: Int): Int
        open fun getOrderedChildIndex(index: Int): Int = index
        open fun getViewHorizontalDragRange(view: View): Int = 0
        abstract fun getViewVerticalDragRange(view: View): Int
        open fun onEdgeDragStarted(edgeFlags: Int, pointerId: Int) = Unit
        open fun onEdgeLock(edgeFlags: Int): Boolean = false
        open fun onEdgeTouched(edgeFlags: Int, pointerId: Int) = Unit
        abstract fun onViewCaptured(view: View, activePointerId: Int)
        abstract fun onViewDragStateChanged(state: Int)
        abstract fun onViewPositionChanged(view: View, left: Int, top: Int, dx: Int, dy: Int)
        abstract fun onViewReleased(view: View, xVelocity: Float, yVelocity: Float)
        abstract fun tryCaptureView(view: View, pointerId: Int): Boolean
    }

    companion object {
        private fun Callback.asAndroidXCallback(): ViewDragHelper.Callback {
            val source = this
            return object : ViewDragHelper.Callback() {
                override fun clampViewPositionHorizontal(view: View, left: Int, dx: Int): Int =
                    source.clampViewPositionHorizontal(view, left, dx)

                override fun clampViewPositionVertical(view: View, top: Int, dy: Int): Int =
                    source.clampViewPositionVertical(view, top, dy)

                override fun getOrderedChildIndex(index: Int): Int =
                    source.getOrderedChildIndex(index)

                override fun getViewHorizontalDragRange(view: View): Int =
                    source.getViewHorizontalDragRange(view)

                override fun getViewVerticalDragRange(view: View): Int =
                    source.getViewVerticalDragRange(view)

                override fun onEdgeDragStarted(edgeFlags: Int, pointerId: Int) {
                    source.onEdgeDragStarted(edgeFlags, pointerId)
                }

                override fun onEdgeLock(edgeFlags: Int): Boolean =
                    source.onEdgeLock(edgeFlags)

                override fun onEdgeTouched(edgeFlags: Int, pointerId: Int) {
                    source.onEdgeTouched(edgeFlags, pointerId)
                }

                override fun onViewCaptured(view: View, activePointerId: Int) {
                    source.onViewCaptured(view, activePointerId)
                }

                override fun onViewDragStateChanged(state: Int) {
                    source.onViewDragStateChanged(state)
                }

                override fun onViewPositionChanged(
                    view: View,
                    left: Int,
                    top: Int,
                    dx: Int,
                    dy: Int
                ) {
                    source.onViewPositionChanged(view, left, top, dx, dy)
                }

                override fun onViewReleased(view: View, xVelocity: Float, yVelocity: Float) {
                    source.onViewReleased(view, xVelocity, yVelocity)
                }

                override fun tryCaptureView(view: View, pointerId: Int): Boolean =
                    source.tryCaptureView(view, pointerId)
            }
        }

        @JvmStatic
        fun create(
            parentView: ViewGroup,
            sensitivity: Float,
            interpolator: Interpolator?,
            callback: Callback
        ): PanelDragHelper {
            interpolator?.hashCode()
            return PanelDragHelper(
                ViewDragHelper.create(parentView, sensitivity, callback.asAndroidXCallback())
            )
        }

        @JvmStatic
        fun create(
            parentView: ViewGroup,
            interpolator: Interpolator?,
            callback: Callback
        ): PanelDragHelper {
            interpolator?.hashCode()
            return PanelDragHelper(
                ViewDragHelper.create(parentView, callback.asAndroidXCallback())
            )
        }
    }

    fun processTouchEvent(event: MotionEvent) {
        dragHelper.processTouchEvent(event)
    }

    fun settleCapturedViewAt(left: Int, top: Int): Boolean =
        dragHelper.settleCapturedViewAt(left, top)

    fun shouldInterceptTouchEvent(event: MotionEvent): Boolean =
        dragHelper.shouldInterceptTouchEvent(event)

    fun abort() {
        dragHelper.abort()
    }

    fun cancel() {
        dragHelper.cancel()
    }

    fun captureChildView(view: View, pointerId: Int) {
        dragHelper.captureChildView(view, pointerId)
    }

    fun clearIfIdle(state: Int) {
        if (state == ViewDragHelper.STATE_IDLE) {
            dragHelper.cancel()
        }
    }

    fun setMinVelocity(minVelocity: Float) {
        dragHelper.minVelocity = minVelocity
    }

    fun continueSettling(continuePosting: Boolean): Boolean =
        dragHelper.continueSettling(continuePosting)

    fun getEdgeSize(): Int = dragHelper.edgeSize

    fun getTouchSlop(): Int = dragHelper.touchSlop

    fun isEdgeTouched(x: Int, y: Int): Boolean =
        dragHelper.isEdgeTouched(x, y)

    fun isDragging(): Boolean = dragHelper.viewDragState == ViewDragHelper.STATE_DRAGGING

    fun isViewUnder(view: View?, x: Int, y: Int): Boolean =
        view != null && dragHelper.isViewUnder(view, x, y)

    fun smoothSlideViewTo(view: View, left: Int, top: Int): Boolean =
        dragHelper.smoothSlideViewTo(view, left, top)
}
