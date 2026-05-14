package gd.app.musicplayer.core.common.extension

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import gd.app.musicplayer.core.common.util.MaxLengthInputFilter
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
