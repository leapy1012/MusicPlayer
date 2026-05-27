package gd.app.musicplayer.core.common.extension

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan

internal fun String.highlight(
    query: String,
    accentColor: Int
): CharSequence {
    if (query.isBlank() || this.isBlank()) return this

    var startIndex = this.indexOf(query, ignoreCase = true)
    if (startIndex == -1) return this

    val spannable = SpannableString(this)
    val queryLength = query.length

    while (startIndex != -1) {
        spannable.setSpan(
            ForegroundColorSpan(accentColor),
            startIndex,
            startIndex + queryLength,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        startIndex = this.indexOf(
            query,
            startIndex = startIndex + queryLength,
            ignoreCase = true
        )
    }

    return spannable
}


fun String?.normalizePath(): String {
    return this.orEmpty().replace('\\', '/')
}