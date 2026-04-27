package gd.app.lib.view.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import gd.app.lib.R
import kotlin.math.abs

class SlidingBannerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    interface OnBannerSlideListener {
        fun onBannerSlide(layout: SlidingBannerLayout, slideOffset: Float)
        fun onBannerExpandedChanged(layout: SlidingBannerLayout, expanded: Boolean)
    }

    private val clipBounds = Rect()
    private val dragHelper = ViewDragHelper.create(this, 0.5f, BannerDragCallback())

    private val peekHeightPx: Int
    private val expandedHeightPx: Int
    private val bottomEdgeTouchHeightPx: Int

    private var primaryContentView: View? = null
    private var bannerView: View? = null
    private var isBannerEnabled = true
    private var isBannerExpanded = false
    private var downX = 0f
    private var downY = 0f
    private var isVerticalGesture = true
    private var startedFromBottomEdge = false
    private var isNavigationEdgeTouchable = false
    private var bannerSlideListener: OnBannerSlideListener? = null

    init {
        val density = resources.displayMetrics.density
        var peekHeight = (64f * density + 0.5f).toInt()
        var expandedHeight = (236f * density + 0.5f).toInt()
        if (attrs != null) {
            val attributes = context.obtainStyledAttributes(attrs, R.styleable.SlidingBannerLayout)
            try {
                peekHeight = attributes.getDimensionPixelSize(R.styleable.SlidingBannerLayout_bannerPeekHeight, peekHeight)
                expandedHeight =
                    attributes.getDimensionPixelSize(R.styleable.SlidingBannerLayout_bannerExpandedHeight, expandedHeight)
            } finally {
                attributes.recycle()
            }
        }
        peekHeightPx = peekHeight
        expandedHeightPx = expandedHeight
        bottomEdgeTouchHeightPx = (16f * density + 0.5f).toInt()
    }

    private inner class BannerDragCallback : ViewDragHelper.Callback() {
        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int = child.left

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            val expandedTop = height - expandedHeightPx
            val collapsedTop = height - peekHeightPx
            return top.coerceIn(expandedTop, collapsedTop)
        }

        override fun getViewVerticalDragRange(child: View): Int = expandedHeightPx - peekHeightPx

        override fun onViewPositionChanged(
            changedView: View,
            left: Int,
            top: Int,
            dx: Int,
            dy: Int
        ) {
            val expandedTop = height - expandedHeightPx
            val collapsedTop = height - peekHeightPx
            val range = (collapsedTop - expandedTop).coerceAtLeast(1)
            val slideOffset = 1f - ((top - expandedTop).toFloat() / range)
            applyBannerSlideOffset(slideOffset.coerceIn(0f, 1f))
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val expandedTop = height - expandedHeightPx
            val collapsedTop = height - peekHeightPx
            val snapThresholdTop = (collapsedTop + expandedTop) / 2
            val shouldExpand = if (releasedChild.top <= snapThresholdTop) yvel <= 2000f else yvel < -2000f
            val expandedChanged = isBannerExpanded != shouldExpand
            isBannerExpanded = shouldExpand
            dragHelper.settleCapturedViewAt(releasedChild.left, if (shouldExpand) expandedTop else collapsedTop)
            if (expandedChanged) {
                bannerSlideListener?.onBannerExpandedChanged(this@SlidingBannerLayout, shouldExpand)
            }
            ViewCompat.postInvalidateOnAnimation(this@SlidingBannerLayout)
        }

        override fun tryCaptureView(child: View, pointerId: Int): Boolean = child === bannerView
    }

    private fun collapseBanner() {
        val currentBanner = bannerView ?: return
        if (height <= 0) return
        val collapsedTop = height - peekHeightPx
        if (dragHelper.smoothSlideViewTo(currentBanner, currentBanner.left, collapsedTop)) {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    private fun applyBannerSlideOffset(slideOffset: Float) {
        val mainContent = primaryContentView ?: return
        val slidingBanner = bannerView ?: return

        val mainLayoutParams = mainContent.layoutParams as FrameLayout.LayoutParams
        mainLayoutParams.width = LayoutParams.MATCH_PARENT
        mainLayoutParams.height = LayoutParams.MATCH_PARENT
        mainLayoutParams.bottomMargin = if (isBannerEnabled) peekHeightPx else 0
        mainContent.layoutParams = mainLayoutParams

        val bannerLayoutParams = slidingBanner.layoutParams as FrameLayout.LayoutParams
        bannerLayoutParams.width = LayoutParams.MATCH_PARENT
        bannerLayoutParams.height = if (isBannerEnabled) expandedHeightPx else 0
        bannerLayoutParams.gravity = android.view.Gravity.BOTTOM
        bannerLayoutParams.bottomMargin = if (isBannerEnabled) {
            (peekHeightPx + ((expandedHeightPx - peekHeightPx) * slideOffset).toInt()) - expandedHeightPx
        } else {
            0
        }
        slidingBanner.layoutParams = bannerLayoutParams
        bannerSlideListener?.onBannerSlide(this, slideOffset)
    }

    override fun computeScroll() {
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        val saveCount = canvas.save()
        val currentBanner = bannerView
        if (currentBanner != null && currentBanner !== child) {
            canvas.getClipBounds(clipBounds)
            clipBounds.bottom = minOf(clipBounds.bottom, currentBanner.top)
            canvas.clipRect(clipBounds)
        }
        val didDraw = super.drawChild(canvas, child, drawingTime)
        canvas.restoreToCount(saveCount)
        return didDraw
    }

    fun i(): Boolean {
        if (!isBannerExpanded) return false
        isBannerExpanded = false
        collapseBanner()
        return true
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        primaryContentView = getChildAt(0)
        bannerView = getChildAt(1)
        applyBannerSlideOffset(if (isBannerExpanded) 1.0f else 0.0f)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragHelper.cancel()
                val touchX = event.x.toInt()
                val touchY = event.y.toInt()
                if (bannerView?.let { dragHelper.isViewUnder(it, touchX, touchY) } == true) {
                    downX = touchX.toFloat()
                    downY = touchY.toFloat()
                } else {
                    downX = 0f
                    downY = 0f
                }
                isVerticalGesture = true
                startedFromBottomEdge = touchY > height - bottomEdgeTouchHeightPx
            }

            MotionEvent.ACTION_MOVE -> {
                if (downX != 0f || downY != 0f) {
                    val horizontalDistance = abs(event.x - downX)
                    val verticalDistance = abs(event.y - downY)
                    if (horizontalDistance > dragHelper.touchSlop || verticalDistance > dragHelper.touchSlop) {
                        isVerticalGesture = horizontalDistance < verticalDistance
                        downX = 0f
                        downY = 0f
                    }
                }
            }
        }

        return (isNavigationEdgeTouchable || !startedFromBottomEdge) &&
            isVerticalGesture &&
            dragHelper.shouldInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if ((!isNavigationEdgeTouchable && startedFromBottomEdge) || !isVerticalGesture) {
            return super.onTouchEvent(event)
        }
        dragHelper.processTouchEvent(event)
        return true
    }

    fun setNavigationEdgeTouchable(touchable: Boolean) {
        isNavigationEdgeTouchable = touchable
    }

    fun setOnBannerSlideListener(listener: OnBannerSlideListener?) {
        bannerSlideListener = listener
    }

    fun setShowBanner(showBanner: Boolean) {
        if (isBannerEnabled != showBanner) {
            isBannerEnabled = showBanner
            applyBannerSlideOffset(if (isBannerExpanded) 1.0f else 0.0f)
        }
    }
}
