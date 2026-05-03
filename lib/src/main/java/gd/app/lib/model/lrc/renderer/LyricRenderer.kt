package gd.app.lib.model.lrc.renderer

import android.graphics.Canvas
import android.graphics.Typeface
import android.view.MotionEvent
import gd.app.lib.model.lrc.resource.LyricText
import gd.app.lib.model.lrc.view.LyricView

interface LyricRenderer {

    fun setParagraphSpacing(spacing: Int)

    fun setTextAlign(align: Int)

    fun onSizeChanged(
        width: Int,
        height: Int,
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        paddingBottom: Int
    )

    fun setCurrentTextColor(color: Int)

    fun draw(canvas: Canvas)

    fun setMaxLines(maxLines: Int)

    fun detachFromView(view: LyricView)

    fun onTouchEvent(view: LyricView, event: MotionEvent): Boolean

    fun isScrollable(): Boolean

    fun setCurrentTime(time: Long)

    fun setNormalTextColor(color: Int)

    fun setNormalTextSize(size: Float)

    fun setTextSpacing(spacing: Int)

    fun setLineTextColor(color: Int)

    fun lyricData(): LyricText?

    fun computeScroll()

    fun setIndicatorTextSize(size: Float)

    fun setIndicatorColor(color: Int)

    fun setTypeface(typeface: Typeface)

    fun setDragEnabled(enabled: Boolean)

    fun setAutoScroll(enabled: Boolean)

    fun setFadeHeight(height: Float)

    fun attachToView(view: LyricView)

    fun setCurrentTextSize(size: Float)
}