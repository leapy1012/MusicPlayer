package gd.app.musicplayer.core.designsystem.view

import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.core.content.res.use
import com.coui.appcompat.edittext.COUIEditText
import com.coui.appcompat.R as CouiR

/**
 * Dialog-line [COUIEditText] that paints selected characters white on the accent highlight.
 *
 * COUI themes resolve `textColorHighlight` via `couiColorSecondary`. Our accent overlay
 * maps that token to opaque accent, so stock black text becomes unreadable on select-all.
 */
class CouiDialogEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : COUIEditText(context, attrs, defStyleAttr) {

    @ColorInt
    private var selectedTextColor: Int = Color.WHITE

    @ColorInt
    private var accentHighlight: Int = Color.TRANSPARENT

    init {
        context.obtainStyledAttributes(
            intArrayOf(CouiR.attr.couiColorPrimary, android.R.attr.textColorHighlight),
        ).use { typed ->
            accentHighlight = typed.getColor(0, Color.BLUE)
            // Prefer opaque accent so white selected glyphs stay readable.
            highlightColor = accentHighlight
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        applySelectedTextColor(selStart, selEnd)
    }

    private fun applySelectedTextColor(selStart: Int, selEnd: Int) {
        val editable = text ?: return
        val spannable = editable as? Spannable ?: SpannableStringBuilder(editable).also { text = it }
        spannable.getSpans(0, spannable.length, SelectedTextColorSpan::class.java)
            .forEach(spannable::removeSpan)
        if (selStart == selEnd) return
        val start = selStart.coerceIn(0, spannable.length)
        val end = selEnd.coerceIn(0, spannable.length)
        if (start >= end) return
        spannable.setSpan(
            SelectedTextColorSpan(selectedTextColor),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }

    /** Marker span so we can clear only our selection tint. */
    private class SelectedTextColorSpan(@ColorInt color: Int) : ForegroundColorSpan(color)
}
