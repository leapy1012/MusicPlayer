package gd.app.lib.model.lrc.view

import android.content.Context
import android.util.AttributeSet
import me.wcy.lrcview.LrcView

class LyricView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LrcView(context, attrs) {

    private var timeOffsetMs: Int = 0

    init {
        // Enable native drag behavior: lyrics follow finger and timeline appears while dragging.
        setDraggable(true, OnPlayClickListener { _, _ -> true })
    }

    fun a(): Boolean = hasLrc()

    fun hasTimedLyrics(): Boolean = hasLrc()

    fun setAutoScroll(enabled: Boolean) {
        // Keep drag timeline enabled; auto scroll is driven by updateTime.
        if (!enabled) {
            setDraggable(true, OnPlayClickListener { _, _ -> true })
        }
    }

    fun setCurrentTime(timeMs: Long) {
        updateTime((timeMs + timeOffsetMs).coerceAtLeast(0L))
    }

    fun setTextAlign(align: Int) {
        invokeIfExists("setTextGravity", arrayOf(Int::class.javaPrimitiveType), align)
        invokeIfExists("setLrcTextGravity", arrayOf(Int::class.javaPrimitiveType), align)
    }

    fun setCurrentTextColor(color: Int) {
        setCurrentColor(color)
    }

    fun setTextSize(size: Float) {
        setNormalTextSize(size)
        setCurrentTextSize(size)
    }

    fun setTextTypeface(style: Int) {
        // No direct equivalent in wangchenyan/LrcView. Keep for compatibility.
    }

    fun setTimeOffset(offsetMs: Int) {
        timeOffsetMs = offsetMs
    }

    fun setLyricText(lyric: String?) {
        loadLrc(lyric.orEmpty())
    }

    private fun invokeIfExists(name: String, paramTypes: Array<Class<*>?>, arg: Any) {
        runCatching {
            javaClass.getMethod(name, *paramTypes).invoke(this, arg)
        }
    }
}
