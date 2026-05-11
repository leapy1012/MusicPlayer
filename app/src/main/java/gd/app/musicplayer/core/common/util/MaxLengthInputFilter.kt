package gd.app.musicplayer.core.common.util

import android.content.Context
import android.text.InputFilter
import android.text.Spanned
import gd.app.musicplayer.R

class MaxLengthInputFilter(
    private val context: Context,
    private val maxLength: Int
) : InputFilter {

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        val remainingLength = maxLength - (dest.length - (dend - dstart))

        if (remainingLength <= 0) {
            ToastUtil.show(context, context.getString(R.string.edit_text_limit, maxLength))
            return ""
        }

        val incomingLength = end - start
        if (remainingLength >= incomingLength) {
            return null
        }

        var keepEnd = start + remainingLength

        if (
            Character.isHighSurrogate(source[keepEnd - 1]) &&
            --keepEnd == start
        ) {
            return ""
        }

        return source.subSequence(start, keepEnd)
    }
}
