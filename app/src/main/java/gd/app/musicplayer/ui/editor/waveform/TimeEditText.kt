package gd.app.musicplayer.ui.editor.waveform

import android.content.Context
import android.text.InputFilter
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import kotlin.math.max

class TimeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs),
    View.OnFocusChangeListener,
    TextView.OnEditorActionListener {

    interface OnInputTimeChangedListener {
        fun onInputTimeChanged(
            timeEditText: TimeEditText,
            rawText: String,
            timeMs: Int
        )

        fun onInvalidTimeInput(
            timeEditText: TimeEditText,
            rawText: String
        )
    }

    private var listener: OnInputTimeChangedListener? = null
    private var minTimeMs: Int = 0
    private var maxTimeMs: Int = Int.MAX_VALUE
    private var lastCommittedText: CharSequence = ""

    init {
        filters = arrayOf(InputFilter.LengthFilter(MAX_LENGTH))
        imeOptions = EditorInfo.IME_ACTION_DONE
        onFocusChangeListener = this
        setOnEditorActionListener(this)
    }

    fun commitInput(): Boolean {
        val rawText = text?.toString()?.trim().orEmpty()
        val parsedTime = parseTime(rawText)

        if (parsedTime == null) {
            restoreLastCommittedText()
            listener?.onInvalidTimeInput(this, rawText)
            return false
        }

        val safeTime = parsedTime.coerceIn(minTimeMs, maxTimeMs)
        setTime(safeTime)
        clearFocus()
        listener?.onInputTimeChanged(this, rawText, safeTime)
        return true
    }

    fun setOnInputTimeChangedListener(listener: OnInputTimeChangedListener?) {
        this.listener = listener
    }

    fun setMinTime(timeMs: Int) {
        minTimeMs = max(0, timeMs)
    }

    fun setMaxTime(timeMs: Int) {
        maxTimeMs = max(0, timeMs)
    }

    fun setTime(timeMs: Int) {
        val formatted = formatTime(timeMs.coerceIn(minTimeMs, maxTimeMs))
        if (text?.toString() != formatted) {
            setText(formatted)
            setSelection(formatted.length)
        }
        lastCommittedText = formatted
    }

    override fun onEditorAction(
        view: TextView?,
        actionId: Int,
        event: KeyEvent?
    ): Boolean {
        if (actionId == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
            commitInput()
            return true
        }
        return false
    }

    override fun onFocusChange(view: View?, hasFocus: Boolean) {
        if (!hasFocus && text?.toString() != lastCommittedText.toString()) {
            commitInput()
        }
    }

    private fun restoreLastCommittedText() {
        val fallback = lastCommittedText.toString()
        setText(fallback)
        setSelection(fallback.length)
    }

    companion object {
        private const val MAX_LENGTH = 8
        private val TIME_PATTERN = Regex("^(?:([0-9]+):)?([0-9]+)(?:\\.([0-9]{1,2}))?$")

        fun formatTime(timeMs: Int): String {
            val safeTime = timeMs.coerceAtLeast(0)
            var seconds = safeTime / 1000
            val centiseconds = (safeTime % 1000) / 10
            return buildString {
                if (safeTime >= 60_000) {
                    val minutes = seconds / 60
                    seconds %= 60
                    append(minutes)
                    append(':')
                    if (seconds < 10) append('0')
                }
                append(seconds)
                append('.')
                if (centiseconds < 10) append('0')
                append(centiseconds)
            }
        }

        fun parseTime(rawText: String): Int? {
            if (rawText.isBlank()) return 0
            rawText.toIntOrNull()?.let { return it * 1000 }
            val match = TIME_PATTERN.matchEntire(rawText.trim()) ?: return null
            val minutes = match.groupValues.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
                ?.toIntOrNull()
                ?: 0
            val seconds = match.groupValues.getOrNull(2)
                ?.takeIf { it.isNotBlank() }
                ?.toIntOrNull()
                ?: return null
            val fraction = match.groupValues.getOrNull(3).orEmpty()
            val millis = when (fraction.length) {
                1 -> fraction.toInt() * 100
                2 -> fraction.toInt() * 10
                else -> 0
            }
            return (minutes * 60_000) + (seconds * 1000) + millis
        }
    }
}
