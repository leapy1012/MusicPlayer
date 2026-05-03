package gd.app.lib

import android.content.Context
import android.graphics.Paint
import android.text.InputFilter
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import androidx.core.os.ConfigurationCompat
import androidx.core.text.TextUtilsCompat
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.ceil

internal fun Context.dpToPx(value: Float): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics
    ).toInt()

internal fun Context.spToPx(value: Float): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics
    ).toInt()

internal fun Paint.textHeight(): Int {
    val metrics = fontMetrics
    return ceil(metrics.descent - metrics.ascent).toInt()
}
internal fun Paint.wrapParagraphIntoLines(
    text: String,
    maxWidth: Float,
    outputLines: MutableList<String>
) {
    var remainingText = text

    while (remainingText.isNotEmpty()) {
        val count = breakText(
            remainingText,
            true,
            maxWidth,
            null
        )

        if (count <= 0) break

        outputLines.add(remainingText.substring(0, count))
        remainingText = remainingText.substring(count)
    }
}

internal fun Paint.getCenteredTextBaselineY(
    centerY: Float
): Float {
    val metrics = fontMetrics
    return centerY - metrics.descent + (metrics.bottom - metrics.top) / 2f
}

internal fun Paint.wrapText(
    text: String,
    maxWidth: Float,
    outputLines: MutableList<String>,
    preserveNewLines: Boolean
) {
    val availableWidth = maxWidth - textSize / 2f

    if (!preserveNewLines) {
        wrapParagraphIntoLines(
            text = text,
            maxWidth = availableWidth,
            outputLines = outputLines
        )
        return
    }

    text.split('\n').forEach { paragraph ->
        wrapParagraphIntoLines(
            text = paragraph,
            maxWidth = availableWidth,
            outputLines = outputLines
        )
    }
}

inline fun <reified T> View.findParentOfType(): T? {
    var currentParent = parent

    while (currentParent != null) {
        if (currentParent is T) {
            return currentParent
        }

        val nextParent = currentParent.parent

        if (currentParent == nextParent) {
            return null
        }

        currentParent = nextParent
    }

    return null
}

internal fun EditText.getTrimmedTextOrNull(
    allowFileSeparator: Boolean = true
): String? {
    val cleanedText = text.toString().trim().let { value ->
        if (allowFileSeparator) {
            value
        } else {
            value.replace(File.separator, "")
        }
    }

    return cleanedText.takeIf { it.isNotEmpty() }
}

internal fun EditText.setMaxLength(maxLength: Int) {
    filters = arrayOf(InputFilter.LengthFilter(maxLength))
}

internal fun Long.toPlaybackTimeText(): String {
    if (this <= 1L) return "00:00"
    if (this < 1000L) return "00:01"

    val totalSeconds = this / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L

    return "%02d:%02d".format(minutes, seconds)
}

@Throws(IOException::class)
internal fun InputStream.containsOnlyAscii(): Boolean {
    while (true) {
        val byte = read()

        when {
            byte == -1 -> return true
            byte > 127 -> return false
        }
    }
}

internal fun Context.isRtlLayoutSupported(): Boolean {
    return (applicationInfo.flags and 0x400000) != 0
}

internal fun Context.isRtl(): Boolean {
    val configuration = resources.configuration
    val locale = ConfigurationCompat.getLocales(configuration)[0]
    val layoutDirection = locale?.let(TextUtilsCompat::getLayoutDirectionFromLocale)
        ?: configuration.layoutDirection

    return layoutDirection == View.LAYOUT_DIRECTION_RTL
}

fun Context.isRtlLayoutEnabled(): Boolean {
    return isRtl() && isRtlLayoutSupported()
}