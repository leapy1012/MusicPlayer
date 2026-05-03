package gd.app.lib.model.lrc.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import gd.app.lib.R
import gd.app.lib.dpToPx
import gd.app.lib.isRtlLayoutEnabled
import gd.app.lib.model.lrc.renderer.LyricRenderer
import gd.app.lib.model.lrc.renderer.OnLyricChangeListener
import gd.app.lib.spToPx


class LyricView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var renderer: LyricRenderer? = null

    private var currentTime: Long = 0L
    private var timeOffset: Int = 0

    private var normalTextColor: Int = -1
    private var currentTextColor: Int = -1
    private var lineTextColor: Int = -1

    private var maxLines: Int = -1

    private var normalTextSize: Int = 28
    private var currentTextSize: Int = 32
    private var indicatorTextSize: Int = 28

    private var textSpacing: Int = 0
    private var paragraphSpacing: Int = 0
    private var textAlign: Int = 1

    private var typeface: Typeface = Typeface.DEFAULT

    private var dragEnabled: Boolean = false
    private var autoScroll: Boolean = false
    private var fadeHeight: Int = -1

    private var lyricChangeListener: OnLyricChangeListener? = null

    init {
        attrs?.let {
            val ta = context.obtainStyledAttributes(it, R.styleable.LyricView)

            normalTextSize = ta.getDimensionPixelSize(R.styleable.LyricView_lyricNormalTextSize, context.spToPx(15f))
            currentTextSize = ta.getDimensionPixelSize(R.styleable.LyricView_lyricCurrentTextSize, context.spToPx(17f))
            indicatorTextSize = ta.getDimensionPixelSize(R.styleable.LyricView_lyricLineTextSize, context.spToPx(16f))

            textSpacing = ta.getDimensionPixelSize(R.styleable.LyricView_lyricTextSpacing, 0)
            paragraphSpacing = ta.getDimensionPixelSize(R.styleable.LyricView_lyricParagraphSpacing, 0)

            normalTextColor = ta.getColor(R.styleable.LyricView_lyricNormalTextColor, normalTextColor)
            currentTextColor = ta.getColor(R.styleable.LyricView_lyricCurrentTextColor, currentTextColor)
            lineTextColor = ta.getColor(R.styleable.LyricView_lyricLineTextColor, lineTextColor)

            maxLines = ta.getInt(R.styleable.LyricView_lyricMaxLines, maxLines)
            dragEnabled = ta.getBoolean(R.styleable.LyricView_lyricDragEnable, false)
            fadeHeight = ta.getDimensionPixelSize(R.styleable.LyricView_lyricFadeHeight, -1)

            val typefaceMode = ta.getInt(R.styleable.LyricView_lyricTextType, 0)

            ta.recycle()

            if (typefaceMode != 0) {
                setTextTypeface(typefaceMode)
            }
        }
    }

    fun isScrollable(): Boolean {
        return renderer?.isScrollable() == true
    }

    fun setTextSize(sizeDp: Int) {
        updateTextSize(sizeDp)
    }

    fun updateTextSize(sizeDp: Int, invalidate: Boolean = true) {
        normalTextSize = context.dpToPx(sizeDp.toFloat())
        currentTextSize = normalTextSize + context.dpToPx(4f)

        renderer?.setCurrentTextSize(currentTextSize.toFloat())
        renderer?.setNormalTextSize(normalTextSize.toFloat())

        if (invalidate) {
            postInvalidate()
        }

        if (maxLines > 0) {
            requestLayout()
        }
    }

    override fun computeScroll() {
        renderer?.computeScroll()
    }

    fun getCurrentTime(): Long = currentTime

    fun getMaxLines(): Int = maxLines

    override fun onDraw(canvas: Canvas) {
        renderer?.draw(canvas)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var finalHeightSpec = heightMeasureSpec

        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
            val availableHeight = MeasureSpec.getSize(heightMeasureSpec)

            val lineCount = if (maxLines > 0) maxLines else 6

            var desiredHeight =
                currentTextSize * lineCount +
                        lineCount * textSpacing + context.dpToPx(8f)

            if (availableHeight > 0) {
                desiredHeight = minOf(desiredHeight, availableHeight)
            }

            finalHeightSpec = MeasureSpec.makeMeasureSpec(
                desiredHeight,
                1 shl 30
            )
        }

        super.onMeasure(widthMeasureSpec, finalHeightSpec)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        if (width <= 0 || height <= 0) return

        renderer?.onSizeChanged(
            width,
            height,
            paddingLeft,
            paddingTop,
            paddingRight,
            paddingBottom
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = renderer?.onTouchEvent(this, event) == true
        return if (handled) true else super.onTouchEvent(event)
    }

    fun setAutoScroll(enabled: Boolean) {
        if (autoScroll == enabled) return

        autoScroll = enabled
        renderer?.setAutoScroll(enabled)
        postInvalidate()
    }

    fun setCurrentTextColor(color: Int) {
        currentTextColor = color
        renderer?.setCurrentTextColor(color)
        postInvalidate()
    }

    fun setCurrentTime(time: Long) {
        currentTime = time
        renderer?.setCurrentTime(time + timeOffset)
    }

    fun setDragEnable(enabled: Boolean) {
        dragEnabled = enabled
        renderer?.setDragEnabled(enabled)
        postInvalidate()
    }

    fun setLineTextColor(color: Int) {
        lineTextColor = color
        renderer?.setLineTextColor(color)
        postInvalidate()
    }

    fun setLyricRenderer(newRenderer: LyricRenderer) {
        renderer?.detachFromView(this)

        newRenderer.setTextAlign(textAlign)
        newRenderer.setTypeface(typeface)

        newRenderer.setCurrentTextColor(currentTextColor)
        newRenderer.setNormalTextColor(normalTextColor)

        newRenderer.setNormalTextSize(normalTextSize.toFloat())
        newRenderer.setCurrentTextSize(currentTextSize.toFloat())

        newRenderer.setLineTextColor(lineTextColor)
        newRenderer.setIndicatorTextSize(indicatorTextSize.toFloat())

        newRenderer.setTextSpacing(textSpacing)
        newRenderer.setParagraphSpacing(paragraphSpacing)

        newRenderer.setMaxLines(maxLines)
        newRenderer.setDragEnabled(dragEnabled)
        newRenderer.setAutoScroll(autoScroll)
        newRenderer.setFadeHeight(fadeHeight.toFloat())

        renderer = newRenderer

        onSizeChanged(width, height, width, height)

        newRenderer.attachToView(this)
        newRenderer.setCurrentTime(currentTime + timeOffset)

        postInvalidate()

        newRenderer.lyricData().let {
            lyricChangeListener?.onLyricChanged(it)
        }
    }

    /**
     * Compatibility alias for old Java call sites.
     */
    fun setLyricDrawer(newRenderer: LyricRenderer) {
        setLyricRenderer(newRenderer)
    }

    fun setNormalTextColor(color: Int) {
        normalTextColor = color
        renderer?.setNormalTextColor(color)
        postInvalidate()
    }

    fun setOnLyricTextChangeListener(listener: OnLyricChangeListener?) {
        lyricChangeListener = listener
    }

    fun setParagraphSpacing(spacing: Int) {
        paragraphSpacing = spacing
        renderer?.setParagraphSpacing(spacing)
        postInvalidate()
    }

    fun setTextAlign(align: Int) {
        var resolvedAlign = align

        if (context.isRtlLayoutEnabled()) {
            resolvedAlign = when (align) {
                0 -> 2
                2 -> 0
                else -> align
            }
        }

        textAlign = resolvedAlign
        renderer?.setTextAlign(resolvedAlign)
        postInvalidate()
    }

    fun setTextSpacing(spacing: Int) {
        textSpacing = spacing
        renderer?.setTextSpacing(spacing)
        postInvalidate()
    }

    fun setTextTypeface(typefaceMode: Int) {
        typeface = when (typefaceMode) {
            1 -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            2 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            3 -> Typeface.DEFAULT_BOLD
            else -> Typeface.DEFAULT
        }

        renderer?.setTypeface(typeface)
        postInvalidate()
    }

    fun setTimeOffset(offset: Int) {
        if (timeOffset == offset) return

        timeOffset = offset
        renderer?.setCurrentTime(currentTime + offset)
    }
}