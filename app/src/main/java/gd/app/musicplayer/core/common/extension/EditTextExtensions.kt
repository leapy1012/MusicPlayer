package gd.app.musicplayer.core.common.extension

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.util.MaxLengthInputFilter
import gd.app.musicplayer.core.common.util.ToastUtil
import java.io.File

fun EditText.extractValidatedText(keepPathSeparators: Boolean): String? {
    val trimmedText = text?.toString()?.trim().orEmpty()
    if (trimmedText.isEmpty()) return null

    val finalText = if (keepPathSeparators) {
        trimmedText
    } else {
        trimmedText.replace(File.separator, "")
    }

    return finalText.takeIf { it.isNotEmpty() }
}

fun EditText.applyLengthFilter(
    maxLength: Int
) {
    filters = arrayOf(MaxLengthInputFilter(context, maxLength))
}

fun EditText.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    if (rootView != null) {
        imm.hideSoftInputFromWindow(rootView.windowToken, 0)
        return
    }
}

fun EditText.showKeyboardDelayed(
    delayMs: Long = 400L
) {
    postDelayed(
        {
            requestFocus()

            val imm = context.getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager

            imm.showSoftInput(
                this,
                InputMethodManager.SHOW_IMPLICIT
            )
        },
        delayMs
    )
}


fun EditText.setTextIfDifferent(value: String) {
    if (text?.toString() == value) return

    setText(value)
    setSelection(text?.length ?: 0)
}

fun EditText.readLongOrNull(
    required: Boolean,
    defaultValue: Long,
    minValue: Long,
    maxValue: Long
): Long? {
    val rawValue = text?.toString()?.trim().orEmpty()

    if (rawValue.isEmpty()) {
        if (required) {
            ToastUtil.show(context, R.string.equalizer_edit_input_error)
            return null
        }

        return defaultValue
    }

    return rawValue.toLongOrNull()?.coerceIn(minValue, maxValue)
        ?: defaultValue
}