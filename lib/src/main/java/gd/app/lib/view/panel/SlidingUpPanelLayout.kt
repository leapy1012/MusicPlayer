package gd.app.lib.view.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import gd.app.lib.R
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

class SlidingUpPanelLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    interface PanelSlideListener {
        fun onPanelSlide(view: View, slideOffset: Float)
        fun onPanelStateChanged(view: View, previousState: PanelState, newState: PanelState)
    }

    enum class PanelState {
        EXPANDED,
        COLLAPSED,
        ANCHORED,
        HIDDEN,
        DRAGGING
    }

    class LayoutParams : MarginLayoutParams {
        var layoutWeight: Float = 0f

        constructor() : super(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        constructor(source: ViewGroup.LayoutParams?) : super(source)
        constructor(source: MarginLayoutParams?) : super(source)
        constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
            val attributes = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.layout_weight))
            try {
                layoutWeight = attributes.getFloat(0, 0f)
            } finally {
                attributes.recycle()
            }
        }
    }

    companion object {
        private const val TAG = "SlidingUpPanelLayout"
        private var DEFAULT_PANEL_STATE: PanelState = PanelState.COLLAPSED
    }

    private val coveredFadePaint = Paint()
    private val temporaryClipRect = Rect()
    private val panelListeners = CopyOnWriteArrayList<PanelSlideListener>()

    private var minimumFlingVelocity = 400
    private var coveredFadeColor = 0x99000000.toInt()
    private var shadowDrawable: Drawable? = null
    private var panelHeightPx = -1
    private var shadowHeightPx = -1
    private var parallaxOffsetPx = -1
    private var isUsingBottomGravity = true
    private var isOverlayed = false
    private var isClipPanel = true
    private var dragViewId = View.NO_ID
    private var scrollableViewId = View.NO_ID

    private var dragView: View? = null
    private var scrollableView: View? = null
    private var scrollableViewHelper: ScrollableViewHelper = ScrollableViewHelper()
    private var mainContentView: View? = null
    private var slideableView: View? = null

    private var panelState: PanelState = DEFAULT_PANEL_STATE
    private var lastNotDraggingState: PanelState = DEFAULT_PANEL_STATE

    private var maxSlideOffset = 1.0f
    private var anchorPoint = 1.0f
    private var slideOffset = 0.0f
    private var slideRangePx = 0

    private var isUnableToDrag = false
    private var isTouchEnabled = true
    private var initialLayoutPending = true
    private var isScrollableViewConsumingTouch = false

    private var downX = 0f
    private var downY = 0f
    private var lastMotionX = 0f
    private var lastMotionY = 0f

    private var fadeClickListener: View.OnClickListener? = null
    private var dragViewClickListener: View.OnClickListener? = null
    private val dragHelper: ViewDragHelper

    init {
        var dragInterpolator: Interpolator? = null
        if (attrs != null) {
            val gravityAttributes = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.gravity))
            try {
                setGravity(gravityAttributes.getInt(0, Gravity.BOTTOM))
            } finally {
                gravityAttributes.recycle()
            }

            val panelAttributes = context.obtainStyledAttributes(attrs, R.styleable.SlidingUpPanelLayout)
            try {
                anchorPoint =
                    panelAttributes.getFloat(R.styleable.SlidingUpPanelLayout_umanoAnchorPoint, 1.0f)
                isClipPanel =
                    panelAttributes.getBoolean(R.styleable.SlidingUpPanelLayout_umanoClipPanel, true)
                dragViewId =
                    panelAttributes.getResourceId(R.styleable.SlidingUpPanelLayout_umanoDragView, View.NO_ID)
                coveredFadeColor =
                    panelAttributes.getColor(R.styleable.SlidingUpPanelLayout_umanoFadeColor, coveredFadeColor)
                minimumFlingVelocity =
                    panelAttributes.getInt(R.styleable.SlidingUpPanelLayout_umanoFlingVelocity, minimumFlingVelocity)
                val stateIndex =
                    panelAttributes.getInt(
                        R.styleable.SlidingUpPanelLayout_umanoInitialState,
                        DEFAULT_PANEL_STATE.ordinal
                    )
                panelState = PanelState.entries.toTypedArray().getOrElse(stateIndex) { DEFAULT_PANEL_STATE }
                isOverlayed =
                    panelAttributes.getBoolean(R.styleable.SlidingUpPanelLayout_umanoOverlay, false)
                panelHeightPx =
                    panelAttributes.getDimensionPixelSize(R.styleable.SlidingUpPanelLayout_umanoPanelHeight, -1)
                parallaxOffsetPx =
                    panelAttributes.getDimensionPixelSize(R.styleable.SlidingUpPanelLayout_umanoParallaxOffset, -1)
                val interpolatorResId =
                    panelAttributes.getResourceId(R.styleable.SlidingUpPanelLayout_umanoScrollInterpolator, View.NO_ID)
                scrollableViewId =
                    panelAttributes.getResourceId(R.styleable.SlidingUpPanelLayout_umanoScrollableView, View.NO_ID)
                shadowHeightPx =
                    panelAttributes.getDimensionPixelSize(R.styleable.SlidingUpPanelLayout_umanoShadowHeight, -1)
                if (interpolatorResId != View.NO_ID) {
                    dragInterpolator = AnimationUtils.loadInterpolator(context, interpolatorResId)
                }
            } finally {
                panelAttributes.recycle()
            }
        }

        val density = resources.displayMetrics.density
        if (panelHeightPx == -1) panelHeightPx = (68f * density + 0.5f).toInt()
        if (shadowHeightPx == -1) shadowHeightPx = (4f * density + 0.5f).toInt()
        if (parallaxOffsetPx == -1) parallaxOffsetPx = 0

        shadowDrawable = when {
            shadowHeightPx <= 0 -> null
            isUsingBottomGravity -> ContextCompat.getDrawable(context, R.drawable.above_shadow)
            else -> ContextCompat.getDrawable(context, R.drawable.below_shadow)
        }

        setWillNotDraw(false)
        dragHelper = ViewDragHelper.create(this, 0.5f, DragCallback(dragInterpolator)).apply {
            minVelocity = minimumFlingVelocity * density
        }
    }

    private inner class DragCallback(private val dragInterpolator: Interpolator?) : ViewDragHelper.Callback() {
        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int = child.left

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            val collapsedTop = computePanelTopPosition(0f)
            val expandedTop = computePanelTopPosition(maxSlideOffset)
            return if (isUsingBottomGravity) top.coerceIn(expandedTop, collapsedTop)
            else top.coerceIn(collapsedTop, expandedTop)
        }

        override fun getViewVerticalDragRange(child: View): Int = slideRangePx

        override fun onViewCaptured(capturedChild: View, activePointerId: Int) {
            makeAllChildrenVisible()
        }

        override fun onViewDragStateChanged(state: Int) {
            if (dragHelper.viewDragState != ViewDragHelper.STATE_IDLE) return
            slideOffset = computeSlideOffset(slideableView?.top ?: return)
            applyParallaxOffset()
            when {
                slideOffset >= maxSlideOffset -> {
                    updateObscuredViewVisibility()
                    setPanelStateInternal(PanelState.EXPANDED)
                }
                slideOffset <= 0.0f -> setPanelStateInternal(PanelState.COLLAPSED)
                slideOffset < 0.0f -> {
                    setPanelStateInternal(PanelState.HIDDEN)
                    slideableView?.visibility = View.INVISIBLE
                }
                else -> {
                    updateObscuredViewVisibility()
                    setPanelStateInternal(PanelState.ANCHORED)
                }
            }
        }

        override fun onViewPositionChanged(
            changedView: View,
            left: Int,
            top: Int,
            dx: Int,
            dy: Int
        ) {
            onPanelDragged(top)
            invalidate()
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val adjustedYVelocity = if (isUsingBottomGravity) -yvel else yvel
            val targetTop = when {
                adjustedYVelocity > 0f && slideOffset <= anchorPoint -> computePanelTopPosition(anchorPoint)
                adjustedYVelocity > 0f && slideOffset > anchorPoint -> computePanelTopPosition(maxSlideOffset)
                adjustedYVelocity < 0f && slideOffset >= anchorPoint -> computePanelTopPosition(anchorPoint)
                adjustedYVelocity < 0f && slideOffset < anchorPoint -> computePanelTopPosition(0f)
                slideOffset >= (anchorPoint + 1.0f) / 2.0f -> computePanelTopPosition(maxSlideOffset)
                slideOffset >= anchorPoint / 2.0f -> computePanelTopPosition(anchorPoint)
                else -> computePanelTopPosition(0f)
            }
            dragHelper.settleCapturedViewAt(releasedChild.left, targetTop)
            invalidate()
        }

        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            return !isUnableToDrag && child === slideableView
        }
    }

    private fun computePanelTopPosition(targetOffset: Float): Int {
        val panel = slideableView
        val slidePixels = (targetOffset * slideRangePx).toInt()
        return if (isUsingBottomGravity) {
            (measuredHeight - paddingBottom - panelHeightPx) - slidePixels
        } else {
            (paddingTop - (panel?.measuredHeight ?: 0)) + panelHeightPx + slidePixels
        }
    }

    private fun computeSlideOffset(panelTop: Int): Float {
        val collapsedTop = computePanelTopPosition(0f)
        val rawOffset = if (isUsingBottomGravity) {
            (collapsedTop - panelTop).toFloat() / slideRangePx
        } else {
            (panelTop - collapsedTop).toFloat() / slideRangePx
        }
        return rawOffset.coerceIn(-1.0f, 1.0f)
    }

    private fun onPanelDragged(panelTop: Int) {
        if (panelState != PanelState.DRAGGING) {
            lastNotDraggingState = panelState
        }
        setPanelStateInternal(PanelState.DRAGGING)
        slideOffset = computeSlideOffset(panelTop)
        applyParallaxOffset()
        dispatchOnPanelSlide(slideableView)

        val contentView = mainContentView ?: return
        val panelView = slideableView ?: return
        val layoutParams = contentView.layoutParams as LayoutParams
        val availableHeight = (height - paddingBottom) - paddingTop
        val fullyVisibleContentHeight = availableHeight - if (slideOffset < 0f) 0 else panelHeightPx

        if (slideOffset > 0f || isOverlayed) {
            if (layoutParams.height != ViewGroup.LayoutParams.MATCH_PARENT || isOverlayed) {
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                contentView.requestLayout()
            }
            return
        }

        val contentHeight = if (isUsingBottomGravity) {
            panelTop - paddingBottom
        } else {
            ((height - paddingBottom) - panelView.measuredHeight) - panelTop
        }
        layoutParams.height =
            if (contentHeight == fullyVisibleContentHeight) ViewGroup.LayoutParams.MATCH_PARENT else contentHeight
        contentView.requestLayout()
    }

    private fun applyParallaxOffset() {
        if (parallaxOffsetPx > 0) {
            mainContentView?.translationY = currentParallaxOffset.toFloat()
        }
    }

    private fun makeAllChildrenVisible() {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.INVISIBLE) {
                child.visibility = View.VISIBLE
            }
        }
    }

    private fun smoothSlideTo(targetOffset: Float): Boolean {
        val panel = slideableView ?: return false
        val targetTop = computePanelTopPosition(targetOffset)
        if (dragHelper.smoothSlideViewTo(panel, panel.left, targetTop)) {
            makeAllChildrenVisible()
            ViewCompat.postInvalidateOnAnimation(this)
            return true
        }
        return false
    }

    private fun updateObscuredViewVisibility() {
        if (childCount == 0) return
        val content = getChildAt(0)
        val panel = slideableView
        val panelLeft: Int
        val panelRight: Int
        val panelTop: Int
        val panelBottom: Int
        if (panel != null && hasOpaqueBackground(panel)) {
            panelLeft = panel.left
            panelRight = panel.right
            panelTop = panel.top
            panelBottom = panel.bottom
        } else {
            panelLeft = 0
            panelRight = 0
            panelTop = 0
            panelBottom = 0
        }
        val visibleLeft = maxOf(paddingLeft, content.left)
        val visibleTop = maxOf(paddingTop, content.top)
        val visibleRight = minOf(width - paddingRight, content.right)
        val visibleBottom = minOf(height - paddingBottom, content.bottom)
        content.visibility =
            if (visibleLeft >= panelLeft &&
                visibleTop >= panelTop &&
                visibleRight <= panelRight &&
                visibleBottom <= panelBottom
            ) {
                View.INVISIBLE
            } else {
                View.VISIBLE
            }
    }

    private fun hasOpaqueBackground(view: View): Boolean {
        val background = view.background
        return background != null && background.opacity == android.graphics.PixelFormat.OPAQUE
    }

    private fun isViewUnder(view: View?, localX: Int, localY: Int): Boolean {
        if (view == null) return false
        return localX >= view.left &&
            localX < view.right &&
            localY >= view.top &&
            localY < view.bottom
    }

    private fun canScrollableViewDragPanel(directionTowardExpand: Boolean): Boolean {
        val view = scrollableView ?: return false
        val isSlidingUp = if (directionTowardExpand) isUsingBottomGravity else !isUsingBottomGravity
        return scrollableViewHelper.getScrollableViewScrollPosition(view, isSlidingUp) > 0
    }

    private fun setPanelStateInternal(newState: PanelState) {
        val previousState = panelState
        if (previousState == newState) return
        panelState = newState
        dispatchOnPanelStateChanged(previousState, newState)
    }

    private fun dispatchOnPanelSlide(panel: View?) {
        val safePanel = panel ?: return
        panelListeners.forEach { it.onPanelSlide(safePanel, slideOffset) }
    }

    private fun dispatchOnPanelStateChanged(oldState: PanelState, newState: PanelState) {
        val safePanel = slideableView ?: return
        panelListeners.forEach { it.onPanelStateChanged(safePanel, oldState, newState) }
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
    }

    val currentParallaxOffset: Int
        get() {
            val offset = (parallaxOffsetPx * maxOf(slideOffset, 0f)).toInt()
            return if (isUsingBottomGravity) -offset else offset
        }

    override fun checkLayoutParams(layoutParams: ViewGroup.LayoutParams): Boolean {
        return layoutParams is LayoutParams && super.checkLayoutParams(layoutParams)
    }

    override fun computeScroll() {
        if (dragHelper.continueSettling(true)) {
            if (isEnabled) ViewCompat.postInvalidateOnAnimation(this) else dragHelper.abort()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (!isEnabled || !y() || (isUnableToDrag && action != MotionEvent.ACTION_DOWN)) {
            dragHelper.cancel()
            return super.dispatchTouchEvent(event)
        }
        val x = event.x
        val y = event.y
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                isScrollableViewConsumingTouch = false
                downX = x
                downY = y
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = x - downX
                val dy = y - downY
                downX = x
                downY = y
                if (abs(dx) > abs(dy)) return super.dispatchTouchEvent(event)
                if (!isViewUnder(scrollableView, lastMotionX.toInt(), lastMotionY.toInt())) {
                    return super.dispatchTouchEvent(event)
                }
                val draggingTowardExpand = if (isUsingBottomGravity) dy < 0 else dy > 0
                val draggingTowardCollapse = if (isUsingBottomGravity) dy > 0 else dy < 0
                if (draggingTowardExpand) {
                    if (canScrollableViewDragPanel(true)) {
                        isScrollableViewConsumingTouch = true
                        return super.dispatchTouchEvent(event)
                    }
                    if (isScrollableViewConsumingTouch) {
                        val cancelEvent = MotionEvent.obtain(event)
                        cancelEvent.action = MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(cancelEvent)
                        cancelEvent.recycle()
                        event.action = MotionEvent.ACTION_DOWN
                    }
                    isScrollableViewConsumingTouch = false
                    return onTouchEvent(event)
                }
                if (draggingTowardCollapse) {
                    if (slideOffset < maxSlideOffset) {
                        isScrollableViewConsumingTouch = false
                        return onTouchEvent(event)
                    }
                    if (!isScrollableViewConsumingTouch && dragHelper.viewDragState == ViewDragHelper.STATE_DRAGGING) {
                        dragHelper.cancel()
                        event.action = MotionEvent.ACTION_DOWN
                    }
                    isScrollableViewConsumingTouch = true
                    return super.dispatchTouchEvent(event)
                }
            }

            MotionEvent.ACTION_UP -> if (isScrollableViewConsumingTouch) dragHelper.cancel()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        val panel = slideableView ?: return
        val shadow = shadowDrawable ?: return
        val shadowTop: Int
        val shadowBottom: Int
        if (isUsingBottomGravity) {
            shadowTop = panel.top - shadowHeightPx
            shadowBottom = panel.top
        } else {
            shadowTop = panel.bottom
            shadowBottom = panel.bottom + shadowHeightPx
        }
        shadow.setBounds(panel.left, shadowTop, panel.right, shadowBottom)
        shadow.draw(canvas)
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        val saveCount = canvas.save()
        val panel = slideableView
        val didDraw = if (panel == null || panel === child) {
            super.drawChild(canvas, child, drawingTime)
        } else {
            canvas.getClipBounds(temporaryClipRect)
            if (!isOverlayed) {
                if (isUsingBottomGravity) temporaryClipRect.bottom = minOf(temporaryClipRect.bottom, panel.top)
                else temporaryClipRect.top = maxOf(temporaryClipRect.top, panel.bottom)
            }
            if (isClipPanel) canvas.clipRect(temporaryClipRect)
            val result = super.drawChild(canvas, child, drawingTime)
            if (coveredFadeColor != 0 && slideOffset > 0f) {
                val baseAlpha = coveredFadeColor ushr 24
                coveredFadePaint.color =
                    (coveredFadeColor and 0x00FFFFFF) or ((baseAlpha * slideOffset).toInt() shl 24)
                canvas.drawRect(temporaryClipRect, coveredFadePaint)
            }
            result
        }
        canvas.restoreToCount(saveCount)
        return didDraw
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LayoutParams()

    override fun generateLayoutParams(attrs: AttributeSet): ViewGroup.LayoutParams =
        LayoutParams(context, attrs)

    override fun generateLayoutParams(layoutParams: ViewGroup.LayoutParams): ViewGroup.LayoutParams {
        return if (layoutParams is MarginLayoutParams) LayoutParams(layoutParams) else LayoutParams(layoutParams)
    }

    fun getAnchorPoint(): Float = anchorPoint
    fun getCoveredFadeColor(): Int = coveredFadeColor
    fun getMinFlingVelocity(): Int = minimumFlingVelocity
    fun getPanelHeight(): Int = panelHeightPx
    fun getPanelState(): PanelState = panelState
    fun getScrollableView(): View? = scrollableView
    fun getShadowHeight(): Int = shadowHeightPx
    fun getSlideableView(): View? = slideableView

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        initialLayoutPending = true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        initialLayoutPending = true
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        if (dragViewId != View.NO_ID) setDragView(findViewById(dragViewId))
        if (scrollableViewId != View.NO_ID) setScrollableView(findViewById(scrollableViewId))
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (isScrollableViewConsumingTouch || !y()) {
            dragHelper.abort()
            return false
        }
        val action = event.actionMasked
        val x = event.x
        val y = event.y
        val horizontalDistance = abs(x - lastMotionX)
        val verticalDistance = abs(y - lastMotionY)
        val touchSlop = dragHelper.touchSlop
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                isUnableToDrag = false
                lastMotionX = x
                lastMotionY = y
                if (!isViewUnder(dragView, x.toInt(), y.toInt())) {
                    dragHelper.cancel()
                    isUnableToDrag = true
                    return false
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (verticalDistance > touchSlop && horizontalDistance > verticalDistance) {
                    dragHelper.cancel()
                    isUnableToDrag = true
                    return false
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragHelper.viewDragState == ViewDragHelper.STATE_DRAGGING) {
                    dragHelper.processTouchEvent(event)
                    return true
                }
                if (verticalDistance <= touchSlop && horizontalDistance <= touchSlop && slideOffset > 0f) {
                    if (!isViewUnder(slideableView, lastMotionX.toInt(), lastMotionY.toInt())) {
                        playSoundEffect(android.view.SoundEffectConstants.CLICK)
                        fadeClickListener?.onClick(this)
                        return true
                    }
                }
            }
        }
        return dragHelper.shouldInterceptTouchEvent(event)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val startLeft = paddingLeft
        val startTop = paddingTop
        if (initialLayoutPending) {
            slideOffset = when (panelState) {
                PanelState.EXPANDED -> maxSlideOffset
                PanelState.ANCHORED -> anchorPoint
                PanelState.HIDDEN -> computeSlideOffset(
                    computePanelTopPosition(0f) + if (isUsingBottomGravity) panelHeightPx else -panelHeightPx
                )
                else -> 0f
            }
        }
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val layoutParams = child.layoutParams as LayoutParams
            if (child.visibility == View.GONE && !(index != 0 && !initialLayoutPending)) continue
            val childTop = when {
                child === slideableView -> computePanelTopPosition(slideOffset.coerceIn(0f, maxSlideOffset))
                !isUsingBottomGravity && child === mainContentView && !isOverlayed ->
                    computePanelTopPosition(slideOffset.coerceIn(0f, maxSlideOffset)) + (slideableView?.measuredHeight ?: 0)
                else -> startTop + layoutParams.topMargin
            }
            val childLeft = startLeft + layoutParams.leftMargin
            child.layout(
                childLeft,
                childTop,
                childLeft + child.measuredWidth,
                childTop + child.measuredHeight
            )
        }
        if (initialLayoutPending) updateObscuredViewVisibility()
        applyParallaxOffset()
        initialLayoutPending = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        check(widthMode == MeasureSpec.EXACTLY || widthMode == MeasureSpec.AT_MOST) {
            "Width must have an exact value or MATCH_PARENT"
        }
        check(heightMode == MeasureSpec.EXACTLY || heightMode == MeasureSpec.AT_MOST) {
            "Height must have an exact value or MATCH_PARENT"
        }
        check(childCount == 2) { "Sliding up panel layout must have exactly 2 children!" }
        mainContentView = getChildAt(0)
        slideableView = getChildAt(1)
        if (dragView == null) setDragView(slideableView)
        if (slideableView?.visibility != View.VISIBLE) panelState = PanelState.HIDDEN
        val availableHeight = heightSize - paddingTop - paddingBottom
        val availableWidth = widthSize - paddingLeft - paddingRight
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val layoutParams = child.layoutParams as LayoutParams
            if (child.visibility == View.GONE && index == 0) continue
            val childAvailableHeight = when (child) {
                mainContentView -> if (isOverlayed || panelState == PanelState.HIDDEN) availableHeight else availableHeight - panelHeightPx
                slideableView -> availableHeight - layoutParams.topMargin - layoutParams.bottomMargin
                else -> availableHeight
            }
            val childAvailableWidth = availableWidth - layoutParams.leftMargin - layoutParams.rightMargin
            val childWidthSpec = when (layoutParams.width) {
                ViewGroup.LayoutParams.WRAP_CONTENT -> MeasureSpec.makeMeasureSpec(childAvailableWidth, MeasureSpec.AT_MOST)
                ViewGroup.LayoutParams.MATCH_PARENT -> MeasureSpec.makeMeasureSpec(childAvailableWidth, MeasureSpec.EXACTLY)
                else -> MeasureSpec.makeMeasureSpec(layoutParams.width, MeasureSpec.EXACTLY)
            }
            val resolvedHeight = when {
                layoutParams.height == ViewGroup.LayoutParams.WRAP_CONTENT ->
                    MeasureSpec.makeMeasureSpec(childAvailableHeight, MeasureSpec.AT_MOST)
                layoutParams.layoutWeight > 0f && layoutParams.layoutWeight < 1.0f ->
                    MeasureSpec.makeMeasureSpec((childAvailableHeight * layoutParams.layoutWeight).toInt(), MeasureSpec.EXACTLY)
                layoutParams.height == ViewGroup.LayoutParams.MATCH_PARENT ->
                    MeasureSpec.makeMeasureSpec(childAvailableHeight, MeasureSpec.EXACTLY)
                else -> MeasureSpec.makeMeasureSpec(layoutParams.height, MeasureSpec.EXACTLY)
            }
            child.measure(childWidthSpec, resolvedHeight)
            if (child === slideableView) {
                slideRangePx = (child.measuredHeight - panelHeightPx).coerceAtLeast(0)
            }
        }
        setMeasuredDimension(widthSize, heightSize)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            val restoredState = state.getSerializable("sliding_state") as? PanelState ?: DEFAULT_PANEL_STATE
            setPanelStateInternal(restoredState)
            super.onRestoreInstanceState(state.getParcelable("superState"))
            return
        }
        super.onRestoreInstanceState(state)
    }

    override fun onSaveInstanceState(): Parcelable {
        return Bundle().apply {
            putParcelable("superState", super.onSaveInstanceState())
            putSerializable("sliding_state", if (panelState == PanelState.DRAGGING) lastNotDraggingState else panelState)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h != oldh) {
            if (dragHelper.viewDragState == ViewDragHelper.STATE_SETTLING) {
                dragHelper.abort()
                post { setPanelState(PanelState.COLLAPSED) }
            }
            initialLayoutPending = true
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || !y()) return super.onTouchEvent(event)
        return try {
            dragHelper.processTouchEvent(event)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun addPanelSlideListener(listener: PanelSlideListener) {
        panelListeners.add(listener)
    }

    fun removePanelSlideListener(listener: PanelSlideListener) {
        panelListeners.remove(listener)
    }

    fun setAnchorPoint(value: Float) {
        if (value <= 0f || value > 1f) return
        anchorPoint = value
        initialLayoutPending = true
        requestLayout()
    }

    fun setClipPanel(clipPanel: Boolean) {
        isClipPanel = clipPanel
    }

    fun setCoveredFadeColor(color: Int) {
        coveredFadeColor = color
        requestLayout()
    }

    fun setDragView(view: View?) {
        dragView?.setOnClickListener(null)
        dragView = view
        dragView?.apply {
            isClickable = true
            isFocusable = false
            isFocusableInTouchMode = false
            setOnClickListener {
                if (!isEnabled || !y()) return@setOnClickListener
                dragViewClickListener?.onClick(this) ?: run {
                    when (panelState) {
                        PanelState.EXPANDED, PanelState.ANCHORED -> setPanelState(PanelState.COLLAPSED)
                        else -> setPanelState(
                            if (anchorPoint < 1.0f) PanelState.ANCHORED else PanelState.EXPANDED
                        )
                    }
                }
            }
        }
    }

    fun setFadeOnClickListener(listener: View.OnClickListener?) {
        fadeClickListener = listener
    }

    fun setDragViewClickListener(listener: View.OnClickListener?) {
        dragViewClickListener = listener
    }

    fun setGravity(gravity: Int) {
        require(gravity == Gravity.TOP || gravity == Gravity.BOTTOM) {
            "gravity must be set to either top or bottom"
        }
        isUsingBottomGravity = gravity == Gravity.BOTTOM
        shadowDrawable = when {
            shadowHeightPx <= 0 -> null
            isUsingBottomGravity -> ContextCompat.getDrawable(context, R.drawable.above_shadow)
            else -> ContextCompat.getDrawable(context, R.drawable.below_shadow)
        }
        if (!initialLayoutPending) requestLayout()
    }

    fun setMaxSlideOffset(value: Float) {
        if (value <= 1.0f) maxSlideOffset = value
    }

    fun setMinFlingVelocity(value: Int) {
        minimumFlingVelocity = value
        dragHelper.minVelocity = value * resources.displayMetrics.density
    }

    fun setOverlayed(overlayed: Boolean) {
        isOverlayed = overlayed
    }

    fun setPanelHeight(height: Int) {
        if (panelHeightPx == height) return
        panelHeightPx = height
        val isCollapsed = panelState == PanelState.COLLAPSED
        if (!initialLayoutPending && !isCollapsed) requestLayout() else if (isCollapsed && D()) invalidate()
    }

    fun setPanelState(newState: PanelState?) {
        require(newState != null && newState != PanelState.DRAGGING) {
            "Panel state cannot be null or DRAGGING."
        }
        if (dragHelper.viewDragState == ViewDragHelper.STATE_SETTLING) {
            Log.d(TAG, "View is settling. Aborting animation.")
            dragHelper.abort()
        }
        if (!isEnabled) return
        if ((!initialLayoutPending && slideableView == null) || newState == panelState || panelState == PanelState.DRAGGING) {
            return
        }
        if (initialLayoutPending) {
            setPanelStateInternal(newState)
            return
        }
        if (panelState == PanelState.HIDDEN) {
            slideableView?.visibility = View.VISIBLE
            requestLayout()
        }
        when (newState) {
            PanelState.EXPANDED -> smoothSlideTo(maxSlideOffset)
            PanelState.COLLAPSED -> smoothSlideTo(0f)
            PanelState.ANCHORED -> smoothSlideTo(anchorPoint)
            PanelState.HIDDEN ->
                smoothSlideTo(
                    computeSlideOffset(
                        computePanelTopPosition(0f) + if (isUsingBottomGravity) panelHeightPx else -panelHeightPx
                    )
                )
            PanelState.DRAGGING -> Unit
        }
    }

    fun setParallaxOffset(offset: Int) {
        parallaxOffsetPx = offset
        if (!initialLayoutPending) requestLayout()
    }

    fun setScrollableView(view: View?) {
        scrollableView = view
    }

    fun setScrollableViewHelper(helper: ScrollableViewHelper?) {
        scrollableViewHelper = helper ?: ScrollableViewHelper()
    }

    fun setShadowHeight(height: Int) {
        shadowHeightPx = height
        shadowDrawable = when {
            shadowHeightPx <= 0 -> null
            isUsingBottomGravity -> ContextCompat.getDrawable(context, R.drawable.above_shadow)
            else -> ContextCompat.getDrawable(context, R.drawable.below_shadow)
        }
        if (!initialLayoutPending) invalidate()
    }

    fun setTouchEnabled(enabled: Boolean) {
        isTouchEnabled = enabled
    }

    fun w(): Boolean {
        if (!isTouchEnabled || panelState != PanelState.EXPANDED) return false
        setPanelState(PanelState.COLLAPSED)
        return true
    }

    fun y(): Boolean = isTouchEnabled && slideableView != null && panelState != PanelState.HIDDEN

    fun D(): Boolean = smoothSlideTo(0f)

    fun setDragView(id: Int) {
        dragViewId = id
        setDragView(findViewById(id))
    }
}
