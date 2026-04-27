package gd.app.musicplayer.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import gd.app.musicplayer.core.ui.extension.dpToPx
import gd.app.musicplayer.core.ui.extension.parcelable
import kotlin.math.max

class ColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface c {
        fun a(color: Int)
    }

    private data class BitmapBuffer(
        var bitmap: Bitmap? = null,
        var canvas: Canvas? = null,
        var key: Float = Float.NaN
    )

    private var sat = 1f
    private var value = 1f
    private var hue = 360f
    private var alphaInt = 255

    private var sliderTrackerColor = -4342339
    private var borderColor = -9539986

    private val hueBarHeight = context.dpToPx(30f)
    private val panelGap = context.dpToPx(10f)
    private val pickerRadius = context.dpToPx(5f).toFloat()
    private val hueTrackerInset = context.dpToPx(2f).toFloat()
    private val hueTrackerWidth = context.dpToPx(4f).toFloat()
    private val minPadding = context.dpToPx(6f)

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pickerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpToPx(2f).toFloat()
    }
    private val hueTrackerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpToPx(2f).toFloat()
        color = sliderTrackerColor
    }
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint()

    private var rootRect = Rect()
    private var colorRect = Rect()
    private var hueRect = Rect()
    private var previewRect = Rect()

    private var valueShader: Shader? = null
    private var satShader: Shader? = null

    private var satValBuffer = BitmapBuffer()
    private var hueBuffer = BitmapBuffer()

    private var downPoint: Point? = null
    private var listener: c? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun getPaddingLeft(): Int = max(super.getPaddingLeft(), minPadding)
    override fun getPaddingTop(): Int = max(super.getPaddingTop(), minPadding)
    override fun getPaddingRight(): Int = max(super.getPaddingRight(), minPadding)
    override fun getPaddingBottom(): Int = max(super.getPaddingBottom(), minPadding)

    fun getBorderColor(): Int = borderColor

    fun getColor(): Int = Color.HSVToColor(alphaInt, floatArrayOf(hue, sat, value))

    fun setBorderColor(color: Int) {
        borderColor = color
        invalidate()
    }

    fun setColor(color: Int) {
        k(color, false)
    }

    fun setOnColorChangedListener(listener: c?) {
        this.listener = listener
    }

    fun setSliderTrackerColor(color: Int) {
        sliderTrackerColor = color
        hueTrackerPaint.color = color
        invalidate()
    }

    fun k(color: Int, notify: Boolean) {
        val hsv = FloatArray(3)
        Color.RGBToHSV(Color.red(color), Color.green(color), Color.blue(color), hsv)
        alphaInt = Color.alpha(color)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]

        if (notify) {
            listener?.a(Color.HSVToColor(alphaInt, floatArrayOf(hue, sat, value)))
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (rootRect.width() <= 0 || rootRect.height() <= 0) return
        drawSatValPanel(canvas)
        drawHueBar(canvas)
        drawPreview(canvas)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)

        var width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var height = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom

        val exact = MeasureSpec.EXACTLY

        if (widthMode != exact && heightMode != exact) {
            val neededWidth = height + panelGap + hueBarHeight
            val neededHeight = width - panelGap - hueBarHeight

            val widthFits = neededWidth <= width
            val heightFits = neededHeight <= height

            when {
                widthFits && !heightFits -> width = neededWidth
                !widthFits && heightFits -> height = neededHeight
            }
        } else if (widthMode == exact && heightMode != exact) {
            val neededHeight = width - panelGap - hueBarHeight
            if (neededHeight <= height) {
                height = neededHeight
            }
        } else if (heightMode == exact && widthMode != exact) {
            val neededWidth = height + panelGap + hueBarHeight
            if (neededWidth <= width) {
                width = neededWidth
            }
        }

        setMeasuredDimension(
            width + paddingLeft + paddingRight,
            height + paddingTop + paddingBottom
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        rootRect = Rect(
            paddingLeft,
            paddingTop,
            w - paddingRight,
            h - paddingBottom
        )

        colorRect = Rect(
            rootRect.left + 1,
            rootRect.top + 1,
            rootRect.right - 1,
            rootRect.bottom - 1 - panelGap - hueBarHeight
        )

        hueRect = Rect(
            rootRect.left + 1 + hueBarHeight + panelGap,
            rootRect.bottom - 1 - hueBarHeight,
            rootRect.right - 1,
            rootRect.bottom - 1
        )

        previewRect = Rect(
            rootRect.left + 1,
            rootRect.bottom - 1 - hueBarHeight,
            rootRect.left + 1 + hueBarHeight,
            rootRect.bottom - 1
        )

        valueShader = null
        satShader = null
        satValBuffer = BitmapBuffer()
        hueBuffer = BitmapBuffer()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downPoint = Point(event.x.toInt(), event.y.toInt())
                handleTouch(event)
            }
            MotionEvent.ACTION_MOVE -> handleTouch(event)
            MotionEvent.ACTION_UP -> {
                downPoint = null
                handleTouch(event)
            }
            else -> false
        }

        if (!handled) return super.onTouchEvent(event)

        listener?.a(getColor())
        invalidate()
        return true
    }

    override fun onSaveInstanceState(): Parcelable {
        return Bundle().apply {
            putParcelable("instanceState", super.onSaveInstanceState())
            putInt("alpha", alphaInt)
            putFloat("hue", hue)
            putFloat("sat", sat)
            putFloat("val", value)
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        var restored = state
        if (restored is Bundle) {
            alphaInt = restored.getInt("alpha")
            hue = restored.getFloat("hue")
            sat = restored.getFloat("sat")
            value = restored.getFloat("val")
            restored = restored.parcelable<Parcelable>("instanceState")
        }
        super.onRestoreInstanceState(restored)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        val start = downPoint ?: return false

        return when {
            hueRect.contains(start.x, start.y) -> {
                hue = clampHue(event.x)
                true
            }
            colorRect.contains(start.x, start.y) -> {
                val pair = clampSatVal(event.x, event.y)
                sat = pair.first
                value = pair.second
                true
            }
            else -> false
        }
    }

    private fun drawSatValPanel(canvas: Canvas) {
        borderPaint.color = borderColor
        canvas.drawRect(
            rootRect.left.toFloat(),
            rootRect.top.toFloat(),
            (colorRect.right + 1).toFloat(),
            (colorRect.bottom + 1).toFloat(),
            borderPaint
        )

        if (valueShader == null) {
            valueShader = LinearGradient(
                colorRect.left.toFloat(),
                colorRect.top.toFloat(),
                colorRect.left.toFloat(),
                colorRect.bottom.toFloat(),
                Color.WHITE,
                Color.BLACK,
                Shader.TileMode.CLAMP
            )
        }

        ensureSatValBuffer()

        if (satValBuffer.key != hue) {
            val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
            satShader = LinearGradient(
                colorRect.left.toFloat(),
                colorRect.top.toFloat(),
                colorRect.right.toFloat(),
                colorRect.top.toFloat(),
                Color.WHITE,
                hueColor,
                Shader.TileMode.CLAMP
            )

            panelPaint.shader = ComposeShader(valueShader!!, satShader!!, PorterDuff.Mode.MULTIPLY)
            satValBuffer.canvas?.drawRect(
                0f,
                0f,
                satValBuffer.bitmap!!.width.toFloat(),
                satValBuffer.bitmap!!.height.toFloat(),
                panelPaint
            )
            satValBuffer.key = hue
        }

        canvas.drawBitmap(satValBuffer.bitmap!!, null, colorRect, null)

        val p = satValToPoint(sat, value)
        pickerPaint.color = Color.BLACK
        canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), pickerRadius - context.dpToPx(1f), pickerPaint)
        pickerPaint.color = -2236963
        canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), pickerRadius, pickerPaint)
    }

    private fun drawHueBar(canvas: Canvas) {
        borderPaint.color = borderColor
        canvas.drawRect(
            (hueRect.left - 1).toFloat(),
            (hueRect.top - 1).toFloat(),
            (hueRect.right + 1).toFloat(),
            (hueRect.bottom + 1).toFloat(),
            borderPaint
        )

        ensureHueBuffer()
        canvas.drawBitmap(hueBuffer.bitmap!!, null, hueRect, null)

        val x = hueToPoint(hue).x.toFloat()
        val tracker = RectF(
            x - hueTrackerWidth / 2f,
            hueRect.top + hueTrackerInset / 2f,
            x + hueTrackerWidth / 2f,
            hueRect.bottom - hueTrackerInset / 2f
        )
        canvas.drawRoundRect(tracker, 2f, 2f, hueTrackerPaint)
    }

    private fun drawPreview(canvas: Canvas) {
        borderPaint.color = borderColor
        canvas.drawRect(
            (previewRect.left - 1).toFloat(),
            (previewRect.top - 1).toFloat(),
            (previewRect.right + 1).toFloat(),
            (previewRect.bottom + 1).toFloat(),
            borderPaint
        )
        previewPaint.color = getColor()
        canvas.drawRect(previewRect, previewPaint)
    }

    private fun ensureSatValBuffer() {
        val width = colorRect.width()
        val height = colorRect.height()

        if (satValBuffer.bitmap == null ||
            satValBuffer.bitmap!!.width != width ||
            satValBuffer.bitmap!!.height != height
        ) {
            satValBuffer.bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            satValBuffer.canvas = Canvas(satValBuffer.bitmap!!)
            satValBuffer.key = Float.NaN
        }
    }

    private fun ensureHueBuffer() {
        val width = hueRect.width()
        val height = hueRect.height()

        if (hueBuffer.bitmap != null &&
            hueBuffer.bitmap!!.width == width &&
            hueBuffer.bitmap!!.height == height
        ) {
            return
        }

        hueBuffer.bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        hueBuffer.canvas = Canvas(hueBuffer.bitmap!!)

        val paint = Paint()
        var currentHue = 360f
        for (x in 0 until width) {
            paint.color = Color.HSVToColor(floatArrayOf(currentHue, 1f, 1f))
            val fx = x.toFloat()
            hueBuffer.canvas?.drawLine(fx, 0f, fx, height.toFloat(), paint)
            currentHue -= 360f / width.toFloat()
        }
    }

    private fun clampHue(x: Float): Float {
        val width = hueRect.width().toFloat()
        val clamped = when {
            x < hueRect.left -> 0f
            x > hueRect.right -> width
            else -> x - hueRect.left
        }
        return 360f - ((clamped * 360f) / width)
    }

    private fun clampSatVal(x: Float, y: Float): Pair<Float, Float> {
        val width = colorRect.width().toFloat()
        val height = colorRect.height().toFloat()

        val sx = when {
            x < colorRect.left -> 0f
            x > colorRect.right -> width
            else -> x - colorRect.left
        }

        val vy = when {
            y < colorRect.top -> 0f
            y > colorRect.bottom -> height
            else -> y - colorRect.top
        }

        return (sx / width) to (1f - (vy / height))
    }

    private fun satValToPoint(sat: Float, value: Float): Point {
        val width = colorRect.width().toFloat()
        val height = colorRect.height().toFloat()
        return Point(
            (sat * width + colorRect.left).toInt(),
            (((1f - value) * height) + colorRect.top).toInt()
        )
    }

    private fun hueToPoint(hue: Float): Point {
        val width = hueRect.width().toFloat()
        return Point(
            (width - ((hue * width) / 360f) + hueRect.left).toInt(),
            hueRect.top
        )
    }
}
