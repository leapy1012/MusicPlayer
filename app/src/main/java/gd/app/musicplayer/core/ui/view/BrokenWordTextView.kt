package gd.app.musicplayer.core.ui.view

import android.content.Context
import android.text.TextPaint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView


class BrokenWordTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    private var originalText: CharSequence? = null
    private var shouldProcessText: Boolean = true

    private fun updateDisplayedTextToFitWidth() {
        val availableWidth = width - (paddingLeft + paddingRight)
        val sourceText = originalText ?: return
        if (availableWidth <= 0) return

        val textPaint: TextPaint = paint
        val breakIndex = textPaint.breakText(
            sourceText,
            0,
            sourceText.length,
            true,
            availableWidth.toFloat(),
            null
        )

        if (breakIndex >= sourceText.length || breakIndex <= 0) {
            setTextDirectly(sourceText)
            return
        }

        var firstChunk = sourceText.subSequence(0, breakIndex)

        if (!isWordSeparator(sourceText[breakIndex])) {
            val safeEnd = findLastSeparatorIndex(firstChunk)
            if (safeEnd > 0) {
                firstChunk = firstChunk.subSequence(0, safeEnd)
            }
        }

        setTextDirectly(firstChunk)
    }

    private fun findLastSeparatorIndex(text: CharSequence): Int {
        for (i in text.length - 1 downTo 0) {
            if (isWordSeparator(text[i])) {
                return i
            }
        }
        return -1
    }

    private fun isWordSeparator(ch: Char): Boolean = ch == ' '

    private fun setTextDirectly(text: CharSequence?) {
        shouldProcessText = false
        super.setText(text, BufferType.NORMAL)
        shouldProcessText = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateDisplayedTextToFitWidth()
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        if (!shouldProcessText) {
            super.setText(text, type)
        } else {
            originalText = text
            updateDisplayedTextToFitWidth()
        }
    }
}