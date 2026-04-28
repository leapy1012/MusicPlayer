package gd.app.musicplayer.core.ui.view

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.RelativeLayout
import androidx.core.graphics.withSave

class ScaleRelativeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RelativeLayout(context, attrs) {

    private var currentViewWidth = 0f
    private var currentViewHeight = 0f
    private var targetContentWidth = 0f
    private var targetContentHeight = 0f
    private var interceptTouchEvents = true
    private var lastTargetWidth = 0
    private var lastTargetHeight = 0

    init {
        val screenSize = getTargetScreenSize(
            context = context,
            isLandscape = isLandscape(context.resources.configuration)
        )
        targetContentWidth = screenSize[0].toFloat()
        targetContentHeight = screenSize[1].toFloat()
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.withSave {

            val scale =
                ((currentViewHeight - paddingTop) - paddingBottom) / targetContentHeight

            scale(
                scale,
                scale,
                currentViewWidth / 2.0f,
                currentViewHeight / 2.0f
            )
            translate(0f, (currentViewHeight - targetContentHeight) / 2.0f)

            super.dispatchDraw(this)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val screenSize = getTargetScreenSize(
            context = context,
            isLandscape = isLandscape(newConfig)
        )
        targetContentWidth = screenSize[0].toFloat()
        targetContentHeight = screenSize[1].toFloat()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return interceptTouchEvents || super.onInterceptTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            getDefaultSize(0, widthMeasureSpec),
            getDefaultSize(0, heightMeasureSpec)
        )

        val desiredWidth = targetContentWidth.toInt()
        val desiredHeight = targetContentHeight.toInt()

        val targetSizeChanged =
            lastTargetWidth != desiredWidth || lastTargetHeight != desiredHeight

        lastTargetWidth = desiredWidth
        lastTargetHeight = desiredHeight

        val measuredWidth = measuredWidth
        val measuredHeight = measuredHeight

        val viewSizeChanged =
            currentViewWidth != measuredWidth.toFloat() ||
                    currentViewHeight != measuredHeight.toFloat()

        currentViewWidth = measuredWidth.toFloat()
        currentViewHeight = measuredHeight.toFloat()

        super.onMeasure(
            MeasureSpec.makeMeasureSpec(desiredWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(desiredHeight, MeasureSpec.EXACTLY)
        )

        if (!targetSizeChanged && viewSizeChanged) {
            postInvalidate()
        }
    }

    fun setInterceptTouchEvent(intercept: Boolean) {
        interceptTouchEvents = intercept
    }

    private fun getTargetScreenSize(context: Context, isLandscape: Boolean): IntArray {
        val metrics = context.resources.displayMetrics
        val shortSide = minOf(metrics.widthPixels, metrics.heightPixels)
        val longSide = maxOf(metrics.widthPixels, metrics.heightPixels)
        return if (isLandscape) {
            intArrayOf(longSide, shortSide)
        } else {
            intArrayOf(shortSide, longSide)
        }
    }

    private fun isLandscape(contextConfiguration: Configuration): Boolean {
        return contextConfiguration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }
}
