package gd.app.musicplayer.ui.editor.waveform

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.hideKeyboard
import gd.app.musicplayer.core.common.util.ToastUtil
import java.util.regex.Pattern

class TimeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs), View.OnFocusChangeListener, TextView.OnEditorActionListener {

    interface OnInputTimeChangedListener {
        fun onInputTimeChanged(timeEditText: TimeEditText, rawText: String, timeMs: Int)
    }

    private val timePattern = Pattern.compile("^(?:([0-9]+):)?([0-9]+)(?:\\.[0-9]{1,2})?$")
    private var minTimeMs = 0
    private var maxTimeMs = Int.MAX_VALUE
    private var lastCommittedText: CharSequence = ""
    private var hadFocus = false
    private var listener: OnInputTimeChangedListener? = null

    init {
        imeOptions = EditorInfo.IME_ACTION_DONE
        onFocusChangeListener = this
        setOnEditorActionListener(this)
    }

    fun commitInput() {
        val rawText = text?.toString()?.trim().orEmpty()
        val parsedTimeMs = when {
            rawText.isEmpty() -> minTimeMs
            rawText.all(Char::isDigit) -> rawText.toIntOrNull()?.times(1000)
            timePattern.matcher(rawText).matches() -> parseTime(rawText)
            else -> null
        }

        if (parsedTimeMs == null) {
            setText(lastCommittedText)
            ToastUtil.show(context, R.string.input_error)
            return
        }

        val clampedTimeMs = parsedTimeMs.coerceIn(minTimeMs, maxTimeMs)
        val normalizedText = formatTime(clampedTimeMs)
        setText(normalizedText)
        setSelection(normalizedText.length)
        if (isFocused) {
            hideKeyboard()
            clearFocus()
        }
        listener?.onInputTimeChanged(this, rawText, clampedTimeMs)
    }

    fun getMaxTime(): Int = maxTimeMs

    fun getMinTime(): Int = minTimeMs

    override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId != EditorInfo.IME_ACTION_DONE &&
            (event == null || event.keyCode != KeyEvent.KEYCODE_ENTER)
        ) {
            return false
        }
        commitInput()
        return false
    }

    override fun onFocusChange(v: View?, hasFocus: Boolean) {
        if (hadFocus && !hasFocus) {
            commitInput()
        }
        hadFocus = hasFocus
    }

    fun setMaxTime(timeMs: Int) {
        maxTimeMs = timeMs.coerceAtLeast(minTimeMs)
    }

    fun setMinTime(timeMs: Int) {
        minTimeMs = timeMs.coerceAtLeast(0)
        if (maxTimeMs < minTimeMs) {
            maxTimeMs = minTimeMs
        }
    }

    fun setOnInputTimeChangedListener(listener: OnInputTimeChangedListener?) {
        this.listener = listener
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        lastCommittedText = text ?: ""
        super.setText(text, type)
    }

    companion object {
        fun formatTime(timeMs: Int): String {
            val totalSeconds = timeMs / 1000
            val hundredths = (timeMs % 1000) / 10
            val builder = StringBuilder()
            if (timeMs >= 60_000) {
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                builder.append(minutes)
                builder.append(':')
                if (seconds < 10) builder.append('0')
                builder.append(seconds)
            } else {
                builder.append(totalSeconds)
            }
            builder.append('.')
            if (hundredths < 10) builder.append('0')
            builder.append(hundredths)
            return builder.toString()
        }

        fun parseTime(value: String): Int {
            if (TextUtils.isEmpty(value)) return 0
            return try {
                val minutePart = value.substringBefore(':', "")
                val secondAndFraction = value.substringAfter(':', value)
                val secondPart = secondAndFraction.substringBefore('.', secondAndFraction)
                val fractionPart = secondAndFraction.substringAfter('.', "")

                val minutes = minutePart.toIntOrNull() ?: 0
                val seconds = secondPart.toIntOrNull() ?: 0
                val fractionMs = when (fractionPart.length) {
                    1 -> (fractionPart.toIntOrNull() ?: 0) * 100
                    2 -> (fractionPart.toIntOrNull() ?: 0) * 10
                    else -> 0
                }
                minutes * 60_000 + seconds * 1000 + fractionMs
            } catch (_: Exception) {
                0
            }
        }
    }
}
