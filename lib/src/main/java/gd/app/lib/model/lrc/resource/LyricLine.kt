package gd.app.lib.model.lrc.resource

import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import gd.app.lib.model.lrc.renderer.MultilineTextLayout
import gd.app.lib.textHeight

class LyricLine private constructor() {

    var index: Int = 0

    var text: String = ""
        private set

    var startTime: Long = 0L
        private set

    private var emptyLine: Boolean = false

    private var lineCount: Int = 0
    private var lineHeight: Int = 0
    private var totalHeight: Int = 0
    var top: Int = 0
    var bottom: Int = 0

    private var highlightedLayout: Boolean = false

    private var textLayout: MultilineTextLayout? = null
    private var layoutCacheKey: String? = null

    fun prepareLayout(
        textPaint: TextPaint,
        highlighted: Boolean,
        maxWidth: Int,
        lineSpacing: Int,
        alignment: Int,
        cacheKey: String
    ) {
        if (textLayout == null || cacheKey != layoutCacheKey) {
            lineHeight = textPaint.textHeight()

            textLayout = MultilineTextLayout(
                textPaint = textPaint,
                maxWidth = maxWidth,
                lineSpacing = lineSpacing,
                lineHeight = lineHeight,
                alignment = alignment
            ).apply {
                setText(text)
            }

            totalHeight = textLayout?.getHeight() ?: 0
            lineCount = textLayout?.getLineCount() ?: 0
            layoutCacheKey = cacheKey
            highlightedLayout = highlighted
        }
    }

    fun copy(): LyricLine {
        return LyricLine().also {
            it.index = index
            it.startTime = startTime
            it.text = text
            it.emptyLine = emptyLine
        }
    }

    fun draw(
        canvas: Canvas,
        x: Float,
        y: Float,
        paintOverride: Paint?
    ) {
        textLayout?.draw(canvas, x, y, paintOverride)
    }

    // f
    fun centerY(): Int {
        return (top + bottom) / 2
    }

    // g
    fun firstLineBaselineCenter(): Int {
        return top + lineHeight / 2
    }

    fun height(): Int {
        return totalHeight
    }

    fun lastLineBaselineCenter(): Int {
        return bottom - lineHeight / 2
    }

    fun isHighlightedLayout(): Boolean {
        return highlightedLayout
    }

    fun isEmptyLine(): Boolean {
        return emptyLine
    }

    fun setStartTime(timeMs: Long) {
        startTime = timeMs
    }

    fun setText(value: String) {
        text = value
        emptyLine = TextUtils.isEmpty(value)
    }

    fun top(): Int = top

    fun bottom(): Int = bottom

    companion object {

        fun buildLayoutCacheKey(
            textPaint: TextPaint,
            maxWidth: Int,
            lineSpacing: Int,
            alignment: Int
        ): String {
            return "${textPaint.textSize}::$maxWidth:$lineSpacing:$alignment"
        }
    }

    override fun toString(): String {
        return "LyricLine{beginTime=$startTime, lyricText='$text'}"
    }

    constructor(startTime: Long) : this(startTime, "")

    constructor(startTime: Long, text: String) : this() {
        this.startTime = startTime
        this.text = text
        this.emptyLine = TextUtils.isEmpty(text)
    }
}