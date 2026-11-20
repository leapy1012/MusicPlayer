package gd.app.musicplayer.core.designsystem.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Port of `com.ijoysoft.music.view.index.RecyclerIndexBar`.
 *
 * Two modes, matching the original drawers: [setLetters] renders the A–Z column (`index.c`) and
 * [setFastScrollEnabled] renders a draggable percentage thumb (`index.b`). The bar fades itself out
 * [AUTO_HIDE_DELAY_MS] after the last scroll or touch.
 */
class RecyclerIndexBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { DISABLED, LETTERS, FAST_SCROLL }

    var onLetterSelected: ((LetterPosition) -> Unit)? = null
    var onFastScrollPercent: ((Float) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val letterGap = (LETTER_GAP_DP * density).roundToInt()
    private val letterHeight = (LETTER_SIZE_DP * density).roundToInt()
    private val verticalPadding = (TRACK_PADDING_DP * density).roundToInt()
    private val thumbHeight = (THUMB_HEIGHT_DP * density).roundToInt()
    private val thumbWidth = (THUMB_WIDTH_DP * density)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = LETTER_SIZE_DP * density
        textAlign = Paint.Align.CENTER
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val bounds = Rect()
    private val trackRect = RectF()

    private var allLetters: List<LetterPosition> = emptyList()
    private var sampledLetters: List<LetterPosition> = emptyList()
    private var sampledIndices: IntArray = IntArray(0)
    private var currentLetter: LetterPosition? = null

    private var mode: Mode = Mode.DISABLED
    private var layerVisible = false
    private var dragging = false
    private var fastScrollPercent = 0f

    private var normalColor: Int = 0
    private var selectedColor: Int = 0

    private val hideRunnable = Runnable { setLayerVisible(false) }

    /** `index.c.f7739c`: the floating letter plate shown while the column is being dragged. */
    private var letterPopup: IndexLetterPopup? = null

    init {
        val typed = context.obtainStyledAttributes(
            intArrayOf(
                com.coui.appcompat.R.attr.couiColorPrimaryNeutral,
                com.coui.appcompat.R.attr.couiColorSecondNeutral,
                com.coui.appcompat.R.attr.couiColorDivider
            )
        )
        val selectedColor = typed.getColor(0, 0xFF000000.toInt())
        val normalColor = typed.getColor(1, 0x8A000000.toInt())
        val trackColor = typed.getColor(2, 0x1F000000)
        typed.recycle()

        setColors(
            trackColor = ColorUtils.setAlphaComponent(trackColor, TRACK_ALPHA),
            normalLetterColor = normalColor,
            selectedLetterColor = selectedColor
        )
    }

    fun setColors(trackColor: Int, normalLetterColor: Int, selectedLetterColor: Int) {
        trackPaint.color = trackColor
        thumbPaint.color = selectedLetterColor
        normalColor = normalLetterColor
        selectedColor = selectedLetterColor
        invalidate()
    }

    /** `RecyclerIndexBar.b`: detach both drawers and keep the bar hidden. */
    fun disable() {
        mode = Mode.DISABLED
        allLetters = emptyList()
        sampledLetters = emptyList()
        currentLetter = null
        removeCallbacks(hideRunnable)
        layerVisible = false
        letterPopup?.hide()
        visibility = GONE
    }

    /** `RecyclerIndexBar.h`: letters mode. */
    fun setLetters(letters: List<LetterPosition>) {
        if (letters.isEmpty()) {
            disable()
            return
        }

        mode = Mode.LETTERS
        allLetters = letters
        currentLetter = null
        visibility = VISIBLE
        resample()
        invalidate()
    }

    /** `RecyclerIndexBar.g`: fast-scroll thumb mode. */
    fun setFastScrollEnabled() {
        mode = Mode.FAST_SCROLL
        allLetters = emptyList()
        sampledLetters = emptyList()
        currentLetter = null
        visibility = VISIBLE
        resample()
        invalidate()
    }

    fun setCurrentLetter(letter: LetterPosition?) {
        if (currentLetter == letter) return
        currentLetter = letter
        showLayerTemporarily()
    }

    fun setFastScrollPercent(percent: Float) {
        fastScrollPercent = percent.coerceIn(0f, 1f)
        if (layerVisible) invalidate()
    }

    /** `RecyclerIndexBar.d`: a non-idle scroll state reveals the bar. */
    fun onScrollStateChanged(newState: Int) {
        if (mode == Mode.DISABLED || dragging || newState == 0) return
        showLayerTemporarily()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) return

        bounds.set(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        resample()
    }

    override fun onDraw(canvas: Canvas) {
        if (mode == Mode.DISABLED || !layerVisible || bounds.isEmpty) return

        when (mode) {
            Mode.LETTERS -> drawLetters(canvas)
            Mode.FAST_SCROLL -> drawThumb(canvas)
            Mode.DISABLED -> Unit
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mode == Mode.DISABLED || !layerVisible) return false

        removeCallbacks(hideRunnable)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = trackRect.contains(trackRect.centerX(), event.y)
                if (!dragging) {
                    postDelayed(hideRunnable, AUTO_HIDE_DELAY_MS)
                    return false
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                dispatchTouchPosition(event.y)
                if (mode == Mode.LETTERS) {
                    letterPopup()?.apply {
                        setLetter(currentLetter?.letter.orEmpty())
                        show()
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                dispatchTouchPosition(event.y)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    dragging = false
                }
                letterPopup?.hideDelayed()
                postDelayed(hideRunnable, AUTO_HIDE_DELAY_MS)
            }
        }

        return dragging
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(hideRunnable)
        letterPopup?.hide()
    }

    private fun dispatchTouchPosition(y: Float) {
        when (mode) {
            Mode.LETTERS -> {
                val letter = letterAtY(y) ?: return
                if (currentLetter != letter) {
                    currentLetter = letter
                    invalidate()
                    if (dragging) letterPopup?.setLetter(letter.letter)
                    onLetterSelected?.invoke(letter)
                }
            }

            Mode.FAST_SCROLL -> {
                val travel = (trackRect.height() - thumbHeight).coerceAtLeast(1f)
                val percent = ((y - trackRect.top - thumbHeight / 2f) / travel).coerceIn(0f, 1f)
                fastScrollPercent = percent
                invalidate()
                onFastScrollPercent?.invoke(percent)
            }

            Mode.DISABLED -> Unit
        }
    }

    /** Inflated on the first drag only; `disable()` must never pay for it. */
    private fun letterPopup(): IndexLetterPopup? =
        letterPopup ?: IndexLetterPopup.createOrNull(context)?.also { letterPopup = it }

    private fun showLayerTemporarily() {
        setLayerVisible(true)
        removeCallbacks(hideRunnable)
        postDelayed(hideRunnable, AUTO_HIDE_DELAY_MS)
    }

    private fun setLayerVisible(visible: Boolean) {
        if (layerVisible == visible) return
        layerVisible = visible
        // `index.c.e`: the plate never outlives the bar itself.
        if (!visible) letterPopup?.hideDelayed()
        invalidate()
    }

    /** `index.c.n` / `index.c.o`: drop letters evenly when the column does not fit. */
    private fun resample() {
        if (bounds.isEmpty || allLetters.isEmpty()) {
            sampledLetters = allLetters
            sampledIndices = IntArray(allLetters.size) { it }
            updateTrackRect(allLetters.size)
            return
        }

        val available = bounds.height() - verticalPadding * 2
        val slot = letterHeight + letterGap
        val maxVisible = max(1, available / slot)

        if (allLetters.size <= maxVisible) {
            sampledLetters = allLetters
            sampledIndices = IntArray(allLetters.size) { it }
        } else {
            val indices = IntArray(maxVisible) { slotIndex ->
                ((slotIndex * (allLetters.size - 1)).toFloat() / (maxVisible - 1)).roundToInt()
            }
            sampledIndices = indices
            sampledLetters = indices.map { allLetters[it] }
        }

        updateTrackRect(sampledLetters.size)
    }

    private fun updateTrackRect(visibleCount: Int) {
        if (bounds.isEmpty) {
            trackRect.setEmpty()
            return
        }

        if (mode == Mode.FAST_SCROLL || visibleCount == 0) {
            trackRect.set(bounds)
            return
        }

        val contentHeight =
            letterHeight * visibleCount + letterGap * (visibleCount - 1) + verticalPadding * 2

        if (contentHeight >= bounds.height()) {
            trackRect.set(bounds)
            return
        }

        val inset = (bounds.height() - contentHeight) / 2f
        trackRect.set(
            bounds.left.toFloat(),
            bounds.top + inset,
            bounds.right.toFloat(),
            bounds.bottom - inset
        )
    }

    private fun drawLetters(canvas: Canvas) {
        if (sampledLetters.isEmpty()) return

        val radius = trackRect.width() / 2f
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        val centerX = trackRect.centerX()
        var baseline = trackRect.top + verticalPadding + letterHeight / 2f -
                (textPaint.descent() + textPaint.ascent()) / 2f

        sampledLetters.forEach { letter ->
            textPaint.color = if (letter == currentLetter) selectedColor else normalColor
            canvas.drawText(letter.letter, centerX, baseline, textPaint)
            baseline += letterHeight + letterGap
        }
    }

    private fun drawThumb(canvas: Canvas) {
        val centerX = trackRect.centerX()
        val travel = (trackRect.height() - thumbHeight).coerceAtLeast(0f)
        val top = trackRect.top + travel * fastScrollPercent
        val radius = thumbWidth / 2f

        canvas.drawRoundRect(
            centerX - radius,
            top,
            centerX + radius,
            top + thumbHeight,
            radius,
            radius,
            thumbPaint
        )
    }

    private fun letterAtY(y: Float): LetterPosition? {
        if (sampledLetters.isEmpty()) return null

        val slot = (letterHeight + letterGap).toFloat()
        val raw = (y - trackRect.top - verticalPadding) / slot
        val clamped = raw.coerceIn(0f, (sampledLetters.size - 1).toFloat())
        val lowerIndex = clamped.toInt()
        val fraction = clamped - lowerIndex

        val interpolated = if (lowerIndex >= sampledIndices.size - 1) {
            sampledIndices[sampledIndices.lastIndex]
        } else {
            val lower = sampledIndices[lowerIndex]
            lower + (fraction * (sampledIndices[lowerIndex + 1] - lower)).roundToInt()
        }

        return allLetters.getOrNull(min(max(interpolated, 0), allLetters.lastIndex))
    }

    private companion object {
        private const val AUTO_HIDE_DELAY_MS = 3_000L
        private const val LETTER_SIZE_DP = 12f
        private const val LETTER_GAP_DP = 4f
        private const val TRACK_PADDING_DP = 3f
        private const val THUMB_HEIGHT_DP = 40f
        private const val THUMB_WIDTH_DP = 4f
        private const val TRACK_ALPHA = 28
    }
}
