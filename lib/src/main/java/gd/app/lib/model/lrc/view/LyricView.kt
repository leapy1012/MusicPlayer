package gd.app.lib.model.lrc.view

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.Gravity
import android.widget.Scroller
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.withStyledAttributes
import gd.app.lib.R
import kotlin.math.max

class LyricView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    private data class LyricLine(
        val timestampMs: Long,
        val text: String
    )

    private var rawLyricText: String? = null
    private var lyricLines: List<LyricLine> = emptyList()
    private var hasLyric = false
    private var currentTimeMs: Long = 0L
    private var timeOffsetMs: Int = 0
    private var currentIndex = -1
    private var autoScrollEnabled = false
    private var lineSpacingPx = 0f
    private var currentTextSizePx = textSize
    private var normalTextSizePx = textSize
    private var alignMode = 1
    private var typefaceMode = 0

    @ColorInt
    private var currentTextColorValue = currentTextColor

    @ColorInt
    private var normalTextColorValue = currentTextColor

    init {
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        setScroller(Scroller(context))
        setLineSpacing(0f, 1f)

        context.withStyledAttributes(attrs, R.styleable.LyricView) {
            currentTextColorValue = getColor(
                R.styleable.LyricView_lyricCurrentTextColor,
                currentTextColor
            )
            normalTextColorValue = getColor(
                R.styleable.LyricView_lyricNormalTextColor,
                currentTextColor
            )
            currentTextSizePx = getDimension(
                R.styleable.LyricView_lyricCurrentTextSize,
                textSize
            )
            normalTextSizePx = getDimension(
                R.styleable.LyricView_lyricNormalTextSize,
                textSize
            )
            lineSpacingPx = getDimension(R.styleable.LyricView_lyricParagraphSpacing, 0f)
            typefaceMode = getInt(R.styleable.LyricView_lyricTextType, 0)
        }

        gravity = Gravity.CENTER
        applyTypeface()
        setLyricText(null)
    }

    fun a(): Boolean = hasLyric

    fun hasTimedLyrics(): Boolean = lyricLines.isNotEmpty()

    fun setAutoScroll(enabled: Boolean) {
        autoScrollEnabled = enabled
        if (enabled) {
            centerCurrentLine()
        }
    }

    fun setCurrentTextColor(@ColorInt color: Int) {
        currentTextColorValue = color
        render()
    }

    fun setCurrentTime(timeMs: Long) {
        currentTimeMs = timeMs
        render()
    }

    fun setTextAlign(align: Int) {
        alignMode = align.coerceIn(0, 2)
        gravity = when (alignMode) {
            0 -> Gravity.START or Gravity.CENTER_VERTICAL
            2 -> Gravity.END or Gravity.CENTER_VERTICAL
            else -> Gravity.CENTER
        }
        textAlignment = when (alignMode) {
            0 -> TEXT_ALIGNMENT_VIEW_START
            2 -> TEXT_ALIGNMENT_VIEW_END
            else -> TEXT_ALIGNMENT_CENTER
        }
        render()
    }

    fun setTextTypeface(style: Int) {
        typefaceMode = style.coerceIn(0, 3)
        applyTypeface()
        render()
    }

    fun setTimeOffset(offsetMs: Int) {
        timeOffsetMs = offsetMs
        render()
    }

    override fun setTextSize(size: Float) {
        normalTextSizePx = size * resources.displayMetrics.scaledDensity
        currentTextSizePx = normalTextSizePx
        super.setTextSize(size)
        render()
    }

    fun setLyricText(lyric: String?) {
        rawLyricText = lyric?.takeIf { it.isNotBlank() }
        lyricLines = parseTimedLyrics(rawLyricText)
        hasLyric = !rawLyricText.isNullOrBlank()
        currentIndex = -1
        render()
    }

    private fun applyTypeface() {
        typeface = when (typefaceMode) {
            1 -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            2 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            3 -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            else -> Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
    }

    private fun parseTimedLyrics(source: String?): List<LyricLine> {
        if (source.isNullOrBlank()) return emptyList()

        val parsed = mutableListOf<LyricLine>()
        source.lineSequence().forEach { line ->
            val matches = TIMESTAMP_REGEX.findAll(line).toList()
            if (matches.isEmpty()) return@forEach

            val content = TIMESTAMP_REGEX.replace(line, "").trim()
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: return@forEach
                val seconds = match.groupValues[2].toLongOrNull() ?: return@forEach
                val fractionRaw = match.groupValues[3]
                val millis = when (fractionRaw.length) {
                    1 -> fractionRaw.toLongOrNull()?.times(100)
                    2 -> fractionRaw.toLongOrNull()?.times(10)
                    else -> fractionRaw.takeIf { it.isNotEmpty() }?.toLongOrNull()
                } ?: 0L
                parsed += LyricLine(
                    timestampMs = (minutes * 60_000L) + (seconds * 1_000L) + millis,
                    text = content
                )
            }
        }

        return parsed.sortedBy(LyricLine::timestampMs)
    }

    private fun render() {
        if (!hasLyric) {
            currentIndex = -1
            setTextColor(normalTextColorValue)
            super.setText("Loading lyrics...")
            scrollTo(0, 0)
            return
        }

        if (lyricLines.isEmpty()) {
            currentIndex = -1
            setTextColor(normalTextColorValue)
            super.setText(rawLyricText)
            scrollTo(0, 0)
            return
        }

        val effectiveTime = (currentTimeMs + timeOffsetMs).coerceAtLeast(0L)
        val newIndex = lyricLines.indexOfLast { it.timestampMs <= effectiveTime }
        if (newIndex == currentIndex && text.isNotEmpty()) {
            if (autoScrollEnabled) {
                centerCurrentLine()
            }
            return
        }
        currentIndex = newIndex

        val builder = SpannableStringBuilder()
        lyricLines.forEachIndexed { index, line ->
            val start = builder.length
            builder.append(line.text.ifBlank { " " })
            val end = builder.length
            val isCurrent = index == currentIndex
            builder.setSpan(
                ForegroundColorSpan(if (isCurrent) currentTextColorValue else normalTextColorValue),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                AbsoluteSizeSpan(
                    (if (isCurrent) currentTextSizePx else normalTextSizePx)
                        .toInt()
                        .coerceAtLeast(1)
                ),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (isCurrent) {
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (index < lyricLines.lastIndex) {
                builder.append("\n\n")
            }
        }

        super.setText(builder, BufferType.SPANNABLE)
        setTextColor(normalTextColorValue)
        setLineSpacing(lineSpacingPx, 1f)
        if (autoScrollEnabled) {
            post { centerCurrentLine() }
        }
    }

    private fun centerCurrentLine() {
        val textLayout = layout ?: return
        if (currentIndex !in lyricLines.indices) return
        val visualLine = max(0, currentIndex * 2)
        val targetTop = textLayout.getLineTop(visualLine)
        val targetBottom = textLayout.getLineBottom(visualLine)
        val targetCenter = (targetTop + targetBottom) / 2
        val scrollTarget = (targetCenter - (height / 2)).coerceAtLeast(0)
        scrollTo(0, scrollTarget)
    }

    companion object {
        private val TIMESTAMP_REGEX =
            Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    }
}
